package zhttp.socket

import zio.Chunk
import zio.stream.{Sink, ZStream}
import zio.test.Assertion._
import zio.test._

object SocketSpec extends DefaultRunnableSpec {
  def spec = suite("SocketSpec") {
    OperatorSpec
  }

  def OperatorSpec = suite("OperatorSpec") {
    testM("fromStream provide") {
      val stream: ZStream[Int, Nothing, Int]     = ZStream.environment[Int]
      val socket: Socket[Any, Nothing, Any, Int] = Socket.fromStream(stream).provide(42)

      assertM(socket.execute(24).run(Sink.collectAll))(equalTo(Chunk(42)))
    } + testM("fromFunction provide") {
      val streamFunc: Int => ZStream[Int, Nothing, Int] = (_: Int) => ZStream.environment[Int]
      val socket: Socket[Any, Nothing, Int, Int]        = Socket.fromFunction(streamFunc).provide(42)

      assertM(socket.execute(24).run(Sink.collectAll))(equalTo(Chunk(42)))
    }
  }
}
