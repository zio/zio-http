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
    suite("byte")(
      test("positive below 128")(assert(byte.unapply("127"))(equalTo(Some(127.byteValue())))),
      test("number greater than 127")(assert(byte.unapply("128"))(isNone)),
      test("negative above -129")(assert(byte.unapply("-127"))(equalTo(Some(-127.byteValue())))),
      test("number smaller than -128")(assert(byte.unapply("-129"))(isNone)),
    ),
  )

}
