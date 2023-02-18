package zio.http.model.headers.values

import zio.Scope
import zio.test._

import zio.http.model.headers.values.From.InvalidFromValue

object FromSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Nothing] =
    suite("From header suite")(
      test("parse valid value") {
        assertTrue(From.toFrom("test.test.tes@email.com") == From.FromValue("test.test.tes@email.com")) &&
        assertTrue(From.toFrom("test==@email.com") == From.FromValue("test==@email.com")) &&
        assertTrue(From.toFrom("test/d@email.com") == From.FromValue("test/d@email.com")) &&
        assertTrue(From.toFrom("test/d@email.com") == From.FromValue("test/d@email.com")) &&
        assertTrue(
          From.toFrom("test11!#$%&'*+-/=?^_`{|}~@email.com") == From.FromValue("test11!#$%&'*+-/=?^_`{|}~@email.com"),
        )

      },
      test("parse invalid value") {
        assertTrue(From.toFrom("t") == InvalidFromValue) &&
        assertTrue(From.toFrom("t@p") == InvalidFromValue) &&
        assertTrue(From.toFrom("") == InvalidFromValue) &&
        assertTrue(From.toFrom("test@email") == InvalidFromValue) &&
        assertTrue(From.toFrom("test.com") == InvalidFromValue) &&
        assertTrue(From.toFrom("@email.com") == InvalidFromValue) &&
        assertTrue(From.toFrom("@com") == InvalidFromValue)
      },
    )
}
