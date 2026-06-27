package zio.http.h2

import java.io.InputStream
import java.net.Socket
import java.nio.charset.StandardCharsets

import scala.annotation.experimental

import zio.blocks.chunk.Chunk
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.http.h2.H2Frame.{GoAway, Headers, Settings}
import zio.http.h2.hpack.{HeaderField, Hpack}
import zio.http.{BindAddress, BoundAddress, Connector, DefectHandler, Handler, Response, Route, Routes}

@experimental
object H2CSmokeTest {
  private val ClientPreface = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes(StandardCharsets.US_ASCII)
  private val StreamId      = 1

  def main(args: Array[String]): Unit = {
    val routes    = Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))
    val transport = new H2Transport(routes, Context.empty, Connector(bind = BindAddress.localhost(0)), DefectHandler.default)
    val handle    = transport.start()

    try {
      val port = handle.binding.address match {
        case BoundAddress.Tcp(_, value) => value
        case other                      => throw new AssertionError("Expected TCP binding but found: " + other)
      }

      val responseHeaders = exchangeGet(port)
      val statusHeader    = responseHeaders.find(_.name == ":status").map(_.value)

      if (!statusHeader.contains("200")) {
        throw new AssertionError(s"Expected HTTP/2 :status 200 but got $statusHeader")
      }
      println(s"✓ H2C smoke test passed: status=${statusHeader.getOrElse("<missing>")}, port=$port")
    } finally {
      handle.close0()
    }
  }

  private def exchangeGet(port: Int): List[HeaderField] = {
    val socket = new Socket("127.0.0.1", port)
    socket.setSoTimeout(5000)

    try {
      val input  = new FrameReader(socket.getInputStream)
      val output = socket.getOutputStream

      output.write(ClientPreface)
      output.write(FrameCodec.encode(Settings(ack = false, Nil)).toArray)
      output.flush()

      val serverSettings = input.readFrame()
      serverSettings match {
        case Settings(false, _) =>
          output.write(FrameCodec.encode(Settings(ack = true, Nil)).toArray)
          output.flush()
        case other              =>
          throw new AssertionError("Expected server SETTINGS frame but received: " + other)
      }

      output.write(FrameCodec.encode(buildGetHeadersFrame(port)).toArray)
      output.flush()

      awaitResponseHeaders(input)
    } finally {
      socket.close()
    }
  }

  private def buildGetHeadersFrame(port: Int): Headers = {
    val headerBlock = Hpack.encode(
      List(
        HeaderField(":method", "GET"),
        HeaderField(":path", "/"),
        HeaderField(":scheme", "http"),
        HeaderField(":authority", s"127.0.0.1:$port"),
      ),
    )

    Headers(StreamId, headerBlock, endStream = true, endHeaders = true)
  }

  private def awaitResponseHeaders(input: FrameReader): List[HeaderField] = {
    var done           = false
    var responseFields = List.empty[HeaderField]

    while (!done) {
      input.readFrame() match {
        case Settings(true, _)                          => ()
        case Settings(false, _)                         => throw new AssertionError("Unexpected non-ack SETTINGS frame after handshake")
        case Headers(StreamId, headerBlock, _, _, _, _) =>
          responseFields = Hpack.decode(headerBlock) match {
            case Right(fields) => fields
            case Left(error)   => throw new AssertionError("Failed to decode response headers: " + error)
          }
          done = true
        case GoAway(_, errorCode, debugData)           =>
          val debug = new String(debugData.toArray, StandardCharsets.UTF_8)
          throw new AssertionError(s"Server sent GOAWAY: error=$errorCode debug=$debug")
        case other                                     =>
          throw new AssertionError("Unexpected frame while waiting for response headers: " + other)
      }
    }

    responseFields
  }

  private final class FrameReader(input: InputStream) {
    var buffer = Chunk.empty[Byte]

    def readFrame(): H2Frame = {
      while (true) {
        FrameCodec.decode(buffer) match {
          case Right((decoded, rest))      =>
            buffer = rest
            return decoded
          case Left(H2Error.InsufficientData) =>
            val chunk = new Array[Byte](8192)
            val read  = input.read(chunk)
            if (read < 0) throw new AssertionError("Connection closed before an HTTP/2 frame was fully received")
            buffer = buffer ++ Chunk.fromArray(java.util.Arrays.copyOf(chunk, read))
          case Left(error)                    =>
            throw new AssertionError("Failed to decode HTTP/2 frame: " + error)
        }
      }
      throw new AssertionError("Unreachable HTTP/2 frame read state")
    }
  }
}
