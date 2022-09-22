package zio.http.model

import zio.Scope
import zio.http.internal.HttpGen
import zio.http.model.HeaderValue.Encoding
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue, check}

object HeaderValueSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("HeaderValues suite")(
    suite("Encoding header value transformation should be symmetrical")(
      test("single value") {
        check(HttpGen.acceptEncodingSingleValueWithWeight) { value =>
          assertTrue(Encoding.toEncoding(Encoding.fromEncoding(value)) == value)
        }
      },
      test("multiple values") {
        check(HttpGen.acceptEncoding) { value =>
          assertTrue(Encoding.toEncoding(Encoding.fromEncoding(value)) == value)
        }
      },
    ),
  )
}
