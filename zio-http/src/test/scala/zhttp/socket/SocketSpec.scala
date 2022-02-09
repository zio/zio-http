package zhttp.socket

import zhttp.http.Status
import zio._
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test.TestAspect.timeout
import zio.test._

object SocketSpec extends DefaultRunnableSpec {

  def spec = suite("SocketSpec") {
    operationsSpec
  } @@ timeout(5 seconds)

  def operationsSpec = suite("OperationsSpec") {
    test("fromStream provide") {
      val text        = "Cat ipsum dolor sit amet"
      val environment = ZStream.service[String]
      val socket      = Socket
        .fromStream(environment)
        .provideEnvironment(ZEnvironment(text))
        .execute("")

      assertM(socket.runCollect) {
        equalTo(Chunk(text))
      }
    } + test("fromFunction provide") {
      val environmentFunction = (_: Any) => ZStream.service[WebSocketFrame]
      val socket              = Socket
        .fromFunction(environmentFunction)
        .provideEnvironment(ZEnvironment(WebSocketFrame.text("Foo")))
        .execute(WebSocketFrame.text("Bar"))

      assertM(socket.runCollect) {
        equalTo(Chunk(WebSocketFrame.text("Foo")))
      }
    } + test("collect provide") {
      val environment = ZStream.service[WebSocketFrame]
      val socket      = Socket
        .collect[WebSocketFrame] { case WebSocketFrame.Pong =>
          environment
        }
        .provideEnvironment(ZEnvironment(WebSocketFrame.ping))
        .execute(WebSocketFrame.pong)

      assertM(socket.runCollect) {
        equalTo(Chunk(WebSocketFrame.ping))
      }
    } + test("ordered provide") {
      val socket = Socket.collect[Int] { case _ =>
        ZStream.service[Int]
      }

      val socketA: Socket[Int, Nothing, Int, Int] = socket.provideEnvironment(ZEnvironment(12))
      val socketB: Socket[Int, Nothing, Int, Int] = socketA.provideEnvironment(ZEnvironment(1))
      val socketC: Socket[Any, Nothing, Int, Int] = socketB.provideEnvironment(ZEnvironment(42))

      assertM(socketC.execute(1000).runCollect)(equalTo(Chunk(12)))
    } +
      test("echo") {
        assertM(Socket.echo(1).runCollect)(equalTo(Chunk(1)))
      } +
      test("empty") {
        assertM(Socket.empty(()).runCollect)(isEmpty)
      } +
      test("toHttp") {
        val http = Socket.succeed(WebSocketFrame.ping).toHttp
        assertM(http(()).map(_.status))(equalTo(Status.SWITCHING_PROTOCOLS))
      }
  }
}
