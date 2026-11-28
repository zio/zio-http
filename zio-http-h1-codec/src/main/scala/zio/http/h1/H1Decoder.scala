/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.h1

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.ListBuffer

import zio.blocks.chunk.Chunk

private[h1] final case class H1CompletedResponse(message: H1Response, next: Int)
private[h1] final case class H1Line(content: String, next: Int)
private[h1] final case class H1ParsedHeaders(headers: H1Headers, next: Int)
private[h1] final case class H1ChunkedBody(body: Chunk[Byte], trailers: H1Headers, next: Int)
private[h1] final case class H1ParsedTrailers(headers: H1Headers, next: Int)

/**
 * HTTP/1.1 decoder with a resumable, linear-time request parser.
 *
 * The request scan persists its state across [[feed]] calls: `scanFrom` is the
 * first buffer index not yet proven LF-free, and a per-message state machine
 * (request line, header section, fixed body, chunk framing, trailer section)
 * remembers what has already been parsed. Each buffered byte is therefore
 * examined O(1) times no matter how the socket delivers it, and every line
 * bound is enforced during the scan - an unterminated line past its bound is
 * rejected before any CRLF arrives.
 *
 * Response decoding ([[feedResponse]]) keeps the restart-per-feed shape. The
 * two paths share only the byte buffer, the poison flag, and compaction; both
 * maintain the invariant that `start` is 0 on every feed entry, so [[poisoned]]
 * and [[bufferedBytes]] stay coherent for either path. A single instance must
 * not be interleaved between the two paths.
 */
final class H1Decoder(val limits: H1Limits = H1Limits.Default, onHeadersComplete: H1Headers => Unit = _ => ()) {
  private val CR: Byte = 13
  private val LF: Byte = 10

  private var buffer: Array[Byte]   = new Array[Byte](8192)
  private var start: Int            = 0
  private var end: Int              = 0
  private var poisonedFlag: Boolean = false
  private var compactionCount: Long = 0L

  // ---------------------------------------------------- resumable request scan

  /** Which part of the message starting at `start` is being accumulated. */
  private var reqState: H1Decoder.RequestState = H1Decoder.ReqLine

  /** Start of the line currently being scanned. */
  private var lineStart: Int = 0

  /** First buffer index not yet proven LF-free; the LF scan resumes here. */
  private var scanFrom: Int = 0

  /** Monotonic count of buffer bytes examined by the request scanner. */
  private var scannedBytesCount: Long = 0L

  /** Per-message accumulators, cleared by [[resetMessage]] after every emit. */
  private var method: String                 = null
  private var target: String                 = null
  private val headerFields                   = ListBuffer.empty[H1Header]
  private var headerCount                    = 0
  private var headerSectionBytes             = 0
  private var currentHeaders: H1Headers      = H1Headers.Empty
  private var framing: H1BodyFraming         = H1BodyFraming.Empty
  private var bodyLength: Long               = 0L
  private var bodyStart: Int                 = 0
  private var chunkRemaining: Long           = 0L
  private var dataStart: Int                 = 0
  private val bodyBuffer                     = ArrayBuffer.empty[Byte]
  private val trailerFields                  = ListBuffer.empty[H1Header]
  private var trailerCount                   = 0
  private var trailerSectionBytes            = 0
  private var announcedTrailers: Set[String] = Set.empty[String]

  /**
   * Guards the Expect hook so a request blocked on its body fires it exactly
   * once.
   */
  private var headersCompleteFired: Boolean = false

  def poisoned: Boolean   = poisonedFlag
  def bufferedBytes: Int  = end - start
  def bufferCapacity: Int = buffer.length
  def compactions: Long   = compactionCount

  /** Monotonic count of buffer bytes examined by the request scanner. */
  private[h1] def scannedBytes: Long = scannedBytesCount

  def feed(bytes: Chunk[Byte]): Either[H1Error, List[H1Request]] = {
    if (poisonedFlag) return Left(H1Error.DecoderPoisoned())
    append(bytes)
    val messages = ListBuffer.empty[H1Request]
    var failure  = Option.empty[H1Error]
    var running  = true
    while (running && failure.isEmpty) {
      val step: Either[H1Error, Boolean] = reqState match {
        case H1Decoder.ReqLine      => stepReqLine()
        case H1Decoder.Headers      => stepHeaders()
        case H1Decoder.AfterHeaders => stepAfterHeaders(messages)
        case H1Decoder.BodyFixed    => stepBodyFixed(messages)
        case H1Decoder.ChunkSize    => stepChunkSize()
        case H1Decoder.ChunkData    => stepChunkData()
        case H1Decoder.ChunkTrailer => stepChunkTrailer(messages)
      }
      step match {
        case Left(error)  => failure = Some(error)
        case Right(true)  => ()
        case Right(false) => running = false
      }
    }
    failure match {
      case Some(error) => poison(error)
      case None        =>
        compactRequestBuffer()
        if (end - start > limits.maxUndecodedBytes) poison(H1Error.RetentionOverflow())
        else Right(messages.toList)
    }
  }

