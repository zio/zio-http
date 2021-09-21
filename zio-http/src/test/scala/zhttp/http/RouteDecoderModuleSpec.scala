package zhttp.http

import zio.test.Assertion.{equalTo, isNone}
import zio.test._

object RouteDecoderModuleSpec extends DefaultRunnableSpec {
  def spec  = suite("RouteDecoderModule")(
    suite("boolean")(
test("true")(assert(boolean.unapply("true"))(equalTo(Some(true)))),
      test("false")(assert(boolean.unapply("false"))(equalTo(Some(false)))),
      test("something else")(assert(boolean.unapply("something"))(isNone)),
      ),
  )

}
