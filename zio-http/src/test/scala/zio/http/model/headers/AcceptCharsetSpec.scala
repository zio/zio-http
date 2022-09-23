package zio.http.model.headers

import zio.Scope
import zio.http.internal.HttpGen
import zio.http.model.headers.HeaderValues.AcceptCharset
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue, check}

object AcceptCharsetSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("AcceptCharset suite")(
    suite("Encoding header value transformation should be symmetrical")(
      test("charset") {
        check(HttpGen.acceptCharset()) { value =>
          assertTrue(AcceptCharset.toCharset(AcceptCharset.fromCharset(value)) == value)
        }
      },
    ),
  )
}