  def feedResponse(bytes: Chunk[Byte], requestMethod: String): Either[H1Error, List[H1Response]] = {
    if (poisonedFlag) return Left(H1Error.DecoderPoisoned())
    append(bytes)
    val messages = List.newBuilder[H1Response]
    var pos      = start
    var failure  = Option.empty[H1Error]
    var blocked  = false
    while (failure.isEmpty && !blocked) {
      parseResponse(pos, requestMethod) match {
        case Left(error)            => failure = Some(error)
        case Right(None)            => blocked = true
        case Right(Some(completed)) =>
          messages += completed.message
          pos = completed.next
      }
    }
    if (failure.isDefined) poison(failure.get)
    else {
      compact(pos)
      if (end - start > limits.maxUndecodedBytes) poison(H1Error.RetentionOverflow())
      else Right(messages.result())
    }
  }

  // ------------------------------------------------------------------- states

  private def stepReqLine(): Either[H1Error, Boolean] =
    scanLine(limits.maxRequestLineLength, H1Error.LineTooLong("request line exceeds the bound")) match {
      case H1Decoder.LineBlocked              => Right(false)
      case H1Decoder.LineFailed(error)        => Left(error)
      case H1Decoder.LineFound(content, next) =>
        parseRequestLine(content) match {
          case Left(error)                         => Left(error)
          case Right((parsedMethod, parsedTarget)) =>
            method = parsedMethod
            target = parsedTarget
            lineStart = next
            scanFrom = next
            reqState = H1Decoder.Headers
            Right(true)
        }
    }

  private def stepHeaders(): Either[H1Error, Boolean] = {
    val bound = limits.maxHeaderSectionBytes - headerSectionBytes - 2
    scanLine(bound, H1Error.HeaderSectionTooLarge("header section exceeds the bound")) match {
      case H1Decoder.LineBlocked              => Right(false)
      case H1Decoder.LineFailed(error)        => Left(error)
      case H1Decoder.LineFound(content, next) =>
        if (content.isEmpty) {
          lineStart = next
          scanFrom = next
          reqState = H1Decoder.AfterHeaders
          Right(true)
        } else
          parseHeaderField(content) match {
            case Left(error)  => Left(error)
            case Right(field) =>
              headerSectionBytes += content.length + 2
              headerCount += 1
              if (field.name.length > limits.maxHeaderNameLength)
                Left(H1Error.HeaderNameTooLong("header name exceeds the bound: '" + field.name + "'"))
              else if (field.value.length > limits.maxHeaderValueLength)
                Left(H1Error.HeaderValueTooLong("header value exceeds the bound"))
              else if (headerSectionBytes > limits.maxHeaderSectionBytes)
                Left(H1Error.HeaderSectionTooLarge("header section exceeds the bound"))
              else if (headerCount > limits.maxHeaderCount)
                Left(H1Error.TooManyHeaders("too many header fields"))
              else {
                headerFields += field
                lineStart = next
                scanFrom = next
                Right(true)
              }
          }
    }
  }

  private def stepAfterHeaders(messages: ListBuffer[H1Request]): Either[H1Error, Boolean] = {
    currentHeaders = H1Headers(headerFields.toList)
    validateRequestHost(target, currentHeaders) match {
      case Left(error) => Left(error)
      case Right(())   =>
        H1Framing.resolve(
          currentHeaders,
          limits,
          isResponse = false,
          statusCode = 0,
          requestMethod = method,
        ) match {
          case Left(error)     => Left(error)
          case Right(resolved) =>
            validateExpect(currentHeaders) match {
              case Left(error)           => Left(error)
              case Right(expectContinue) =>
                framing = resolved
                if (expectContinue && hasPendingBody(resolved) && !headersCompleteFired) {
                  onHeadersComplete(currentHeaders)
                  headersCompleteFired = true
                }
                resolved match {
                  case H1BodyFraming.Empty         =>
                    emitRequest(messages, lineStart, emptyChunk, H1Headers.Empty)
                    Right(true)
                  case H1BodyFraming.Fixed(length) =>
                    bodyLength = length
                    bodyStart = lineStart
                    reqState = H1Decoder.BodyFixed
                    Right(true)
                  case H1BodyFraming.Chunked       =>
                    bodyBuffer.clear()
                    announcedTrailers = announcedTrailerNames(currentHeaders)
                    reqState = H1Decoder.ChunkSize
                    Right(true)
                }
            }
        }
    }
  }

