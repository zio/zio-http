package zhttp.socket

import zio.stream._
import zio.test.Assertion._
import zio.test._
import zio._

object SocketSpec extends DefaultRunnableSpec {
  def spec = suite("SocketSpec") {
    OperatorSpec
  }

  def OperatorSpec = suite("OperatorSpec") {
    testM("fromStream provide") {
      val environment: ZStream[Int, Nothing, Int] = ZStream.environment[Int]
      val fromStream                              = Socket
        .fromStream(environment)
        .provide(123)
        .execute(2)

      assertM(fromStream.runCollect)(equalTo(Chunk(123)))
    } + testM("fromFunction provide") {
      val environment: ZStream[Int, Nothing, Int] = ZStream.environment[Int]
      val fromFunction                            = Socket
        .fromFunction((_: Int) => environment)
        .provide(123)
        .execute(2)

      assertM(fromFunction.runCollect)(equalTo(Chunk(123)))
    }
  }
}
