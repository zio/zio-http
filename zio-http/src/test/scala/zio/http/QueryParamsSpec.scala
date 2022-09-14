package zio.http

import zio.test.Assertion.equalTo
import zio.test.ZIOSpecDefault
import zio.test._

object QueryParamsSpec extends ZIOSpecDefault {

  def spec =
    suite("QueryParams")(
      suite("apply")(
        test("success") {
          val gens = Gen.fromIterable(
            Seq(
              (
                Seq(
                  ("ord", List("ASC")),
                  ("txt", List("scala is awesome!")),
                  ("u", List("1")),
                  ("u", List("2")),
                ),
                Map("ord" -> List("ASC"), "txt" -> List("scala is awesome!"), "u" -> List("1", "2")),
              ),
              (
                Seq(
                  ("ord", List("ASC")),
                  ("txt", List("scala, is awesome!")),
                  ("u", List("1")),
                  ("u", List("2")),
                ),
                Map("ord" -> List("ASC"), "txt" -> List("scala, is awesome!"), "u" -> List("1", "2")),
              ),
            ),
          )

          checkAll(gens) { case (tuples, expected) =>
            val result = QueryParams(tuples: _*)
            assert(result.map)(equalTo(expected))
          }

        },
      ),
      suite("decode")(
        test("successfully decodes queryStringFragment") {
          val gens = Gen.fromIterable(
            Seq(
              ("", Map.empty[String, List[String]]),
              ("foo", Map("foo" -> List(""))),
              (
                "ord=ASC&txt=scala%20is%20awesome%21&u=1&u=2",
                Map("ord" -> List("ASC"), "txt" -> List("scala is awesome!"), "u" -> List("1", "2")),
              ),
              (
                "ord=ASC&txt=scala%20is%20awesome%21&u=1%2C2",
                Map("ord" -> List("ASC"), "txt" -> List("scala is awesome!"), "u" -> List("1", "2")),
              ),
            ),
          )

          checkAll(gens) { case (queryStringFragment, expected) =>
            val result = QueryParams.decode(queryStringFragment)
            assertTrue(result.map == expected)
          }
        },
      ),
    )

}
