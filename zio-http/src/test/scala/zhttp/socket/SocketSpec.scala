package zhttp.socket

import zhttp.http.Status
import zio._
import zio.duration.durationInt
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test.TestAspect.timeout
import zio.test._

object SocketSpec extends DefaultRunnableSpec {

  def spec = suite("SocketSpec") {
    operationsSpec
  } @@ timeout(5 seconds)

  def operationsSpec = suite("OperationsSpec") {
    testM("fromStream provide") {
      val text        = "Cat ipsum dolor sit amet"
      val environment = ZStream.environment[String]
      val socket      = Socket
        .fromStream(environment)
        .provideEnvironment(text)
        .execute("")

      assertM(socket.runCollect)(equalTo(Chunk(text)))
    } +
      testM("fromFunction provide") {
        val environmentFunction = (_: Any) => ZStream.environment[WebSocketFrame]
        val socket              = Socket
          .fromFunction(environmentFunction)
          .provideEnvironment(WebSocketFrame.text("Foo"))
          .execute(WebSocketFrame.text("Bar"))

        assertM(socket.runCollect)(equalTo(Chunk(WebSocketFrame.text("Foo"))))
      } +
      testM("collect provide") {
        val environment = ZStream.environment[WebSocketFrame]
        val socket      = Socket
          .collect[WebSocketFrame] { case WebSocketFrame.Pong =>
            environment
          }
          .provideEnvironment(WebSocketFrame.ping)
          .execute(WebSocketFrame.pong)

        assertM(socket.runCollect)(equalTo(Chunk(WebSocketFrame.ping)))
      } +
      testM("ordered provide") {
        val socket = Socket.collect[Int] { case _ =>
          ZStream.environment[Int]
        }

        val socketA: Socket[Int, Nothing, Int, Int] = socket.provideEnvironment(12)
        val socketB: Socket[Int, Nothing, Int, Int] = socketA.provideEnvironment(1)
        val socketC: Socket[Any, Nothing, Int, Int] = socketB.provideEnvironment(42)

        assertM(socketC.execute(1000).runCollect)(equalTo(Chunk(12)))
      } +
      testM("echo") {
        assertM(Socket.echo(1).runCollect)(equalTo(Chunk(1)))
      } +
      testM("empty") {
        assertM(Socket.empty(()).runCollect)(isEmpty)
      } +
      testM("toHttp") {
        val http = Socket.succeed(WebSocketFrame.ping).toHttp
        assertM(http(()).map(_.status))(equalTo(Status.SwitchingProtocols))
      }
  }
}
