/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http

import zio.test.Assertion.equalTo
import zio.test._
import zio.{NonEmptyChunk, ZIO}

object QueryParamsSpec extends ZIOHttpSpec {

  def spec =
    suite("QueryParams")(
      suite("-")(
        test("removes query param") {
          val gens = Gen.fromIterable(
            Seq(
              (
                QueryParams(
                  Map("a" -> NonEmptyChunk("foo", "bar"), "b" -> NonEmptyChunk("fii"), "c" -> NonEmptyChunk("baz")),
                ),
                "a",
                QueryParams(Map("b" -> NonEmptyChunk("fii"), "c" -> NonEmptyChunk("baz"))),
              ),
              (
                QueryParams(Map("a" -> NonEmptyChunk("foo", "bar"), "b" -> NonEmptyChunk("fii"))),
                "c",
                QueryParams(Map("a" -> NonEmptyChunk("foo", "bar"), "b" -> NonEmptyChunk("fii"))),
              ),
            ),
          )

          checkAll(gens) { case (initialQueryParams, keyToRemove, expectedResult) =>
            val actualResult = initialQueryParams.remove(keyToRemove)
            assert(actualResult)(equalTo(expectedResult))
          }
        },
        test("removes query params") {
          val gens = Gen.fromIterable(
            Seq(
              (
                QueryParams(
                  Map(
                    "a" -> NonEmptyChunk("foo", "bar"),
                    "b" -> NonEmptyChunk("fii"),
                    "c" -> NonEmptyChunk("baz"),
                    "d" -> NonEmptyChunk("boo"),
                  ),
                ),
                "a",
                "c",
                Seq("d"),
                QueryParams(Map("b" -> NonEmptyChunk("fii"))),
              ),
              (
                QueryParams(Map("a" -> NonEmptyChunk("foo", "bar"), "b" -> NonEmptyChunk("fii"))),
                "b",
                "c",
                Seq("d"),
                QueryParams(Map("a" -> NonEmptyChunk("foo", "bar"))),
              ),
            ),
          )

          checkAll(gens) { case (initialQueryParams, key1, key2, otherKeysToRemove, expectedResult) =>
            val actualResult = initialQueryParams.removeAll(key1 :: key2 :: (otherKeysToRemove.toList))
            assert(actualResult)(equalTo(expectedResult))
          }
        },
      ),
      suite("++")(
        test("success") {
          val gens = Gen.fromIterable(
            NonEmptyChunk(
              (
                QueryParams(Map("a" -> NonEmptyChunk("foo"), "b" -> NonEmptyChunk("bar"))),
                QueryParams(Map("c" -> NonEmptyChunk("faa"), "b" -> NonEmptyChunk("baz"))),
                QueryParams(
                  Map("a" -> NonEmptyChunk("foo"), "b" -> NonEmptyChunk("bar", "baz"), "c" -> NonEmptyChunk("faa")),
                ),
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
            NonEmptyChunk(
              (
                QueryParams(Map("a" -> NonEmptyChunk("foo"), "b" -> NonEmptyChunk("bar"))),
                "a",
                "faa",
                QueryParams(Map("a" -> NonEmptyChunk("foo", "faa"), "b" -> NonEmptyChunk("bar"))),
              ),
              (
                QueryParams(Map("a" -> NonEmptyChunk("foo"), "b" -> NonEmptyChunk("bar, baz"))),
                "c",
                "fee",
                QueryParams(
                  Map("a" -> NonEmptyChunk("foo"), "b" -> NonEmptyChunk("bar, baz"), "c" -> NonEmptyChunk("fee")),
                ),
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
            NonEmptyChunk(
              (
                QueryParams(Map("a" -> NonEmptyChunk("foo"), "b" -> NonEmptyChunk("bar"))),
                "a",
                NonEmptyChunk("faa", "fee"),
                QueryParams(Map("a" -> NonEmptyChunk("foo", "faa", "fee"), "b" -> NonEmptyChunk("bar"))),
              ),
              (
                QueryParams(Map("a" -> NonEmptyChunk("foo"), "b" -> NonEmptyChunk("bar, baz"))),
                "c",
                NonEmptyChunk("fee", "faa"),
                QueryParams(
                  Map("a" -> NonEmptyChunk("foo"), "b" -> NonEmptyChunk("bar, baz"), "c" -> NonEmptyChunk("fee", "faa")),
                ),
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
                  ("ord", NonEmptyChunk("ASC")),
                  ("txt", NonEmptyChunk("scala is awesome!")),
                  ("u", NonEmptyChunk("1")),
                  ("u", NonEmptyChunk("2")),
                ),
                QueryParams(
                  Map(
                    "ord" -> NonEmptyChunk("ASC"),
                    "txt" -> NonEmptyChunk("scala is awesome!"),
                    "u"   -> NonEmptyChunk("1", "2"),
                  ),
                ),
              ),
              (
                Seq(
                  ("ord", NonEmptyChunk("ASC")),
                  ("txt", NonEmptyChunk("scala, is awesome!")),
                  ("u", NonEmptyChunk("1")),
                  ("u", NonEmptyChunk("2")),
                ),
                QueryParams(
                  Map(
                    "ord" -> NonEmptyChunk("ASC"),
                    "txt" -> NonEmptyChunk("scala, is awesome!"),
                    "u"   -> NonEmptyChunk("1", "2"),
                  ),
                ),
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
              ("?foo", QueryParams(Map("foo" -> NonEmptyChunk("")))),
              (
                "?ord=ASC&txt=scala%20is%20awesome%21&u=1&u=2",
                QueryParams(
                  Map(
                    "ord" -> NonEmptyChunk("ASC"),
                    "txt" -> NonEmptyChunk("scala is awesome!"),
                    "u"   -> NonEmptyChunk("1", "2"),
                  ),
                ),
              ),
              (
                "?ord=ASC&txt=scala%20is%20awesome%21&u=1%2C2",
                QueryParams(
                  Map(
                    "ord" -> NonEmptyChunk("ASC"),
                    "txt" -> NonEmptyChunk("scala is awesome!"),
                    "u"   -> NonEmptyChunk("1,2"),
                  ),
                ),
              ),
              ("?a=%2Cb", QueryParams(Map("a" -> NonEmptyChunk(",b")))),
              ("", QueryParams(Map.empty[String, zio.NonEmptyChunk[String]])),
              ("?=a", QueryParams(Map("a" -> NonEmptyChunk("")))),
              ("?a=", QueryParams(Map("a" -> NonEmptyChunk("")))),
              ("?a=%2Cb%2Cd", QueryParams("a" -> NonEmptyChunk(",b,d"))),
              ("?a=%2C&a=b%2Cc", QueryParams("a" -> NonEmptyChunk(",", "b,c"))),
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
              (QueryParams(Map("a" -> NonEmptyChunk(""))), "?a="),
              (QueryParams(Map("a" -> NonEmptyChunk("foo"))), "?a=foo"),
              (QueryParams(Map("a" -> NonEmptyChunk("foo", "fee"))), "?a=foo&a=fee"),
              (
                QueryParams(
                  Map("a" -> NonEmptyChunk("scala is awesome!", "fee"), "b" -> NonEmptyChunk("ZIO is awesome!")),
                ),
                "?a=scala%20is%20awesome%21&a=fee&b=ZIO%20is%20awesome%21",
              ),
              (QueryParams(Map("" -> NonEmptyChunk(""))), ""),
              (QueryParams(Map("" -> NonEmptyChunk("a"))), ""),
              (QueryParams(Map("a" -> NonEmptyChunk(""))), "?a="),
              (QueryParams(Map("a" -> NonEmptyChunk("", "b"))), "?a=&a=b"),
              (QueryParams(Map("a" -> NonEmptyChunk("c,d"))), "?a=c%2Cd"),
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
                  .chunkOf1(Gen.string1(Gen.alphaNumericChar)),
              )
              .map(queryParamsMap => QueryParams(queryParamsMap))

          val testValueEmptyList = ZIO.succeed {
            val queryParamWithEmptyList = QueryParams(
              "0" -> NonEmptyChunk(""),
            )
            val result                  = QueryParams.decode(queryParamWithEmptyList.encode)
            assert(result)(equalTo(QueryParams("0" -> NonEmptyChunk(""))))
          }

          val testKeyEmpty =
            ZIO.succeed {
              val queryParams = QueryParams(
                "" -> NonEmptyChunk("aa"),
              )
              val result      = QueryParams.decode(queryParams.encode)
              assert(result)(equalTo(QueryParams(Map.empty[String, zio.NonEmptyChunk[String]])))
            }

          val testValueEmptyStringInList =
            ZIO.succeed {
              val queryParams = QueryParams(
                "32" -> NonEmptyChunk("8", ""),
              )
              val result      = QueryParams.decode(queryParams.encode)
              val expected    = QueryParams("32" -> NonEmptyChunk("8"), "32" -> NonEmptyChunk(""))
              assert(result)(equalTo(expected))
            }

          def deduplicateAndSortQueryParamValues(queryParams: QueryParams): QueryParams =
            QueryParams(queryParams.map.map { case (k, v) => (k, NonEmptyChunk.fromChunk(v.sorted).get) })

          def sortQueryParamValues(queryParams: QueryParams): QueryParams =
            QueryParams(queryParams.map.map { case (k, v) => (k, NonEmptyChunk.fromChunk(v.sorted).get) })

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
