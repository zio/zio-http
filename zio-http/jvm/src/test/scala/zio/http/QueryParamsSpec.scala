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

import scala.jdk.CollectionConverters._

import zio.test.Assertion.{anything, equalTo, fails, hasSize}
import zio.test._
import zio.{Chunk, ZIO}

object QueryParamsSpec extends ZIOHttpSpec {

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
            val actualResult = initialQueryParams.removeQueryParam(keyToRemove)
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
            val actualResult = initialQueryParams.removeQueryParams(key1 :: key2 :: (otherKeysToRemove.toList))
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
            val actual = initial.addQueryParam(key, value)
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
            val actual = initial.addQueryParams(key, value)
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
      suite("get - getAll")(
        test("success") {
          val name        = "name"
          val default     = "default"
          val unknown     = "non-existent"
          val queryParams = QueryParams(name -> "a", name -> "b")
          assertTrue(
            queryParams.queryParam(name).get == "a",
            queryParams.queryParam(unknown).isEmpty,
            queryParams.queryParamOrElse(name, default) == "a",
            queryParams.queryParamOrElse(unknown, default) == default,
            queryParams.queryParams(name).length == 2,
            queryParams.queryParams(unknown).isEmpty,
            queryParams.queryParamsOrElse(name, Chunk(default)).length == 2,
            queryParams.queryParamsOrElse(unknown, Chunk(default)).length == 1,
          )
        },
      ),
      suite("getAs - getAllAs")(
        test("success") {
          val typed        = "typed"
          val default      = 3
          val invalidTyped = "invalidTyped"
          val unknown      = "non-existent"
          val queryParams  = QueryParams(typed -> "1", typed -> "2", invalidTyped -> "str")
          assertTrue(
            queryParams.queryParamTo[Int](typed) == Right(1),
            queryParams.queryParamTo[Int](invalidTyped).isLeft,
            queryParams.queryParamTo[Int](unknown).isLeft,
            queryParams.queryParamToOrElse[Int](typed, default) == 1,
            queryParams.queryParamToOrElse[Int](invalidTyped, default) == default,
            queryParams.queryParamToOrElse[Int](unknown, default) == default,
            queryParams.queryParamsTo[Int](typed).map(_.length) == Right(2),
            queryParams.queryParamsTo[Int](invalidTyped).isLeft,
            queryParams.queryParamsTo[Int](unknown).isLeft,
            queryParams.queryParamsToOrElse[Int](typed, Chunk(default)).length == 2,
            queryParams.queryParamsToOrElse[Int](invalidTyped, Chunk(default)).length == 1,
            queryParams.queryParamsToOrElse[Int](unknown, Chunk(default)).length == 1,
          )
          assertZIO(queryParams.queryParamToZIO[Int](typed))(equalTo(1)) &&
          assertZIO(queryParams.queryParamToZIO[Int](invalidTyped).exit)(fails(anything)) &&
          assertZIO(queryParams.queryParamToZIO[Int](unknown).exit)(fails(anything)) &&
          assertZIO(queryParams.queryParamsToZIO[Int](typed))(hasSize(equalTo(2))) &&
          assertZIO(queryParams.queryParamsToZIO[Int](invalidTyped).exit)(fails(anything)) &&
          assertZIO(queryParams.queryParamsToZIO[Int](unknown).exit)(fails(anything))
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
            QueryParams(queryParams.seq.map { entry =>
              (entry.getKey, Chunk.fromIterable(entry.getValue.asScala).sorted)
            }: _*)

          for {
            nonCornerCasesTests <- check(genQueryParamsWithoutCornerCases) { case givenQueryParams =>
              val result = QueryParams.decode(givenQueryParams.encode)

              assert(deduplicateAndSortQueryParamValues(result))(
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
      suite("maintains ordering")(
        test("upon construction") {
          val numbers     = Range(0, 100).map(_.toString).toList
          val queryParams = QueryParams(numbers.map(x => x -> Chunk("0")): _*)
          assertTrue(queryParams.seq.map(_.getKey).toList == numbers)
        },
        test("after ++") {
          val numbers0     = Range(0, 50).map(_.toString).toList
          val numbers50    = Range(50, 100).map(_.toString).toList
          val numbers100   = Range(0, 100).map(_.toString).toList
          val queryParams1 = QueryParams(numbers0.map(x => x -> Chunk("0")): _*) ++
            QueryParams(numbers50.map(x => x -> Chunk("0")): _*)
          assertTrue(queryParams1.seq.map(_.getKey).toList == numbers100)
        },
      ),
      suite("produces a map")(
        test("success") {
          val queryParams = QueryParams("a" -> Chunk("1", "2"), "b" -> Chunk("3"))
          assertTrue(queryParams.map == Map("a" -> Chunk("1", "2"), "b" -> Chunk("3")))
        },
      ),
    )

}
