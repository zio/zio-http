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

  val isAge                           = "isAge"
  val codecBool                       = HttpCodec.query[Boolean](isAge)
  def makeRequest(paramValue: String) = Request.get(googleUrl.setQueryParams(QueryParams(isAge -> paramValue)))

  def spec = suiteAll("HttpCodecSpec") {
    suiteAll("fallback") {
      test("query fallback") {
        val codec1 = HttpCodec.query[String]("skip")
        val codec2 = HttpCodec.query[String]("limit")

        val fallback = codec1 | codec2

        val skipRequest  = Request.get(url = usersUrl.copy(queryParams = QueryParams("skip" -> "10")))
        val limitRequest = Request.get(url = usersUrl.copy(queryParams = QueryParams("limit" -> "20")))

        for {
          result1 <- fallback.decodeRequest(skipRequest)
          result2 <- fallback.decodeRequest(limitRequest)
        } yield assertTrue(result1 == "10") && assertTrue(result2 == "20")
      }
      test("header fallback") {
        val codec1 = HeaderCodec.headerAs[String]("Authentication")
        val codec2 = HeaderCodec.headerAs[String]("X-Token-ID")

        val fallback = codec1 | codec2

        val authRequest  = Request.get(url = usersUrl).copy(headers = Headers("Authentication" -> "1234"))
        val tokenRequest = Request.get(url = usersUrl).copy(headers = Headers("X-Token-ID" -> "5678"))

        for {
          result1 <- fallback.decodeRequest(authRequest)
          result2 <- fallback.decodeRequest(tokenRequest)
        } yield assertTrue(result1 == "1234") && assertTrue(result2 == "5678")
      }
      test("composite fallback") {

        val codec1 = HttpCodec.query[String]("skip") ++ HeaderCodec.headerCodec(
          "Authentication",
          TextCodec.string,
        )
        val codec2 = HttpCodec.query[String]("limit") ++ HeaderCodec.headerCodec(
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
      }
      test("no fallback for defects") {
        val e = new RuntimeException("boom")

        val codec1 = HttpCodec.empty.transform[Unit](_ => throw e)(_ => ()).const("route1")
        val codec2 = HttpCodec.empty.const("route2")

        val fallback = codec1 | codec2

        for {
          result <- fallback.decodeRequest(Request.get(url = URL.root)).exit
        } yield assertTrue(result.causeOption.get.defects.forall(_ == e))
      }
    }
    suiteAll("optional") {
      test("fallback for missing values") {
        val codec = HttpCodec.query[String]("name").transformOrFail[String](_ => Left("fail"))(Right(_))

        val request = Request.get(url = URL.root)

        val optional = codec.optional

        for {
          result <- optional.decodeRequest(request)
        } yield assertTrue(result.isEmpty)
      }
      test("fallback for missing header value") {
        val codec   = HeaderCodec.ifNoneMatch.optional
        val request = Request.get(url = URL.root)
        for {
          result <- codec.decodeRequest(request)
        } yield assertTrue(result.isEmpty)
      }
      test("no fallback for decoding errors") {
        val codec = HttpCodec.query[String]("key").transformOrFail[String](_ => Left("fail"))(Right(_))

        val request = Request.get(url = URL.root.copy(queryParams = QueryParams("key" -> "value")))

        val optional = codec.optional

        for {
          result <- optional.decodeRequest(request).exit
        } yield assertTrue(result.isFailure)
      }
    }
    suite("HeaderCodec") {
      test("dummy test") {
        assertTrue(true)
      }
    }
    suite("BodyCodec") {
      test("dummy test") {
        assertTrue(true)
      }
    }
    suiteAll("QueryCodec") {
      test("paramBool decoding with case-insensitive") {
        assertZIO(codecBool.decodeRequest(makeRequest("true")))(Assertion.isTrue) &&
        assertZIO(codecBool.decodeRequest(makeRequest("TRUE")))(Assertion.isTrue) &&
        assertZIO(codecBool.decodeRequest(makeRequest("yes")))(Assertion.isTrue) &&
        assertZIO(codecBool.decodeRequest(makeRequest("YES")))(Assertion.isTrue) &&
        assertZIO(codecBool.decodeRequest(makeRequest("on")))(Assertion.isTrue) &&
        assertZIO(codecBool.decodeRequest(makeRequest("ON")))(Assertion.isTrue)
      }
      test("paramBool decoding with different values") {
        assertZIO(codecBool.decodeRequest(makeRequest("true")))(Assertion.isTrue) &&
        assertZIO(codecBool.decodeRequest(makeRequest("1")))(Assertion.isTrue) &&
        assertZIO(codecBool.decodeRequest(makeRequest("yes")))(Assertion.isTrue) &&
        assertZIO(codecBool.decodeRequest(makeRequest("on")))(Assertion.isTrue)
      }
      test("paramBool encoding") {
        val requestTrue  = codecBool.encodeRequest(true)
        val requestFalse = codecBool.encodeRequest(false)
        assert(requestTrue.url.queryParams.queryParam(isAge).get)(Assertion.equalTo("true")) &&
        assert(requestFalse.url.queryParams.queryParam(isAge).get)(Assertion.equalTo("false"))
      }
    }
    suiteAll("Codec with examples") {
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
    }
  }

}
