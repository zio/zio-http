package zhttp.http

import zio.test.Assertion.{equalTo, isNone}
import zio.test._

import java.util.UUID

object RouteDecoderModuleSpec extends DefaultRunnableSpec {
  def spec = suite("RouteDecoderModule")(
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
      test("negative above -2147483649")(assert(int.unapply("-2147483648"))(equalTo(Some(-2147483648.intValue())))),
      test("number smaller than -2147483648")(assert(int.unapply("-2147483649"))(isNone)),
    ),
    suite("long")(
      test("positive below 9223372036854775808")(
        assert(long.unapply("9223372036854775807"))(equalTo(Some(9223372036854775807L.longValue()))),
      ),
      test("number greater than 9223372036854775807")(assert(long.unapply("9223372036854775808"))(isNone)),
      test("negative above -9223372036854775809")(
        assert(long.unapply("-9223372036854775808"))(equalTo(Some(-9223372036854775808L.longValue()))),
      ),
      test("number smaller than -9223372036854775808")(assert(long.unapply("-9223372036854775809"))(isNone)),
    ),
    suite("float")(
      test("positive lesser than or equal to max positive limit")(
        assert(float.unapply("3.4028235E38"))(equalTo(Some(Float.MaxValue))),
      ),
      test("positive greater than the max limit")(
        assert(float.unapply("3.4028235E39"))(equalTo(Some(Float.PositiveInfinity))),
      ),
      test("positive greater than or equal to min positive limit")(
        assert(float.unapply("1.4E-45"))(equalTo(Some(1.4e-45f))),
      ),
      test("positive lesser than the min positive limit")(assert(float.unapply("1.4E-46"))(equalTo(Some(0.0f)))),
      test("negative greater than or equal to min negative limit")(
        assert(float.unapply("-3.4028235E38"))(equalTo(Some(Float.MinValue))),
      ),
      test("negative lesser than the min negative limit")(
        assert(float.unapply("-3.4028235E39"))(equalTo(Some(Float.NegativeInfinity))),
      ),
      test("negative lesser than or equal to max negative limit")(
        assert(float.unapply("-1.4E-45"))(equalTo(Some(-1.4e-45f))),
      ),
      test("negative greater than the max negative limit")(assert(float.unapply("-1.4E-46"))(equalTo(Some(0.0f)))),
      test("illegal strings")(assert(float.unapply("some other string"))(isNone)),
    ),
    suite("double")(
      test("positive lesser than or equal to max positive limit")(
        assert(double.unapply("1.7976931348623157E308"))(equalTo(Some(Double.MaxValue))),
      ),
      test("positive greater than the max limit")(
        assert(double.unapply("1.7976931348623157E309"))(equalTo(Some(Double.PositiveInfinity))),
      ),
      test("positive greater than or equal to min positive limit")(
        assert(double.unapply("4.9E-324"))(equalTo(Some(4.9e-324d))),
      ),
      test("positive lesser than the min positive limit")(assert(double.unapply("4.9E-325"))(equalTo(Some(0.0d)))),
      test("negative greater than or equal to min negative limit")(
        assert(double.unapply("-1.7976931348623157E308"))(equalTo(Some(Double.MinValue))),
      ),
      test("negative lesser than the min negative limit")(
        assert(double.unapply("-1.7976931348623157E309"))(equalTo(Some(Double.NegativeInfinity))),
      ),
      test("negative lesser than or equal to max negative limit")(
        assert(double.unapply("-4.9E-324"))(equalTo(Some(-4.9e-324d))),
      ),
      test("negative greater than the max negative limit")(assert(double.unapply("-4.9E-325"))(equalTo(Some(0.0d)))),
      test("illegal strings")(assert(double.unapply("some other string"))(isNone)),
    ),
    suite("uuid")(
      test("eligible uuid string")(
        assert(uuid.unapply("123e4567-e89b-12d3-a456-556642440000"))(
          equalTo(Some(UUID.fromString("123e4567-e89b-12d3-a456-556642440000"))),
        ),
      ),
      test("illegal string")(assert(uuid.unapply("something"))(isNone)),
    ),
  )

}
