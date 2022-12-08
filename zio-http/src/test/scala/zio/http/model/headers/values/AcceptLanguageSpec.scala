package zio.http.model.headers.values

import zio.test._
import zio.{Chunk, Scope}

object AcceptLanguageSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Accept Language header suite")(
    test("accept language header transformation must be symmetrical") {
      check(acceptLanguageStr) { header =>
        assertTrue(
          AcceptLanguage.fromAcceptLanguage(AcceptLanguage.toAcceptLanguage(Chunk((header, None)))) == Chunk(
            (header, None),
          ),
        )
      } &&
      check(acceptLanguageWithWeightStr) { header =>
        assertTrue(
          AcceptLanguage.fromAcceptLanguage(AcceptLanguage.toAcceptLanguage(Chunk((header, None)))) == Chunk(
            (header, None),
          ),
        )
      }
    },
    test("empty input should yield invalid header value") {
      assertTrue(
        AcceptLanguage.toAcceptLanguage(Chunk(("", None))) == AcceptLanguage.AcceptedLanguages(
          Chunk(AcceptLanguage.InvalidAcceptLanguageValue),
        ),
      )
    },
    test("parse multiple accept language values") {
      assertTrue(
        AcceptLanguage.toAcceptLanguage(Chunk(("en", None), ("de", None))) == AcceptLanguage.AcceptedLanguages(
          Chunk(AcceptLanguage.AcceptedLanguage("en", None), AcceptLanguage.AcceptedLanguage("de", None)),
        ),
      )
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
