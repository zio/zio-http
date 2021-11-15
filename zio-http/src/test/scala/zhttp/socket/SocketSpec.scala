package zhttp.socket

import zio._
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test._

object SocketSpec extends DefaultRunnableSpec {

  def spec = suite("SocketSpec") {
    OperationsSpec
  }

  def OperationsSpec = suite("Operations Spec") {
    testM("fromStream provide") {
      val environment = ZStream.environment[WebSocketFrame]
      val socket      = Socket
        .fromStream(environment)
        .provide(WebSocketFrame.text("Foo"))
        .execute(WebSocketFrame.text("Bar"))

      assertM(socket.runCollect) {
        equalTo(Chunk(WebSocketFrame.text("Foo")))
      }
    } + testM("fromFunction provide") {
      val environmentFunction = (_: Any) => ZStream.environment[WebSocketFrame]
      val socket              = Socket
        .fromFunction(environmentFunction)
        .provide(WebSocketFrame.text("Foo"))
        .execute(WebSocketFrame.text("Bar"))

      assertM(socket.runCollect) {
        equalTo(Chunk(WebSocketFrame.text("Foo")))
      }
    } + testM("collect provide") {
      val environment = ZStream.environment[WebSocketFrame]
      val socket      = Socket
        .collect[WebSocketFrame] { case WebSocketFrame.Pong =>
          environment
        }
        .provide(WebSocketFrame.ping)
        .execute(WebSocketFrame.pong)

      assertM(socket.runCollect) {
        equalTo(Chunk(WebSocketFrame.ping))
      }
    }
  }
}
