package zhttp.http

import zhttp.internal.HttpMessageAssertions
import zhttp.service.EventLoopGroup
import zio.test.Assertion._
import zio.test._

object StatusSpec extends DefaultRunnableSpec with HttpMessageAssertions {
  private val env = EventLoopGroup.auto(1)

  def spec = suite("Status")(
    toAppSpec,
    toResponseSpec,
  ).provideCustomLayer(env)

  def toResponseSpec =
    suite("toResponse")(
      test("ok")(assert(Status.OK.toResponse.status)(equalTo(Status.OK))),
    )

  def toAppSpec = {
    suite("toApp")(
      testM("ok") {
        val res = Status.OK.toApp.getResponse
        assertM(res)(isResponse(responseStatus(200)))
      },
    )
  }
}
