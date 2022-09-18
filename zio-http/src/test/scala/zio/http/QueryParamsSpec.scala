package zio.http

import zio.test.Assertion.equalTo
import zio.test._
import zio.{Chunk, ZIO}

object QueryParamsSpec extends ZIOSpecDefault {

  def spec =
    suite("QueryParams")(
      suite("-")(
        test("removes query param") {
          val gens = Gen.fromIterable(
            Seq(
              (
                QueryParams(Map("a" -> Chunk("foo", "bar"), "b" -> Chunk("fii"), "c" -> Chunk("baz"))),
                "a",
                QueryParams(Map("b" -> Chunk("fii"), "c" -> Chunk("baz"))),
              ),
              (
                QueryParams(Map("a" -> Chunk("foo", "bar"), "b" -> Chunk("fii"))),
                "c",
                QueryParams(Map("a" -> Chunk("foo", "bar"), "b" -> Chunk("fii"))),
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
                QueryParams(
                  Map("a" -> Chunk("foo", "bar"), "b" -> Chunk("fii"), "c" -> Chunk("baz"), "d" -> Chunk("boo")),
                ),
                "a",
                "c",
                Seq("d"),
                QueryParams(Map("b" -> Chunk("fii"))),
              ),
              (
                QueryParams(Map("a" -> Chunk("foo", "bar"), "b" -> Chunk("fii"))),
                "b",
                "c",
                Seq("d"),
                QueryParams(Map("a" -> Chunk("foo", "bar"))),
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
            Chunk(
              (
                QueryParams(Map("a" -> Chunk("foo"), "b" -> Chunk("bar"))),
                QueryParams(Map("c" -> Chunk("faa"), "b" -> Chunk("baz"))),
                QueryParams(Map("a" -> Chunk("foo"), "b" -> Chunk("bar", "baz"), "c" -> Chunk("faa"))),
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
            Chunk(
              (
                QueryParams(Map("a" -> Chunk("foo"), "b" -> Chunk("bar"))),
                "a",
                "faa",
                QueryParams(Map("a" -> Chunk("foo", "faa"), "b" -> Chunk("bar"))),
              ),
              (
                QueryParams(Map("a" -> Chunk("foo"), "b" -> Chunk("bar, baz"))),
                "c",
                "fee",
                QueryParams(Map("a" -> Chunk("foo"), "b" -> Chunk("bar, baz"), "c" -> Chunk("fee"))),
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
            Chunk(
              (
                QueryParams(Map("a" -> Chunk("foo"), "b" -> Chunk("bar"))),
                "a",
                Chunk("faa", "fee"),
                QueryParams(Map("a" -> Chunk("foo", "faa", "fee"), "b" -> Chunk("bar"))),
              ),
              (
                QueryParams(Map("a" -> Chunk("foo"), "b" -> Chunk("bar, baz"))),
                "c",
                Chunk("fee", "faa"),
                QueryParams(Map("a" -> Chunk("foo"), "b" -> Chunk("bar, baz"), "c" -> Chunk("fee", "faa"))),
              ),
            ),
          )

          checkAll(gens) { case (initial, key, value, expected) =>
            val actual = initial.addAll(key, value)
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
                  ("ord", Chunk("ASC")),
                  ("txt", Chunk("scala is awesome!")),
                  ("u", Chunk("1")),
                  ("u", Chunk("2")),
                ),
                QueryParams(Map("ord" -> Chunk("ASC"), "txt" -> Chunk("scala is awesome!"), "u" -> Chunk("1", "2"))),
              ),
              (
                Seq(
                  ("ord", Chunk("ASC")),
                  ("txt", Chunk("scala, is awesome!")),
                  ("u", Chunk("1")),
                  ("u", Chunk("2")),
                ),
                QueryParams(Map("ord" -> Chunk("ASC"), "txt" -> Chunk("scala, is awesome!"), "u" -> Chunk("1", "2"))),
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
              ("?foo", QueryParams(Map("foo" -> Chunk("")))),
              (
                "?ord=ASC&txt=scala%20is%20awesome%21&u=1&u=2",
                QueryParams(Map("ord" -> Chunk("ASC"), "txt" -> Chunk("scala is awesome!"), "u" -> Chunk("1", "2"))),
              ),
              (
                "?ord=ASC&txt=scala%20is%20awesome%21&u=1%2C2",
                QueryParams(Map("ord" -> Chunk("ASC"), "txt" -> Chunk("scala is awesome!"), "u" -> Chunk("1,2"))),
              ),
              ("?a=%2Cb", QueryParams(Map("a" -> Chunk(",b")))),
              ("", QueryParams(Map.empty[String, Chunk[String]])),
              ("?=a", QueryParams(Map("a" -> Chunk("")))),
              ("?a=", QueryParams(Map("a" -> Chunk("")))),
              ("?a=%2Cb%2Cd", QueryParams("a" -> Chunk(",b,d"))),
              ("?a=%2C&a=b%2Cc", QueryParams("a" -> Chunk(",", "b,c"))),
              ("?commas=%2C%2C%2C%2C%2C", QueryParams(("commas", ",,,,,"))),
              ("?commas=%2Cb%2Cc%2Cd%2Ce%2Cf", QueryParams(("commas", ",b,c,d,e,f"))),
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
              (QueryParams(Map("a" -> Chunk.empty)), "?a="),
              (QueryParams(Map("a" -> Chunk(""))), "?a="),
              (QueryParams(Map("a" -> Chunk("foo"))), "?a=foo"),
              (QueryParams(Map("a" -> Chunk("foo", "fee"))), "?a=foo&a=fee"),
              (
                QueryParams(Map("a" -> Chunk("scala is awesome!", "fee"), "b" -> Chunk("ZIO is awesome!"))),
                "?a=scala%20is%20awesome%21&a=fee&b=ZIO%20is%20awesome%21",
              ),
              (QueryParams(Map("" -> Chunk(""))), ""),
              (QueryParams(Map("" -> Chunk("a"))), ""),
              (QueryParams(Map("a" -> Chunk(""))), "?a="),
              (QueryParams(Map("a" -> Chunk("", "b"))), "?a=&a=b"),
              (QueryParams(Map("a" -> Chunk("c,d"))), "?a=c%2Cd"),
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
          val genQueryParamsWithoutCornerCases =
            Gen
              .mapOf(
                Gen.string1(Gen.alphaNumericChar),
                Gen
                  .chunkOf1(Gen.string1(Gen.alphaNumericChar))
                  .map(_.toChunk),
              )
              .map(queryParamsMap => QueryParams(queryParamsMap))

          val testValueEmptyList = ZIO.succeed {
            val queryParamWithEmptyList = QueryParams(
              "0" -> Chunk(),
              "1" -> Chunk.empty,
            )
            val result                  = QueryParams.decode(queryParamWithEmptyList.encode)
            assert(result)(equalTo(QueryParams("0" -> Chunk(""), "1" -> Chunk(""))))
          }

          val testKeyEmpty =
            ZIO.succeed {
              val queryParams = QueryParams(
                "" -> Chunk("aa"),
                "" -> Chunk(),
                "" -> Chunk.empty,
              )
              val result      = QueryParams.decode(queryParams.encode)
              assert(result)(equalTo(QueryParams(Map.empty[String, Chunk[String]])))
            }

          val testValueEmptyStringInList =
            ZIO.succeed {
              val queryParams = QueryParams(
                "32" -> Chunk("8", ""),
              )
              val result      = QueryParams.decode(queryParams.encode)
              val expected    = QueryParams("32" -> Chunk("8"), "32" -> Chunk(""))
              assert(result)(equalTo(expected))
            }

          def deduplicateAndSortQueryParamValues(queryParams: QueryParams): QueryParams =
            QueryParams(queryParams.map.map { case (k, v) => (k, v.sorted) })

          def sortQueryParamValues(queryParams: QueryParams): QueryParams =
            QueryParams(queryParams.map.map { case (k, v) => (k, v.sorted) })

          for {
            nonCornerCasesTests <- check(genQueryParamsWithoutCornerCases) { case givenQueryParams =>
              val result = QueryParams.decode(givenQueryParams.encode)

              assert(sortQueryParamValues(result))(
                equalTo(deduplicateAndSortQueryParamValues(givenQueryParams)),
              )
            }
            t1                  <- testValueEmptyList
            t2                  <- testKeyEmpty
            t3                  <- testValueEmptyStringInList
            cornerCasesTests = t1 && t2 && t3
          } yield nonCornerCasesTests && cornerCasesTests

        },
      ),
    )

}