  private def stepBodyFixed(messages: ListBuffer[H1Request]): Either[H1Error, Boolean] = {
    val available = (end - bodyStart).toLong
    if (available < bodyLength) Right(false)
    else {
      val length = bodyLength.toInt
      val body   = copyChunk(bodyStart, length)
      scannedBytesCount += length.toLong
      emitRequest(messages, bodyStart + length, body, H1Headers.Empty)
      Right(true)
    }
  }

  private def stepChunkSize(): Either[H1Error, Boolean] =
    scanLine(limits.maxChunkLineLength, H1Error.LineTooLong("chunk-size line exceeds the bound")) match {
      case H1Decoder.LineBlocked              => Right(false)
      case H1Decoder.LineFailed(error)        => Left(error)
      case H1Decoder.LineFound(content, next) =>
        if (content.isEmpty) Left(H1Error.InvalidMessage("empty chunk-size line"))
        else if (content.length > limits.maxChunkLineLength)
          Left(H1Error.LineTooLong("chunk-size line exceeds the bound"))
        else
          H1Validation.parseChunkSize(content) match {
            case Left(error) => Left(error)
            case Right(size) =>
              if (size > limits.maxUndecodedBytes.toLong)
                Left(H1Error.BodyTooLarge("chunk size exceeds the retention bound"))
              else if (bodyBuffer.length.toLong + size > limits.maxUndecodedBytes.toLong)
                Left(H1Error.BodyTooLarge("chunked body exceeds the retention bound"))
              else if (size == 0L) {
                lineStart = next
                scanFrom = next
                reqState = H1Decoder.ChunkTrailer
                Right(true)
              } else {
                chunkRemaining = size
                dataStart = next
                lineStart = next
                scanFrom = next
                reqState = H1Decoder.ChunkData
                Right(true)
              }
          }
    }

  private def stepChunkData(): Either[H1Error, Boolean] = {
    val needed = chunkRemaining + 2L
    if ((end - dataStart).toLong < needed) Right(false)
    else {
      val dataEnd = dataStart + chunkRemaining.toInt
      if (buffer(dataEnd) != CR || buffer(dataEnd + 1) != LF)
        Left(H1Error.InvalidMessage("chunk data terminator must be CRLF"))
      else {
        var i = dataStart
        while (i < dataEnd) {
          bodyBuffer += buffer(i)
          i += 1
        }
        scannedBytesCount += chunkRemaining + 2L
        lineStart = dataEnd + 2
        scanFrom = dataEnd + 2
        reqState = H1Decoder.ChunkSize
        Right(true)
      }
    }
  }

  private def stepChunkTrailer(messages: ListBuffer[H1Request]): Either[H1Error, Boolean] = {
    val bound = limits.maxTrailerSectionBytes - trailerSectionBytes - 2
    scanLine(bound, H1Error.TrailerSectionTooLarge("trailer section exceeds the bound")) match {
      case H1Decoder.LineBlocked              => Right(false)
      case H1Decoder.LineFailed(error)        => Left(error)
      case H1Decoder.LineFound(content, next) =>
        if (content.isEmpty) {
          val parsed      = trailerFields.toList
          val unannounced = parsed.find(header => !announcedTrailers.contains(header.name.toLowerCase))
          unannounced match {
            case Some(header) => Left(H1Error.AmbiguousFraming("unannounced trailer field: '" + header.name + "'"))
            case None         =>
              emitRequest(messages, next, Chunk.fromArray(bodyBuffer.toArray), H1Headers(parsed))
              Right(true)
          }
        } else
          parseHeaderField(content) match {
            case Left(error)  => Left(error)
            case Right(field) =>
              trailerSectionBytes += content.length + 2
              trailerCount += 1
              if (field.name.equalsIgnoreCase("Content-Length") || field.name.equalsIgnoreCase("Transfer-Encoding"))
                Left(H1Error.AmbiguousFraming("framing header inside trailers: '" + field.name + "'"))
              else if (field.name.length > limits.maxHeaderNameLength)
                Left(H1Error.HeaderNameTooLong("trailer name exceeds the bound: '" + field.name + "'"))
              else if (field.value.length > limits.maxHeaderValueLength)
                Left(H1Error.HeaderValueTooLong("trailer value exceeds the bound"))
              else if (trailerSectionBytes > limits.maxTrailerSectionBytes)
                Left(H1Error.TrailerSectionTooLarge("trailer section exceeds the bound"))
              else if (trailerCount > limits.maxTrailerCount)
                Left(H1Error.TooManyTrailers("too many trailer fields"))
              else {
                trailerFields += field
                lineStart = next
                scanFrom = next
                Right(true)
              }
          }
    }
  }

