package zhttp.http

import zio.test.Assertion._
import zio.test._

object ResponseSpec extends DefaultRunnableSpec {
  trait R; trait E

  def spec = suite("Response")(
    suite("content")(
      testM("streaming content") {
        assertM(typeCheck("Mock[Response[R, E, Buffered]].map(_.content).is[Content[R, E, Buffered]]"))(
          isRight(anything),
        )
      },
      testM("complete content") {
        assertM(typeCheck("Mock[Response[R, E, Complete]].map(_.content).is[Content[R, E, Complete]]"))(
          isRight(anything),
        )
      },
      testM("opaque content") {
        assertM(typeCheck("Mock[Response[R, E, Opaque]].map(_.content).is[Content[R, E, Opaque]]"))(isRight(anything))
      },
      testM("nothing content") {
        assertM(typeCheck("Mock[Response[R, E, Nothing]].map(_.content)"))(
          isLeft(equalTo("content unavailable")),
        )
      },
      testM("any content") {
        assertM(typeCheck("Mock[Response[R, E, Any]].map(_.content)"))(
          isLeft(equalTo("content unavailable")),
        )
      },
    ),
  )
}
