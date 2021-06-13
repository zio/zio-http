package zhttp.http

import zio._
import zio.stream._
import zio.test.Assertion._
import zio.test._

object ContentSpec extends DefaultRunnableSpec { self =>
  trait R; trait E

  override def spec: ZSpec[Environment, Failure] = suite("Content")(
    suite("data")(
      testM("streaming content") {
        assertM(typeCheck("Mock[Content[R, E, Buffered]].map(_.data).is[ZStream[R, E, Byte]]"))(isRight(anything))
      },
      testM("complete content") {
        assertM(typeCheck("Mock[Content[R, E, Complete]].map(_.data).is[Chunk[Byte]]"))(isRight(anything))
      },
      testM("opaque content") {
        assertM(typeCheck("Mock[Content[R, E, Opaque]].map(_.data)"))(
          isLeft(equalTo("data is unavailable on this type of content")),
        )
      },
      testM("nothing content") {
        assertM(typeCheck("Mock[Content[R, E, Nothing]].map(_.data)"))(
          isLeft(equalTo("data is unavailable on this type of content")),
        )
      },
      testM("any content") {
        assertM(typeCheck("Mock[Content[R, E, Any]].map(_.data)"))(
          isLeft(equalTo("data is unavailable on this type of content")),
        )
      },
    ),
  )
}
