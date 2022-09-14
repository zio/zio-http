package zio.http

import zio.test.ZIOSpecDefault
import zio.test._

object QueryParamsSpec extends ZIOSpecDefault {

  def spec =
    suite("QueryParams")(
      suite("decode")(
        test("successfully decodes queryStringFragment") {
          val urls = Gen.fromIterable(
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

          checkAll(urls) { case (queryStringFragment, expected) =>
            val result = QueryParams.decode(queryStringFragment)
            assertTrue(result.map == expected)
          }
        },
      ),
    )

}
