package zio.http.model.headers.values

import zio.Scope
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue, check}

import zio.http.internal.HttpGen

object AcceptEncodingSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("AcceptEncoding suite")(
    suite("Encoding header value transformation should be symmetrical")(
      test("single value") {
        check(HttpGen.acceptEncodingSingleValueWithWeight) { value =>
          assertTrue(AcceptEncoding.toAcceptEncoding(AcceptEncoding.fromAcceptEncoding(value)) == value)
        }
      },
      test("multiple values") {
        check(HttpGen.acceptEncoding) { value =>
          assertTrue(AcceptEncoding.toAcceptEncoding(AcceptEncoding.fromAcceptEncoding(value)) == value)
        }
      },
    ),
  )
}
