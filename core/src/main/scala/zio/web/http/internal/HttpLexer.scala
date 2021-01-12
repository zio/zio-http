package zio.web.http.internal

import zio.web.http.model.{ Method, Uri, Version }

import scala.collection.mutable.Queue

import java.io.Reader
import java.nio.CharBuffer
import java.util

import zio.Chunk
import zio.web.http.internal.HttpLexer.HeaderParseError._
import zio.web.http.internal.zio_json_reuse._

import scala.annotation.switch
import scala.util.control.NoStackTrace

/*
HTTP 1.1 grammar (source: https://tools.ietf.org/html/rfc7230)

HTTP-message   = start-line
.                *( header-field CRLF )
                 CRLF
                 [ message-body ]

header-field   = field-name ":" OWS field-value OWS

OWS            = *( SP / HTAB )
                 ; optional whitespace

field-name     = token
token          = 1*tchar
tchar          = "!" / "#" / "$" / "%" / "&" / "'" / "*"
                 / "+" / "-" / "." / "^" / "_" / "`" / "|" / "~"
                 / DIGIT / ALPHA
                 ; any VCHAR, except delimiters
                 ; Delimiters are DQUOTE and "(),/:;<=>?@[\]{}"
ALPHA          = %x41-5A / %x61-7A
                 ; A-Z / a-z
DIGIT          = %x30-39
                 ; 0-9

field-value    = *( field-content / obs-fold )
field-content  = field-vchar [ 1*( SP / HTAB ) field-vchar ]
field-vchar    = VCHAR / obs-text
VCHAR          = %x21-7E
                ; visible (printing) characters
obs-text       = %x80-FF
obs-fold       = CRLF 1*( SP / HTAB )
                ; obsolete line folding
                ; see Section 3.2.4
 */

// Parsing mechanism adapted from ZIO JSON, credits to Sam Halliday (@fommil).
object HttpLexer {

  /**
   *
   * Parses start-line and returns a tuple of {@link zio.web.http.model.Method}, {@link zio.web.http.model.Uri} and {@link zio.web.http.model.Version}
   *
   * @param reader - HTTP request
   * @param methodLimit - defines maximum HTTP method length
   * @param uriLimit - defines maximum HTTP URI length (2048 search engine friendly)
   * @param versionLimit - defines maximum HTTP version length (according to the spec and available HTTP versions it can be 8)
   * @return a tuple of Method, Uri and Version
   */
  def parseStartLine(
    reader: java.io.Reader,
    methodLimit: Int = 7,
    uriLimit: Int = 2048,
    versionLimit: Int = 8
  ): (Method, Uri, Version) = {
    //TODO: not sure that it actually supports HTTP 2, I just started digging into HTTP 2 and it looks like a different story
    // it uses something called frames and has a different layout
    //TODO: https://undertow.io/blog/2015/04/27/An-in-depth-overview-of-HTTP2.html
    //TODO: https://developers.google.com/web/fundamentals/performance/http2/

    require(reader != null)
    require(reader.ready())

    // end of a line is CRLF
    val CR = 0x0D
    val LF = 0x0A
    val SP = 0x20 // elements separated by space

    val elements       = Queue[String]()
    var currentElement = new StringBuilder
    var char           = reader.read()

    //there is no need in reading the whole http request, so reading till the end of the first line
    while (char != LF) {

      // define and check the corresponding limit based on a currently processing element
      checkCurrentElementSize(currentElement.size, elements.size match {
        case 0 => methodLimit
        case 1 => uriLimit
        case 2 => versionLimit
      })

      char match {
        case c if c == SP || (c == CR && elements.size == 2) =>
          elements += currentElement.toString(); currentElement = new StringBuilder
        case _ => currentElement.append(char.toChar)
      }

      char = reader.read()

      if (elements.size == 3 && char != LF)
        throw new IllegalStateException("Malformed HTTP start-line")
    }

    def checkCurrentElementSize(elementSize: Int, limit: Int): Unit =
      if (elementSize > limit) throw new IllegalStateException("Malformed HTTP start-line")

    (Method.fromString(elements.dequeue()), Uri.fromString(elements.dequeue()), Version.fromString(elements.dequeue()))
  }

  sealed abstract class HeaderParseError(message: String) extends Exception(message) with NoStackTrace

  object HeaderParseError {
    case object UnexpectedEnd extends HeaderParseError("Unexpected EOF")

    private def msg(char: Int, in: String) = s"Invalid character in $in: 0x${char.toHexString}"

    // TODO: Ensure that this translates to an HTTP 431 "Request Header Fields Too Large" response
    case object HeaderTooLarge extends HeaderParseError("Request header fields too large")

    final case class InvalidCharacterInName(char: Int) extends HeaderParseError(msg(char, "header"))

    final case class InvalidCharacterInValue(char: Int) extends HeaderParseError(msg(char, "value"))

    final case class ExpectedLF(char: Int)
        extends HeaderParseError(
          "CR must be followed by LF, but got " +
            (if (char == -1) "EOF." else s"0x${char.toHexString}.")
        )
  }

  val TokenChars = Seq('!', '#', '$', '%', '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|', '~') ++
    ('0' to '9') ++ ('a' to 'z') ++ ('A' to 'Z')

  private val isTokenChar: Array[Boolean] = {
    val r = Array.fill(256)(false)
    TokenChars.foreach(char => r(char.toInt) = true)
    r
  }