  // ------------------------------------------------------------------ helpers

  /**
   * Resumes the LF scan at `scanFrom` for the line starting at `lineStart`.
   *
   * The scan is single-pass: a CR is resolved by peeking the next byte (a CR at
   * the buffer edge blocks and resumes on the CR itself), so every buffered
   * byte is examined O(1) times across feeds. `bound` is the maximum content
   * length this line may have; a regular byte that proves the line would exceed
   * it fails immediately with `boundError`, before any LF arrives. An empty
   * line never trips the bound, as it has no content bytes.
   */
  private def scanLine(bound: Int, boundError: H1Error): H1Decoder.LineResult = {
    var i = scanFrom
    while (i < end) {
      scannedBytesCount += 1L
      val b = buffer(i)
      if (b == LF)
        // A legal line always completes on the CR peek below, so an LF
        // reached by the scan is bare or not CR-terminated.
        return H1Decoder.LineFailed(H1Error.InvalidMessage("line must end with CRLF"))
      if (b == CR) {
        if (i + 1 == end) {
          scanFrom = i
          return H1Decoder.LineBlocked
        }
        if (buffer(i + 1) != LF)
          return H1Decoder.LineFailed(H1Error.InvalidMessage("lone CR inside a line"))
        scannedBytesCount += 1L
        val contentLength = i - lineStart
        scannedBytesCount += contentLength.toLong
        return H1Decoder.LineFound(latin1(lineStart, i), i + 2)
      }
      if (i + 1 - lineStart > bound)
        return H1Decoder.LineFailed(boundError)
      i += 1
    }
    scanFrom = end
    H1Decoder.LineBlocked
  }

  private def emitRequest(messages: ListBuffer[H1Request], next: Int, body: Chunk[Byte], trailers: H1Headers): Unit = {
    messages += H1Request(method, target, currentHeaders, body, framing, trailers)
    start = next
    resetMessage()
  }

  /**
   * Drops every per-message accumulator and points the scan at the next request
   * line. The consumed prefix is compacted away once per feed, after the parse
   * loop, together with the cursor shift.
   */
  private def resetMessage(): Unit = {
    reqState = H1Decoder.ReqLine
    lineStart = start
    scanFrom = start
    method = null
    target = null
    headerFields.clear()
    headerCount = 0
    headerSectionBytes = 0
    currentHeaders = H1Headers.Empty
    framing = H1BodyFraming.Empty
    bodyLength = 0L
    bodyStart = 0
    chunkRemaining = 0L
    dataStart = 0
    bodyBuffer.clear()
    trailerFields.clear()
    trailerCount = 0
    trailerSectionBytes = 0
    announcedTrailers = Set.empty[String]
    headersCompleteFired = false
  }

  private def poison(error: H1Error): Either[H1Error, Nothing] = {
    poisonedFlag = true
    start = 0
    end = 0
    resetMessage()
    Left(error)
  }

  private def compact(pos: Int): Unit =
    if (pos > start) {
      val remaining = end - pos
      if (remaining > 0) System.arraycopy(buffer, pos, buffer, 0, remaining)
      start = 0
      end = remaining
      compactionCount += 1L
    }

  /**
   * Compacts the consumed prefix once per feed and shifts the persisted scan
   * cursors with it, so `start` is 0 again on the next feed's entry.
   */
  private def compactRequestBuffer(): Unit =
    if (start > 0) {
      val remaining = end - start
      if (remaining > 0) System.arraycopy(buffer, start, buffer, 0, remaining)
      end = remaining
      lineStart -= start
      scanFrom -= start
      bodyStart -= start
      dataStart -= start
      start = 0
      compactionCount += 1L
    }

  private def append(bytes: Chunk[Byte]): Unit = {
    val needed = (end - start) + bytes.length
    if (needed > buffer.length) {
      var capacity = buffer.length * 2
      while (capacity < needed) capacity *= 2
      val grown    = new Array[Byte](capacity)
      System.arraycopy(buffer, start, grown, 0, end - start)
      buffer = grown
      end = end - start
      start = 0
    }
    var i      = 0
    while (i < bytes.length) {
      buffer(end) = bytes(i)
      end += 1
      i += 1
    }
  }

  /**
   * Every HTTP/1.1 request carries exactly one non-empty `Host`, regardless of
   * the request-target form; an absolute-form target must additionally agree
   * with it on host and port.
   */
  private def validateRequestHost(target: String, headers: H1Headers): Either[H1Error, Unit] = {
    val hostValues = headers.headerValues("Host")
    if (hostValues.isEmpty) Left(H1Error.InvalidMessage("HTTP/1.1 request requires a Host header"))
    else if (hostValues.length > 1) Left(H1Error.InvalidMessage("multiple Host headers"))
    else {
      val host = H1Validation.trimOWS(hostValues.head)
      if (host.isEmpty) Left(H1Error.InvalidMessage("empty Host header"))
      else if (isAbsoluteForm(target)) checkAbsoluteFormHost(target, host)
      else Right(())
    }
  }

