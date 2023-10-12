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

package zio.http.codec

import java.util.UUID

import zio._
import zio.prelude.Id
import zio.test._

import zio.http._

object HttpCodecSpec extends ZIOHttpSpec {
  val googleUrl     = URL.decode("http://google.com").toOption.get
  val usersUrl      = URL.decode("http://mywebservice.com/users").toOption.get
  val usersIdUrl    = URL.decode("http://mywebservice.com/users/42").toOption.get
  val postURL       = URL.decode("http://mywebservice.com/users/42/post").toOption.get
  val postidURL     = URL.decode("http://mywebservice.com/users/42/post/42").toOption.get
  val postidfontURL = URL.decode("http://mywebservice.com/users/42/post/42/fontstyle").toOption.get
  val postsURL      = URL.decode("http://mywebservice.com/posts").toOption.get

  val headerExample =
    Headers(Header.ContentType(MediaType.application.json)) ++ Headers("X-Trace-ID", "1234")

  val emptyJson = Body.fromString("{}")

  private val strParam          = "name"
  private val codecStr          = QueryCodec.query(strParam)
  private val boolParam         = "isAge"
  private val codecBool         = QueryCodec.queryBool(boolParam)
  private val intParam          = "age"
  private val codecInt          = QueryCodec.queryInt(intParam)
  private val longParam         = "count"
  private val codecLong         = QueryCodec.queryAs[Long](longParam)
  private val optBoolParam      = "maybe"
  private val codecOptBool      = QueryCodec.queryOpt[Boolean](optBoolParam)
  private val seqIntParam       = "integers"
  private val codecSeqInt       = QueryCodec.queryAll[Int](seqIntParam)
  private val oneOrMoreStrParam = "names"
  private val codecOneOrMoreStr = QueryCodec.queryOneOrMore[String](oneOrMoreStrParam)

  def makeRequest(name: String, value: Any)              =
    Request.get(googleUrl.queryParams(QueryParams(name -> value.toString)))
  def makeChunkRequest(name: String, values: Chunk[Any]) =
    Request.get(googleUrl.queryParams(QueryParams(name -> values.map(_.toString))))