  /**
   * Parses the headers with the specified names, returning an array that contains header values
   * for the headers with those names. Returns null strings if it couldn't find the headers before
   * the end of the headers.
   */
  def parseHeaders(
    headers: Array[String],
    reader: Reader,
    headerSizeLimit: Int = 8192
  ): Array[Chunk[String]] = {
    // TODO:
    //  * Async???
    //  * Support for HTTP/2, HTTP/3
    //  * Parameterize on protocol version
    //  * Implement limits (e.g. number of whitespace characters to read before
    //    rejecting the request) to prevent huge messages from overloading the
    //    server

    val output: Array[Chunk[String]] = Array.fill[Chunk[String]](headers.length)(Chunk.empty)
    val matrix                       = new CaseInsensitiveStringMatrix(headers)
    var replay                       = false
    var c: Int                       = -1
    var size                         = 0

    def read(): Unit =
      if (replay)
        replay = false
      else {
        c = reader.read
        size += 1
        if (headerSizeLimit < size) throw HeaderTooLarge
      }

    def readLF(): Unit = {
      c = reader.read()
      if (c != '\n')
        throw ExpectedLF(c)
    }

    def startsWithCRLF(): Boolean = {
      c = reader.read()
      if (c == '\r') {
        c = reader.read()
        if (c == '\n') return true
        throw ExpectedLF(c)
      } else if (c == -1) throw UnexpectedEnd
      else replay = true
      false
    }

    def parseHeaderName(): Int = {
      var i: Int       = 0
      var bitset: Long = matrix.initial
      while ({ read(); c != ':' }) {
        if (c == -1) throw UnexpectedEnd
        else if (isTokenChar(c)) {
          bitset = matrix.update(bitset, i, c)
          i += 1
        } else
          throw InvalidCharacterInName(c)
      }
      bitset = matrix.exact(bitset, i)
      matrix.first(bitset)
    }

    def consumeWhitespace() =
      do read() while (
        (c: @switch) match {
          case ' '  => true
          case '\t' => true
          case _ =>
            replay = true
            false
        }
      )

    def parseHeaderValue(): String = {
      val sb = new FastStringBuilder(64)
      while ({ read(); c != '\r' }) if (c == -1)
        throw UnexpectedEnd
      else if (c == '\t' || ' ' <= c)
        sb.append(c.toChar)
      else
        throw InvalidCharacterInValue(c)
      readLF()
      sb.trimmedBuffer.toString
    }

    while (!startsWithCRLF()) {
      val header = parseHeaderName()
      if (header == -1) {
        while (c != '\r') {
          if (c == -1) throw UnexpectedEnd
          read()
        }
        readLF()
      } else {
        consumeWhitespace()
        output(header) = output(header) :+ parseHeaderValue()
      }
    }

    output
  }
}

// Adapted from ZIO JSON, credits to Sam Halliday (@fommil).
object zio_json_reuse {

  final private[internal] class CaseInsensitiveStringMatrix(val xs: Array[String]) {
    require(xs.forall(_.nonEmpty))
    require(xs.nonEmpty)
    require(xs.length < 64)

    val width               = xs.length
    val height: Int         = xs.map(_.length).max
    val lengths: Array[Int] = xs.map(_.length)
    val initial: Long       = (0 until width).foldLeft(0L)((bs, r) => bs | (1L << r))

    private val matrix: Array[Int] = {
      val m           = Array.fill[Int](width * height)(-1)
      var string: Int = 0
      while (string < width) {
        val s         = xs(string).toLowerCase
        val len       = s.length
        var char: Int = 0
        while (char < len) {
          m(width * char + string) = s.codePointAt(char)
          char += 1
        }
        string += 1
      }
      m
    }

    // must be called with increasing `char` (starting with bitset obtained from a
    // call to 'initial', char = 0)
    def update(bitset: Long, char: Int, c: Int): Long = {
      val lowerChar = c.toChar.toLower
      if (char >= height) 0L    // too long
      else if (bitset == 0L) 0L // everybody lost
      else {
        var latest: Long = bitset
        val base: Int    = width * char

        if (bitset == initial) { // special case when it is dense since it is simple
          var string: Int = 0
          while (string < width) {
            if (matrix(base + string) != lowerChar)
              latest = latest ^ (1L << string)
            string += 1
          }
        } else {
          var remaining: Long = bitset
          while (remaining != 0L) {
            val string: Int = java.lang.Long.numberOfTrailingZeros(remaining)
            val bit: Long   = 1L << string
            if (matrix(base + string) != lowerChar)
              latest = latest ^ bit
            remaining = remaining ^ bit
          }
        }

        latest
      }
    }

    // excludes entries that are not the given exact length
    def exact(bitset: Long, length: Int): Long =
      if (length > height) 0L // too long
      else {
        var latest: Long    = bitset
        var remaining: Long = bitset
        while (remaining != 0L) {
          val string: Int = java.lang.Long.numberOfTrailingZeros(remaining)
          val bit: Long   = 1L << string
          if (lengths(string) != length)
            latest = latest ^ bit
          remaining = remaining ^ bit
        }
        latest
      }

    def first(bitset: Long): Int =
      if (bitset == 0L) -1
      else java.lang.Long.numberOfTrailingZeros(bitset) // never returns 64
  }

  // like StringBuilder but doesn't have any encoding or range checks
  final private[internal] class FastStringBuilder(initial: Int) {
    private[this] var chars: Array[Char] = Array.ofDim(initial)
    private[this] var i: Int             = 0

    def append(c: Char): Unit = {
      if (i == chars.length)
        chars = util.Arrays.copyOf(chars, chars.length * 2)
      chars(i) = c
      i += 1
    }

    def trimmedBuffer: CharSequence = {
      var len = i
      while ((0 < len) && (chars(len - 1) <= ' ')) len -= 1
      CharBuffer.wrap(chars, 0, len)
    }
  }
}