  private def isAbsoluteForm(target: String): Boolean =
    target.regionMatches(true, 0, "https://", 0, 8) || target.regionMatches(true, 0, "http://", 0, 7)

  private def checkAbsoluteFormHost(target: String, hostHeader: String): Either[H1Error, Unit] = {
    val https              = target.regionMatches(true, 0, "https://", 0, 8)
    val schemeEnd          = if (https) 8 else 7
    var authorityEnd       = target.length
    var i                  = schemeEnd
    var found              = false
    while (i < target.length && !found) {
      val c = target.charAt(i)
      if (c == '/' || c == '?' || c == '#') {
        authorityEnd = i
        found = true
      } else i += 1
    }
    val authority          = target.substring(schemeEnd, authorityEnd)
    val (uriHost, uriPort) = splitAuthority(authority)
    val (hdrHost, hdrPort) = splitAuthority(hostHeader)
    if (!uriHost.equalsIgnoreCase(hdrHost))
      Left(H1Error.InvalidMessage("Host header conflicts with the absolute-form target authority"))
    else {
      val defaultPort = if (https) 443L else 80L
      (parsePort(uriPort), parsePort(hdrPort)) match {
        case (Right(uriPortValue), Right(hdrPortValue)) =>
          if (uriPortValue.getOrElse(defaultPort) == hdrPortValue.getOrElse(defaultPort)) Right(())
          else Left(H1Error.InvalidMessage("Host header port conflicts with the absolute-form target"))
        case _                                          =>
          Left(H1Error.InvalidMessage("malformed port in the Host header or the absolute-form target"))
      }
    }
  }

  /**
   * Splits an authority into its host part and raw port; the port is None when
   * absent.
   */
  private def splitAuthority(authority: String): (String, Option[String]) =
    if (authority.startsWith("[")) {
      val close = authority.indexOf(']')
      if (close < 0) (authority, None)
      else if (authority.length > close + 1 && authority.charAt(close + 1) == ':')
        (authority.substring(0, close + 1), Some(authority.substring(close + 2)))
      else (authority.substring(0, close + 1), None)
    } else {
      val colon = authority.lastIndexOf(':')
      if (colon > 0) (authority.substring(0, colon), Some(authority.substring(colon + 1)))
      else (authority, None)
    }

  /**
   * Parses a raw port; Left when the port is present but is not a plain digit
   * run.
   */
  private def parsePort(raw: Option[String]): Either[Unit, Option[Long]] =
    raw match {
      case None        => Right(None)
      case Some(value) =>
        if (value.isEmpty) Left(())
        else {
          var i     = 0
          var acc   = 0L
          var valid = true
          while (i < value.length && valid) {
            val c = value.charAt(i)
            if (c < '0' || c > '9') valid = false
            else if (acc > (Long.MaxValue - (c - '0').toLong) / 10L) valid = false
            else acc = acc * 10L + (c - '0').toLong
            i += 1
          }
          if (valid) Right(Some(acc)) else Left(())
        }
    }

  /**
   * Validates the Expect header; Right(true) when the request carries a legal
   * 100-continue expectation.
   */
  private def validateExpect(headers: H1Headers): Either[H1Error, Boolean] = {
    val values = headers.headerValues("Expect")
    if (values.isEmpty) Right(false)
    else if (values.length != 1) Left(H1Error.UnsupportedExpectation("multiple Expect headers"))
    else if (H1Validation.equalsIgnoreCase(H1Validation.trimOWS(values.head), "100-continue")) Right(true)
    else Left(H1Error.UnsupportedExpectation("unsupported Expect value: '" + values.head + "'"))
  }

  private def hasPendingBody(framing: H1BodyFraming): Boolean =
    framing match {
      case H1BodyFraming.Fixed(length) => length > 0L
      case H1BodyFraming.Chunked       => true
      case H1BodyFraming.Empty         => false
    }

  /**
   * Lowercased field names announced by the Trailer header; empty when the
   * message announces none.
   */
  private def announcedTrailerNames(headers: H1Headers): Set[String] = {
    val values = headers.headerValues("Trailer")
    val names  = Set.newBuilder[String]
    var i      = 0
    while (i < values.length) {
      val parts = values(i).split(",")
      var j     = 0
      while (j < parts.length) {
        val token = H1Validation.trimOWS(parts(j)).toLowerCase
        if (token.nonEmpty) names += token
        j += 1
      }
      i += 1
    }
    names.result()
  }