  def spec = suite("HttpCodecSpec")(
    suite("fallback") {
      test("query fallback") {
        val codec1 = QueryCodec.query("skip")
        val codec2 = QueryCodec.query("limit")

        val fallback = codec1 | codec2

        val skipRequest  = Request.get(url = usersUrl.copy(queryParams = QueryParams("skip" -> "10")))
        val limitRequest = Request.get(url = usersUrl.copy(queryParams = QueryParams("limit" -> "20")))

        for {
          result1 <- fallback.decodeRequest(skipRequest)
          result2 <- fallback.decodeRequest(limitRequest)
        } yield assertTrue(result1 == "10") && assertTrue(result2 == "20")
      } +
        test("header fallback") {
          val codec1 = HeaderCodec.headerCodec("Authentication", TextCodec.string)
          val codec2 = HeaderCodec.headerCodec("X-Token-ID", TextCodec.string)

          val fallback = codec1 | codec2

          val authRequest  = Request.get(url = usersUrl).copy(headers = Headers("Authentication" -> "1234"))
          val tokenRequest = Request.get(url = usersUrl).copy(headers = Headers("X-Token-ID" -> "5678"))

          for {
            result1 <- fallback.decodeRequest(authRequest)
            result2 <- fallback.decodeRequest(tokenRequest)
          } yield assertTrue(result1 == "1234") && assertTrue(result2 == "5678")
        } +
        test("composite fallback") {

          val codec1 = QueryCodec.query("skip") ++ HeaderCodec.headerCodec(
            "Authentication",
            TextCodec.string,
          )
          val codec2 = QueryCodec.query("limit") ++ HeaderCodec.headerCodec(
            "X-Token-ID",
            TextCodec.string,
          )

          val fallback = codec1 | codec2

          val usersRequest = Request
            .get(url = URL.root.copy(queryParams = QueryParams("skip" -> "10")))
            .copy(headers = Headers("Authentication" -> "1234"))
          val postsRequest = Request
            .get(url = URL.root.copy(queryParams = QueryParams("limit" -> "20")))
            .copy(headers = Headers("X-Token-ID" -> "567"))

          for {
            result1 <- fallback.decodeRequest(usersRequest)
            result2 <- fallback.decodeRequest(postsRequest)
          } yield assertTrue(result1 == (("10", "1234"))) && assertTrue(result2 == (("20", "567")))
        } +
        test("no fallback for defects") {
          val e = new RuntimeException("boom")

          val codec1 = HttpCodec.empty.transform[Unit](_ => throw e)(_ => ()).const("route1")
          val codec2 = HttpCodec.empty.const("route2")

          val fallback = codec1 | codec2

          for {
            result <- fallback.decodeRequest(Request.get(url = URL.root)).exit
          } yield assertTrue(result.causeOption.get.defects.forall(_ == e))
        }
    } +
      suite("optional") {
        test("fallback for missing values") {
          val codec = QueryCodec.query("name").transformOrFail[String](_ => Left("fail"))(Right(_))

          val request = Request.get(url = URL.root)

          val optional = codec.optional

          for {
            result <- optional.decodeRequest(request)
          } yield assertTrue(result.isEmpty)
        } +
          test("no fallback for decoding errors") {
            val codec = QueryCodec.query("key").transformOrFail[String](_ => Left("fail"))(Right(_))

            val request = Request.get(url = URL.root.copy(queryParams = QueryParams("key" -> "value")))

            val optional = codec.optional

            for {
              result <- optional.decodeRequest(request).exit
            } yield assertTrue(result.isFailure)
          }
      } +
      suite("HeaderCodec") {
        test("dummy test") {
          assertTrue(true)
        }
      } +
      suite("BodyCodec") {
        test("dummy test") {
          assertTrue(true)
        }
      } +
      suite("QueryCodec")(
        test("paramStr decoding and encoding") {
          check(Gen.alphaNumericString) { value =>
            assertZIO(codecStr.decodeRequest(makeRequest(strParam, value)))(Assertion.equalTo(value)) &&
            assert(codecStr.encodeRequest(value).url.queryParams.get(strParam))(
              Assertion.isSome(Assertion.equalTo(value)),
            )
          }
        },
        test("paramBool decoding true") {
          Chunk("true", "TRUE", "yes", "YES", "on", "ON", "1") map { value =>
            assertZIO(codecBool.decodeRequest(makeRequest(boolParam, value)))(Assertion.isTrue)
          } reduce (_ && _)
        },
        test("paramBool decoding false") {
          Chunk("false", "FALSE", "no", "NO", "off", "OFF", "0") map { value =>
            assertZIO(codecBool.decodeRequest(makeRequest(boolParam, value)))(Assertion.isFalse)
          } reduce (_ && _)
        },
        test("paramBool encoding") {
          val requestTrue  = codecBool.encodeRequest(true)
          val requestFalse = codecBool.encodeRequest(false)
          assert(requestTrue.url.queryParams.get(boolParam).get)(Assertion.equalTo("true")) &&
          assert(requestFalse.url.queryParams.get(boolParam).get)(Assertion.equalTo("false"))
        },
        test("paramInt decoding and encoding") {
          check(Gen.int) { value =>
            assertZIO(codecInt.decodeRequest(makeRequest(intParam, value)))(Assertion.equalTo(value)) &&
            assert(codecInt.encodeRequest(value).url.queryParams.get(intParam))(
              Assertion.isSome(Assertion.equalTo(value.toString)),
            )
          }
        },
        test("paramLong decoding and encoding") {
          check(Gen.long) { value =>
            assertZIO(codecLong.decodeRequest(makeRequest(longParam, value)))(Assertion.equalTo(value)) &&
            assert(codecLong.encodeRequest(value).url.queryParams.get(longParam))(
              Assertion.isSome(Assertion.equalTo(value.toString)),
            )
          }
        },
        test("paramOpt decoding empty chunk") {
          assertZIO(codecOptBool.decodeRequest(makeChunkRequest(optBoolParam, Chunk.empty)))(Assertion.isNone)
        },
        test("paramOpt decoding singleton chunk") {
          assertZIO(codecOptBool.decodeRequest(makeChunkRequest(optBoolParam, Chunk("true"))))(
            Assertion.isSome(Assertion.isTrue),
          ) &&
          assertZIO(codecOptBool.decodeRequest(makeChunkRequest(optBoolParam, Chunk("false"))))(
            Assertion.isSome(Assertion.isFalse),
          )
        },
        test("paramOpt encoding empty chunk") {
          assert(codecOptBool.encodeRequest(None).url.queryParams.get(optBoolParam))(Assertion.isNone)
        },
        test("paramOpt encoding non-empty chunk") {
          assert(codecOptBool.encodeRequest(Some(true)).url.queryParams.getAll(optBoolParam).get)(
            Assertion.equalTo(Chunk("true")),
          ) &&
          assert(codecOptBool.encodeRequest(Some(false)).url.queryParams.getAll(optBoolParam).get)(
            Assertion.equalTo(Chunk("false")),
          )
        },
        test("params decoding empty chunk") {
          assertZIO(codecSeqInt.decodeRequest(makeChunkRequest(seqIntParam, Chunk.empty)))(Assertion.isEmpty)
        },
        test("params decoding non-empty chunk") {
          assertZIO(codecSeqInt.decodeRequest(makeChunkRequest(seqIntParam, Chunk("2023", "10", "7"))))(
            Assertion.equalTo(Chunk(2023, 10, 7)),
          )
        },
        test("params encoding empty chunk") {
          assert(codecSeqInt.encodeRequest(Chunk.empty).url.queryParams.get(seqIntParam))(Assertion.isNone)
        },
        test("params encoding non-empty chunk") {
          assert(codecSeqInt.encodeRequest(Chunk(1974, 5, 3)).url.queryParams.getAll(seqIntParam).get)(
            Assertion.equalTo(Chunk("1974", "5", "3")),
          )
        },
        test("paramOneOrMore decoding non-empty chunk") {
          assertZIO(codecOneOrMoreStr.decodeRequest(makeChunkRequest(oneOrMoreStrParam, Chunk("one"))))(
            Assertion.equalTo(NonEmptyChunk("one")),
          ) &&
          assertZIO(codecOneOrMoreStr.decodeRequest(makeChunkRequest(oneOrMoreStrParam, Chunk("one", "two", "three"))))(
            Assertion.equalTo(NonEmptyChunk("one", "two", "three")),
          )
        },
        test("paramOneOrMore encoding non-empty chunk") {
          assert(
            codecOneOrMoreStr
              .encodeRequest(NonEmptyChunk("for", "five", "six"))
              .url
              .queryParams
              .getAll(oneOrMoreStrParam)
              .get,
          )(
            Assertion.equalTo(Chunk("for", "five", "six")),
          )
        },
      ) +
      suite("Codec with examples") {
        test("with examples") {
          val userCodec = HttpCodec.empty.const("foo").examples("user" -> "John", "user2" -> "Jane")
          val uuid1     = UUID.randomUUID
          val uuid2     = UUID.randomUUID
          val uuidCodec = HttpCodec.empty.const(UUID.randomUUID()).examples("userId" -> uuid1, "userId2" -> uuid2)

          val userExamples = userCodec.examples
          val uuidExamples = uuidCodec.examples
          assertTrue(
            userExamples == Map("user" -> "John", "user2" -> "Jane"),
            uuidExamples == Map("userId" -> uuid1, "userId2" -> uuid2),
          )
        }
      },
  )

}
