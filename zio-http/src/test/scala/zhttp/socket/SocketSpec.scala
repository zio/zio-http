package zhttp.socket

import zhttp.http.Status
import zhttp.socket.SocketSpec.testM
import zio._
import zio.duration.durationInt
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test.TestAspect.timeout
import zio.test._
import zio.test.environment.{TestClock, TestConsole}

object SocketSpec extends ZIOSpecDefault {

  def spec = suite("SocketSpec") {
    operationsSpec
  } @@ timeout(5 seconds)

  def operationsSpec = suite("OperationsSpec")(
    test("fromStream provide") {
      val text        = "Cat ipsum dolor sit amet"
      val environment = ZStream.service[String]
      val socket      = Socket
        .fromStream(environment)
        .provideEnvironment(ZEnvironment(text))
        .execute("")

      assertZIO(socket.runCollect) {
        equalTo(Chunk(text))
      }
    } + test("fromFunction provide") {
      val environmentFunction = (_: Any) => ZStream.service[WebSocketFrame]
      val socket              = Socket
        .fromFunction(environmentFunction)
        .provideEnvironment(ZEnvironment(WebSocketFrame.text("Foo")))
        .execute(WebSocketFrame.text("Bar"))

      assertZIO(socket.runCollect) {
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

      assertZIO(socket.runCollect) {
        equalTo(Chunk(WebSocketFrame.ping))
      }
    } + test("ordered provide") {
      val socket = Socket.collect[Int] { case _ =>
        ZStream.service[Int]
      }

      val socketA: Socket[Int, Nothing, Int, Int] = socket.provideEnvironment(ZEnvironment(12))
      val socketB: Socket[Int, Nothing, Int, Int] = socketA.provideEnvironment(ZEnvironment(1))
      val socketC: Socket[Any, Nothing, Int, Int] = socketB.provideEnvironment(ZEnvironment(42))

      assertZIO(socketC.execute(1000).runCollect)(equalTo(Chunk(12)))
    } +
      test("echo") {
        assertZIO(Socket.echo(1).runCollect)(equalTo(Chunk(1)))
      } +
      test("empty") {
        assertZIO(Socket.empty(()).runCollect)(isEmpty)
      } +
      test("toHttp") {
        val http = Socket.succeed(WebSocketFrame.ping).toHttp
        assertZIO(http(()).map(_.status))(equalTo(Status.SwitchingProtocols))
      },
    test("delay") {
      val socket  =
        Socket.from(1, 2, 3).delay(1.second).mapZIO(i => clock.instant.map(time => (time.getEpochSecond, i)))
      val program = for {
        f <- socket(()).runCollect.fork
        _ <- TestClock.adjust(10 second)
        l <- f.join
      } yield l.toList
      assertZIO(program)(equalTo(List((1L, 1), (2L, 2), (3L, 3))))
    },
    test("tap") {
      val socket  = Socket.from(1, 2, 3).tap(i => zio.console.putStrLn(i.toString))
      val program = for {
        _ <- socket(()).runDrain
        l <- TestConsole.output
      } yield l
      assertZIO(program)(equalTo(Vector("1\n", "2\n", "3\n")))
    },
  }
}