  private def readLine(pos: Int): Either[H1Error, Option[H1Line]] = {
    val lf = indexOfLF(pos)
    if (lf < 0) Right(None)
    else if (lf == pos || buffer(lf - 1) != CR) Left(H1Error.InvalidMessage("line must end with CRLF"))
    else {
      var i = pos
      while (i < lf - 1) {
        if (buffer(i) == CR) return Left(H1Error.InvalidMessage("lone CR inside a line"))
        i += 1
      }
      Right(Some(H1Line(latin1(pos, lf - 1), lf + 1)))
    }
  }

  private def indexOfLF(from: Int): Int = {
    var i = from
    while (i < end) {
      if (buffer(i) == LF) return i
      i += 1
    }
    -1
  }

  private def latin1(from: Int, until: Int): String = {
    val chars = new Array[Char](until - from)
    var i     = 0
    while (from + i < until) {
      chars(i) = (buffer(from + i) & 0xff).toChar
      i += 1
    }
    new String(chars)
  }

  private def parseRequestLine(content: String): Either[H1Error, (String, String)] = {
    val first  = content.indexOf(' ')
    val second = if (first < 0) -1 else content.indexOf(' ', first + 1)
    if (first <= 0 || second <= first + 1 || second == content.length - 1 || content.indexOf(' ', second + 1) >= 0)
      Left(H1Error.InvalidMessage("invalid request line: '" + content + "'"))
    else {
      val method  = content.substring(0, first)
      val target  = content.substring(first + 1, second)
      val version = content.substring(second + 1)
      if (content.length > limits.maxRequestLineLength) Left(H1Error.LineTooLong("request line exceeds the bound"))
      else if (!H1Validation.isToken(method)) Left(H1Error.InvalidMessage("invalid method: '" + method + "'"))
      else if (!H1Validation.isValidTarget(target)) Left(H1Error.InvalidMessage("invalid target: '" + target + "'"))
      else if (version != "HTTP/1.1") Left(H1Error.InvalidMessage("unsupported version: '" + version + "'"))
      else Right((method, target))
    }
  }

  private def parseStatusLine(content: String): Either[H1Error, (Int, String)] = {
    if (content.length > limits.maxStatusLineLength) return Left(H1Error.LineTooLong("status line exceeds the bound"))
    if (!content.startsWith("HTTP/1.1 ") || content.length < 12)
      return Left(H1Error.InvalidMessage("invalid status line: '" + content + "'"))
    val digits = content.substring(9, 12)
    if (!isDigits(digits) || content.length < 13 || content.charAt(12) != ' ')
      return Left(H1Error.InvalidMessage("invalid status line: '" + content + "'"))
    val status = (digits.charAt(0) - '0') * 100 + (digits.charAt(1) - '0') * 10 + (digits.charAt(2) - '0')
    if (status < 100 || status > 599) return Left(H1Error.InvalidMessage("invalid status code: '" + digits + "'"))
    val reason = content.substring(13)
    if (!H1Validation.isReasonPhrase(reason)) Left(H1Error.InvalidMessage("invalid reason phrase"))
    else Right((status, reason))
  }

  private def isDigits(s: String): Boolean = {
    var i = 0
    if (s.isEmpty) return false
    while (i < s.length) {
      if (s.charAt(i) < '0' || s.charAt(i) > '9') return false
      i += 1
    }
    true
  }

  private def parseHeaders(pos: Int): Either[H1Error, Option[H1ParsedHeaders]] = {
    val fields       = List.newBuilder[H1Header]
    var cursor       = pos
    var sectionBytes = 0
    var count        = 0
    var done         = false
    var failure      = Option.empty[H1Error]
    while (!done && failure.isEmpty) {
      readLine(cursor) match {
        case Left(error)       => failure = Some(error)
        case Right(None)       => return Right(None)
        case Right(Some(line)) =>
          if (line.content.isEmpty) {
            done = true
            cursor = line.next
          } else {
            parseHeaderField(line.content) match {
              case Left(error)  => failure = Some(error)
              case Right(field) =>
                sectionBytes += (line.content.length + 2)
                count += 1
                if (field.name.length > limits.maxHeaderNameLength)
                  failure = Some(H1Error.HeaderNameTooLong("header name exceeds the bound: '" + field.name + "'"))
                else if (field.value.length > limits.maxHeaderValueLength)
                  failure = Some(H1Error.HeaderValueTooLong("header value exceeds the bound"))
                else if (sectionBytes > limits.maxHeaderSectionBytes)
                  failure = Some(H1Error.HeaderSectionTooLarge("header section exceeds the bound"))
                else if (count > limits.maxHeaderCount)
                  failure = Some(H1Error.TooManyHeaders("too many header fields"))
                else {
                  fields += field
                  cursor = line.next
                }
            }
          }
      }
    }
    if (failure.isDefined) Left(failure.get)
    else Right(Some(H1ParsedHeaders(H1Headers(fields.result()), cursor)))
  }

