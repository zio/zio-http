package zio.http.h1

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.net.Socket
import java.nio.charset.StandardCharsets

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.context.Context
import zio.test.TestResult

import zio.http.{BindAddress, BoundAddress, Connector, LoomServer, Protocol, Routes}

/**
 * Shared raw HTTP/1.1 fixture for the Loom H1 engine specs.
 *
 * Binds a real [[zio.http.LoomServer]] with a cleartext `Protocol.H1C`
 * connector to an ephemeral loopback port plus a minimal raw HTTP/1.1 client
 * (request bytes out, status line + headers + Content-Length / chunked /
 * close-delimited bodies back). Every test runs blocking socket IO off the ZIO
 * executor via `ZIO.attemptBlocking` at the call site.
 */
object H1RawClientFixture {

  def withH1Server[R](routes: Routes[Any])(
    use: Int => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    withH1Server(routes, Connector(bind = BindAddress.localhost(0), protocol = Protocol.H1C()))(use)

  def withH1Server[R](routes: Routes[Any], connector: Connector)(
    use: Int => ZIO[R, Throwable, TestResult],
  ): ZIO[R & Scope, Throwable, TestResult] =
    ZIO
      .acquireRelease(
        ZIO.attempt(LoomServer(connector).serve(routes, Context.empty)),
      )(h => ZIO.succeed(h.shutdownAndWait()))
      .flatMap { handle =>
        val port = handle.bindings.head.address match {
          case BoundAddress.Tcp(_, p) => p
          case other                  => throw new AssertionError("Expected TCP binding but found: " + other)
        }
        use(port)
      }

  final case class RawH1Response(
    status: Int,
    headers: Map[String, List[String]],
    body: Chunk[Byte],
  ) {
    def header(name: String): Option[String] =
      headers.get(name.toLowerCase(java.util.Locale.ROOT)).flatMap(_.headOption)

    def bodyText: String =
      new String(body.toArray, StandardCharsets.UTF_8)
  }

  final class RawH1Client(val port: Int) extends AutoCloseable {
    val socket      = new Socket("127.0.0.1", port)
    socket.setSoTimeout(10000)
    private val out = socket.getOutputStream
    private val in  = socket.getInputStream

    def sendRaw(text: String): Unit = {
      out.write(text.getBytes(StandardCharsets.US_ASCII))
      out.flush()
    }

    def sendRaw(bytes: Array[Byte]): Unit = {
      out.write(bytes)
      out.flush()
    }

    /**
     * Reads one response; throws [[EOFException]] when the server closed
     * without one.
     */
    def readResponse(): RawH1Response =
      readResponse(readBody = true)

    /**
     * Reads one response, optionally skipping the body. HEAD responses
     * advertise `Content-Length` with zero body bytes on the wire, so HEAD
     * callers must pass `readBody = false`.
     */
    def readResponse(readBody: Boolean): RawH1Response = {
      val statusLine = readLine()
      val parts      = statusLine.split(" ", 3)
      if (parts.length < 2 || !parts(0).startsWith("HTTP/"))
        throw new AssertionError("Bad H1 status line: " + statusLine)
      val status     = parts(1).toInt
      var headers    = Map.empty[String, List[String]]
      var line       = readLine()
      while (line.nonEmpty) {
        val colon = line.indexOf(':')
        if (colon < 0) throw new AssertionError("Bad H1 header line: " + line)
        val name  = line.substring(0, colon).trim.toLowerCase(java.util.Locale.ROOT)
        val value = line.substring(colon + 1).trim
        headers = headers.updated(name, headers.getOrElse(name, Nil) :+ value)
        line = readLine()
      }
      val chunked    =
        headers.get("transfer-encoding").exists(_.exists(_.toLowerCase(java.util.Locale.ROOT).contains("chunked")))
      val body       =
        if (!readBody) Chunk.empty[Byte]
        else if (chunked) readChunked()
        else
          headers.get("content-length").flatMap(_.headOption.map(_.trim)) match {
            case Some(length) => readExact(length.toInt)
            case None         => readUntilClose()
          }
      RawH1Response(status, headers, body)
    }

    /** Reads one byte; returns -1 on clean server close. */
    def readByte(): Int = in.read()

    def close(): Unit = socket.close()

    private def readChunked(): Chunk[Byte] = {
      var body = Chunk.empty[Byte]
      var done = false
      while (!done) {
        val line     = readLine()
        val semi     = line.indexOf(';')
        val sizeText = (if (semi < 0) line else line.substring(0, semi)).trim
        val size     = Integer.parseInt(sizeText, 16)
        if (size == 0) {
          var trailer = readLine()
          while (trailer.nonEmpty) trailer = readLine()
          done = true
        } else {
          body = body ++ readExact(size)
          val crlf = readExactBytes(2)
          if (crlf(0) != '\r' || crlf(1) != '\n') throw new AssertionError("Chunk data missing CRLF")
        }
      }
      body
    }

    private def readExact(length: Int): Chunk[Byte] =
      Chunk.fromArray(readExactBytes(length))

    private def readExactBytes(length: Int): Array[Byte] = {
      val out    = new Array[Byte](length)
      var offset = 0
      while (offset < length) {
        val n = in.read(out, offset, length - offset)
        if (n < 0) throw new EOFException("EOF after " + offset + " of " + length + " bytes")
        offset += n
      }
      out
    }

    private def readUntilClose(): Chunk[Byte] = {
      var body = Chunk.empty[Byte]
      val tmp  = new Array[Byte](8192)
      var n    = in.read(tmp)
      while (n >= 0) {
        body = body ++ Chunk.fromArray(java.util.Arrays.copyOf(tmp, n))
        n = in.read(tmp)
      }
      body
    }

    private def readLine(): String = {
      val buf   = new ByteArrayOutputStream()
      var done  = false
      while (!done) {
        val b = in.read()
        if (b < 0) throw new EOFException("EOF inside H1 response line")
        if (b == '\n') done = true
        else buf.write(b)
      }
      val bytes = buf.toByteArray
      val len   = if (bytes.length > 0 && bytes(bytes.length - 1) == '\r') bytes.length - 1 else bytes.length
      new String(bytes, 0, len, StandardCharsets.US_ASCII)
    }
  }
}
