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
    suite("short")(
      test("positive below 32768")(assert(short.unapply("32767"))(equalTo(Some(32767.shortValue())))),
      test("number greater than 32767")(assert(short.unapply("32768"))(isNone)),
      test("negative above -32769")(assert(short.unapply("-32768"))(equalTo(Some(-32768.shortValue())))),
      test("number smaller than -32768")(assert(short.unapply("-32769"))(isNone)),
    ),
    suite("int")(
      test("positive below 2147483648")(assert(int.unapply("2147483647"))(equalTo(Some(2147483647.intValue())))),
      test("number greater than 2147483647")(assert(int.unapply("2147483648"))(isNone)),
      test("negative above -2147483648")(assert(int.unapply("-2147483647"))(equalTo(Some(-2147483647.intValue())))),
      test("number smaller than -2147483648")(assert(int.unapply("-2147483651"))(isNone)),
    ),
  )

}
