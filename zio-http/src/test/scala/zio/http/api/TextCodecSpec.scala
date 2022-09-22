package zio.http.api

import zio.Scope
import zio.http.internal.HttpGen
import zio.test._

object TextCodecSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("TextCodec suite")(
    suite("Encoding codec should be symmetrical")(
      test("single value") {
        check(HttpGen.acceptEncodingSingleValueWithWeight) { value =>
          assertTrue(TextCodec.encoding.decode(TextCodec.encoding.encode(value)).get == value)
        }
      },
      test("multiple values") {
        check(HttpGen.acceptEncoding) { value =>
          assertTrue(TextCodec.encoding.decode(TextCodec.encoding.encode(value)).get == value)
        }
      },
    ),
  )
}
