package zio.http

import zio.test.Assertion.equalTo
import zio.test.{ZIOSpecDefault, _}

object QueryParamsSpec extends ZIOSpecDefault {

  def spec =
    suite("QueryParams")(
      suite("-")(
        test("removes query param") {
          val gens = Gen.fromIterable(
            Seq(
              (
                QueryParams(Map("a" -> List("foo", "bar"), "b" -> List("fii"), "c" -> List("baz"))),
                "a",
                QueryParams(Map("b" -> List("fii"), "c" -> List("baz"))),
              ),
              (
                QueryParams(Map("a" -> List("foo", "bar"), "b" -> List("fii"))),
                "c",
                QueryParams(Map("a" -> List("foo", "bar"), "b" -> List("fii"))),
              ),
            ),
          )

          checkAll(gens) { case (initialQueryParams, keyToRemove, expectedResult) =>
            val actualResult = initialQueryParams.-(keyToRemove)
            assert(actualResult)(equalTo(expectedResult))
          }
        },
        test("removes query params") {
          val gens = Gen.fromIterable(
            Seq(
              (
                QueryParams(Map("a" -> List("foo", "bar"), "b" -> List("fii"), "c" -> List("baz"), "d" -> List("boo"))),
                "a",
                "c",
                Seq("d"),
                QueryParams(Map("b" -> List("fii"))),
              ),
              (
                QueryParams(Map("a" -> List("foo", "bar"), "b" -> List("fii"))),
                "b",
                "c",
                Seq("d"),
                QueryParams(Map("a" -> List("foo", "bar"))),
              ),
            ),
          )

          checkAll(gens) { case (initialQueryParams, key1, key2, otherKeysToRemove, expectedResult) =>
            val actualResult = initialQueryParams.-(key1, key2, otherKeysToRemove: _*)
            assert(actualResult)(equalTo(expectedResult))
          }
        },
      ),
      suite("++")(
        test("success") {
          val gens = Gen.fromIterable(
            List(
              (
                QueryParams(Map("a" -> List("foo"), "b" -> List("bar"))),
                QueryParams(Map("c" -> List("faa"), "b" -> List("baz"))),
                QueryParams(Map("a" -> List("foo"), "b" -> List("bar", "baz"), "c" -> List("faa"))),
              ),
            ),
          )

          checkAll(gens) { case (first, second, expected) =>
            val actual = first ++ second
            assert(actual)(equalTo(expected))
          }
        },
      ),
      suite("add")(
        test("success when non list input value") {
          val gens = Gen.fromIterable(
            List(
              (
                QueryParams(Map("a" -> List("foo"), "b" -> List("bar"))),
                "a",
                "faa",
                QueryParams(Map("a" -> List("foo", "faa"), "b" -> List("bar"))),
              ),
              (
                QueryParams(Map("a" -> List("foo"), "b" -> List("bar, baz"))),
                "c",
                "fee",
                QueryParams(Map("a" -> List("foo"), "b" -> List("bar, baz"), "c" -> List("fee"))),
              ),
            ),
          )

          checkAll(gens) { case (initial, key, value, expected) =>
            val actual = initial.add(key, value)
            assert(actual)(equalTo(expected))
          }
        },
        test("success when list input value") {
          val gens = Gen.fromIterable(
            List(
              (
                QueryParams(Map("a" -> List("foo"), "b" -> List("bar"))),
                "a",
                List("faa", "fee"),
                QueryParams(Map("a" -> List("foo", "faa", "fee"), "b" -> List("bar"))),
              ),
              (
                QueryParams(Map("a" -> List("foo"), "b" -> List("bar, baz"))),
                "c",
                List("fee", "faa"),
                QueryParams(Map("a" -> List("foo"), "b" -> List("bar, baz"), "c" -> List("fee", "faa"))),
              ),
            ),
          )

          checkAll(gens) { case (initial, key, value, expected) =>
            val actual = initial.add(key, value)
            assert(actual)(equalTo(expected))
          }
        },
      ),
      suite("apply")(
        test("from tuples") {
          val gens = Gen.fromIterable(
            Seq(
              (
                Seq(
                  ("ord", List("ASC")),
                  ("txt", List("scala is awesome!")),
                  ("u", List("1")),
                  ("u", List("2")),
                ),
                QueryParams(Map("ord" -> List("ASC"), "txt" -> List("scala is awesome!"), "u" -> List("1", "2"))),
              ),
              (
                Seq(
                  ("ord", List("ASC")),
                  ("txt", List("scala, is awesome!")),
                  ("u", List("1")),
                  ("u", List("2")),
                ),
                QueryParams(Map("ord" -> List("ASC"), "txt" -> List("scala, is awesome!"), "u" -> List("1", "2"))),
              ),
            ),
          )

          checkAll(gens) { case (tuples, expected) =>
            val result = QueryParams(tuples: _*)
            assert(result)(equalTo(expected))
          }

        },
      ),
      suite("decode")(
        test("successfully decodes queryStringFragment") {
          val gens = Gen.fromIterable(
            Seq(
              ("?", QueryParams.empty),
              ("?foo", QueryParams(Map("foo" -> List("")))),
              (
                "?ord=ASC&txt=scala%20is%20awesome%21&u=1&u=2",
                QueryParams(Map("ord" -> List("ASC"), "txt" -> List("scala is awesome!"), "u" -> List("1", "2"))),
              ),
              (
                "?ord=ASC&txt=scala%20is%20awesome%21&u=1%2C2",
                QueryParams(Map("ord" -> List("ASC"), "txt" -> List("scala is awesome!"), "u" -> List("1", "2"))),
              ),
            ),
          )

          checkAll(gens) { case (queryStringFragment, expected) =>
            val result = QueryParams.decode(queryStringFragment)
            assertTrue(result == expected)
          }
        },
      ),
      suite("encode")(
        test("success") {
          val gens = Gen.fromIterable(
            Seq(
              (QueryParams.empty, ""),
              (QueryParams(Map("a" -> List(""))), "?a="),
              (QueryParams(Map("a" -> List("foo"))), "?a=foo"),
              (QueryParams(Map("a" -> List("foo", "fee"))), "?a=foo%2Cfee"),
              (
                QueryParams(Map("a" -> List("scala is awesome!", "fee"), "b" -> List("ZIO is awesome!"))),
                "?a=scala%20is%20awesome%21%2Cfee&b=ZIO%20is%20awesome%21",
              ),
            ),
          )

          checkAll(gens) { case (queryParams, expected) =>
            val result = queryParams.encode
            assertTrue(result == expected)
          }
        },
      ),
      suite("encode - decode")(
        test("success") {
          val gens = Gen.fromIterable(
            Seq(
              QueryParams.empty,
              QueryParams(Map("a" -> List(""))),
              QueryParams(Map("a" -> List("foo"))),
              QueryParams(Map("a" -> List("foo", "fee"))),
              QueryParams(Map("a" -> List("scala is awesome!", "fee"), "b" -> List("ZIO is awesome!"))),
            ),
          )

          checkAll(gens) { case queryParams =>
            val result = QueryParams.decode(queryParams.encode)
            assertTrue(result == queryParams)
          }
        },
      ),
    )

}
