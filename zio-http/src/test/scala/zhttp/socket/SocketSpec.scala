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
      val text        = "Cat ipsum dolor sit amet"
      val environment = ZStream.environment[String]
      val socket      = Socket
        .fromStream(environment)
        .provide(text)
        .execute("")

      assertM(socket.runCollect) {
        equalTo(Chunk(text))
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
    } + testM("ordered provide") {
      val socket = Socket.collect[Int] { case _ =>
        ZStream.environment[Int]
      }

      val socketA: Socket[Int, Nothing, Int, Int] = socket.provide(12)
      val socketB: Socket[Int, Nothing, Int, Int] = socketA.provide(1)
      val socketC: Socket[Any, Nothing, Int, Int] = socketB.provide(42)

      assertM(socketC.execute(1000).runCollect)(equalTo(Chunk(12)))
    }
  }
}
