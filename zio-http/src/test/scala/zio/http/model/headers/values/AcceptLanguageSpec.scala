package zio.http.model.headers.values

import zio.Scope
import zio.test._

object AcceptLanguageSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Accept Language header suite")(
    test("accept language header transformation must be symmetrical") {
      check(acceptLanguageStr) { header =>
        assertTrue(AcceptLanguage.fromAcceptLanguage(AcceptLanguage.toAcceptLanguage(header)) == header)
      } &&
      check(acceptLanguageWithWeightStr) { header =>
        assertTrue(AcceptLanguage.fromAcceptLanguage(AcceptLanguage.toAcceptLanguage(header)) == header)
      }
    },
    test("empty input should yield invalid header value") {
      assertTrue(AcceptLanguage.toAcceptLanguage("") == AcceptLanguage.InvalidAcceptLanguageValue)
    },
    test("presence of invalid characters should yield invalid value") {
      assertTrue(AcceptLanguage.toAcceptLanguage("!") == AcceptLanguage.InvalidAcceptLanguageValue)
    },
  )

  private def acceptLanguageStr: Gen[Any, String] =
    for {
      part1 <- Gen.stringN(2)(Gen.alphaChar)
      part2 <- Gen.stringN(2)(Gen.alphaChar)
    } yield s"$part1-$part2"

  private def acceptLanguageWithWeightStr: Gen[Any, String] =
    for {
      acceptLang <- acceptLanguageStr
      weight     <- Gen.double(0.0, 1.0)
    } yield s"$acceptLang;q=$weight"

}