  private def parseHeaderField(content: String): Either[H1Error, H1Header] = {
    val first  = if (content.isEmpty) ' ' else content.charAt(0)
    if (first == ' ' || first == '\t') return Left(H1Error.InvalidMessage("obs-fold continuation is forbidden"))
    val colon  = content.indexOf(':')
    if (colon <= 0) return Left(H1Error.InvalidMessage("header field without a colon"))
    val before = content.charAt(colon - 1)
    if (before == ' ' || before == '\t') return Left(H1Error.InvalidMessage("whitespace before the header colon"))
    val name   = content.substring(0, colon)
    val value  = H1Validation.trimOWS(content.substring(colon + 1))
    if (!H1Validation.isToken(name)) Left(H1Error.InvalidMessage("invalid header name: '" + name + "'"))
    else if (!H1Validation.isHeaderValue(value)) Left(H1Error.InvalidMessage("invalid header value"))
    else Right(H1Header(name, value))
  }

  private def parseChunkedBody(pos: Int, announcedTrailers: Set[String]): Either[H1Error, Option[H1ChunkedBody]] = {
    val body    = new ArrayBuffer[Byte]()
    var cursor  = pos
    var failure = Option.empty[H1Error]
    var result  = Option.empty[H1ChunkedBody]
    while (failure.isEmpty && result.isEmpty) {
      readLine(cursor) match {
        case Left(error)       => failure = Some(error)
        case Right(None)       => return Right(None)
        case Right(Some(line)) =>
          if (line.content.isEmpty) failure = Some(H1Error.InvalidMessage("empty chunk-size line"))
          else if (line.content.length > limits.maxChunkLineLength)
            failure = Some(H1Error.LineTooLong("chunk-size line exceeds the bound"))
          else {
            H1Validation.parseChunkSize(line.content) match {
              case Left(error) => failure = Some(error)
              case Right(size) =>
                if (size > limits.maxUndecodedBytes.toLong)
                  failure = Some(H1Error.BodyTooLarge("chunk size exceeds the retention bound"))
                else if (body.size.toLong + size > limits.maxUndecodedBytes.toLong)
                  failure = Some(H1Error.BodyTooLarge("chunked body exceeds the retention bound"))
                else if (size == 0L) {
                  parseTrailers(line.next, announcedTrailers) match {
                    case Left(error)           => failure = Some(error)
                    case Right(None)           => return Right(None)
                    case Right(Some(trailers)) =>
                      result = Some(H1ChunkedBody(Chunk.fromArray(body.toArray), trailers.headers, trailers.next))
                  }
                } else {
                  val dataStart = line.next
                  val needed    = size + 2L
                  if ((end - dataStart).toLong < needed) return Right(None)
                  val dataEnd   = dataStart + size.toInt
                  if (buffer(dataEnd) != CR || buffer(dataEnd + 1) != LF)
                    failure = Some(H1Error.InvalidMessage("chunk data terminator must be CRLF"))
                  else {
                    var i = dataStart
                    while (i < dataEnd) {
                      body += buffer(i)
                      i += 1
                    }
                    cursor = dataEnd + 2
                  }
                }
            }
          }
      }
    }
    if (failure.isDefined) Left(failure.get)
    else Right(result)
  }

  private def parseTrailers(pos: Int, announcedTrailers: Set[String]): Either[H1Error, Option[H1ParsedTrailers]] = {
    val fields       = List.newBuilder[H1Header]
    var cursor       = pos
    var sectionBytes = 0
    var count        = 0
    var done         = false
    var failure      = Option.empty[H1Error]
    while (!done && failure.isEmpty) {
      readLine(cursor) match {
        case Left(error)       => failure = Some(error)
        case Right(None)       => return Right(None)
        case Right(Some(line)) =>
          if (line.content.isEmpty) {
            done = true
            cursor = line.next
          } else {
            parseHeaderField(line.content) match {
              case Left(error)  => failure = Some(error)
              case Right(field) =>
                sectionBytes += (line.content.length + 2)
                count += 1
                if (field.name.equalsIgnoreCase("Content-Length") || field.name.equalsIgnoreCase("Transfer-Encoding"))
                  failure = Some(H1Error.AmbiguousFraming("framing header inside trailers: '" + field.name + "'"))
                else if (field.name.length > limits.maxHeaderNameLength)
                  failure = Some(H1Error.HeaderNameTooLong("trailer name exceeds the bound: '" + field.name + "'"))
                else if (field.value.length > limits.maxHeaderValueLength)
                  failure = Some(H1Error.HeaderValueTooLong("trailer value exceeds the bound"))
                else if (sectionBytes > limits.maxTrailerSectionBytes)
                  failure = Some(H1Error.TrailerSectionTooLarge("trailer section exceeds the bound"))
                else if (count > limits.maxTrailerCount)
                  failure = Some(H1Error.TooManyTrailers("too many trailer fields"))
                else {
                  fields += field
                  cursor = line.next
                }
            }
          }
      }
    }
    if (failure.isDefined) Left(failure.get)
    else {
      val parsed      = fields.result()
      val unannounced = parsed.find(header => !announcedTrailers.contains(header.name.toLowerCase))
      unannounced match {
        case Some(header) => Left(H1Error.AmbiguousFraming("unannounced trailer field: '" + header.name + "'"))
        case None         => Right(Some(H1ParsedTrailers(H1Headers(parsed), cursor)))
      }
    }
  }

