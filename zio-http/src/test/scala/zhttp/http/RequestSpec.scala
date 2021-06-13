package zhttp.http

import zio.test._
import zio.test.Assertion._

object RequestSpec extends DefaultRunnableSpec {
  trait R; trait E

  def spec = suite("Request")(
    suite("content")(
      testM("streaming content") {
        assertM(typeCheck("Mock[Request[R, E, Buffered]].map(_.content).is[Content[R, E, Buffered]]"))(
          isRight(anything),
        )
      },
      testM("complete content") {
        assertM(typeCheck("Mock[Request[R, E, Complete]].map(_.content).is[Content[R, E, Complete]]"))(
          isRight(anything),
        )
      },
      testM("opaque content") {
        assertM(typeCheck("Mock[Request[R, E, Opaque]].map(_.content).is[Content[R, E, Opaque]]"))(isRight(anything))
      },
      testM("nothing content") {
        assertM(typeCheck("Mock[Request[R, E, Nothing]].map(_.content)"))(
          isLeft(equalTo("content unavailable")),
        )
      },
      testM("any content") {
        assertM(typeCheck("Mock[Request[R, E, Any]].map(_.content)"))(
          isLeft(equalTo("content unavailable")),
        )
      },
    ),
  )
}