  private def parseResponse(pos: Int, requestMethod: String): Either[H1Error, Option[H1CompletedResponse]] =
    readLine(pos) match {
      case Left(error)       => Left(error)
      case Right(None)       => Right(None)
      case Right(Some(line)) =>
        parseStatusLine(line.content) match {
          case Left(error)             => Left(error)
          case Right((status, reason)) =>
            parseHeaders(line.next) match {
              case Left(error)          => Left(error)
              case Right(None)          => Right(None)
              case Right(Some(headers)) =>
                if (isNoBody(status, requestMethod)) {
                  if (headers.headers.contains("Transfer-Encoding"))
                    Left(H1Error.AmbiguousFraming("Transfer-Encoding is forbidden on a bodyless response"))
                  else
                    Right(
                      Some(
                        H1CompletedResponse(
                          H1Response(status, reason, headers.headers, emptyChunk, H1BodyFraming.Empty, H1Headers.Empty),
                          headers.next,
                        ),
                      ),
                    )
                } else {
                  H1Framing.resolve(
                    headers.headers,
                    limits,
                    isResponse = true,
                    statusCode = status,
                    requestMethod = requestMethod,
                  ) match {
                    case Left(error)    => Left(error)
                    case Right(framing) =>
                      framing match {
                        case H1BodyFraming.Empty         =>
                          Right(
                            Some(
                              H1CompletedResponse(
                                H1Response(status, reason, headers.headers, emptyChunk, framing, H1Headers.Empty),
                                headers.next,
                              ),
                            ),
                          )
                        case H1BodyFraming.Fixed(length) =>
                          val available = (end - headers.next).toLong
                          if (available < length) Right(None)
                          else {
                            val body = copyChunk(headers.next, length.toInt)
                            Right(
                              Some(
                                H1CompletedResponse(
                                  H1Response(status, reason, headers.headers, body, framing, H1Headers.Empty),
                                  headers.next + length.toInt,
                                ),
                              ),
                            )
                          }
                        case H1BodyFraming.Chunked       =>
                          parseChunkedBody(headers.next, announcedTrailerNames(headers.headers)) match {
                            case Left(error)          => Left(error)
                            case Right(None)          => Right(None)
                            case Right(Some(chunked)) =>
                              Right(
                                Some(
                                  H1CompletedResponse(
                                    H1Response(
                                      status,
                                      reason,
                                      headers.headers,
                                      chunked.body,
                                      framing,
                                      chunked.trailers,
                                    ),
                                    chunked.next,
                                  ),
                                ),
                              )
                          }
                      }
                  }
                }
            }
        }
    }

  private def isNoBody(status: Int, requestMethod: String): Boolean =
    (status >= 100 && status <= 199) || status == 204 || status == 304 ||
      H1Validation.equalsIgnoreCase(requestMethod, "HEAD")

  private val emptyChunk: Chunk[Byte] = Chunk.fromArray(new Array[Byte](0))

  private def copyChunk(from: Int, length: Int): Chunk[Byte] = {
    val out = new Array[Byte](length)
    System.arraycopy(buffer, from, out, 0, length)
    Chunk.fromArray(out)
  }
}

private[h1] object H1Decoder {
  sealed trait RequestState
  case object ReqLine      extends RequestState
  case object Headers      extends RequestState
  case object AfterHeaders extends RequestState
  case object BodyFixed    extends RequestState
  case object ChunkSize    extends RequestState
  case object ChunkData    extends RequestState
  case object ChunkTrailer extends RequestState

  sealed trait LineResult
  case object LineBlocked                                extends LineResult
  final case class LineFound(content: String, next: Int) extends LineResult
  final case class LineFailed(error: H1Error)            extends LineResult
}
