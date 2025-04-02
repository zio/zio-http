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

import zio.test._
import zio.{Chunk, NonEmptyChunk, Ref, Scope}
import zio.stream.ZStream

object RequestSpec extends ZIOHttpSpec {

  def spec: Spec[TestEnvironment with Scope, Any] = suite("Result")(
    test("`#default`") {
      val body     = Body.fromString("foo")
      val expected = Request(
        method = Method.POST,
        body = body,
      )

      val actual = Request.post(URL.empty, body)
      assertTrue(actual == expected)
    },
    test("`#delete`") {
      val expected = Request(
        method = Method.DELETE,
      )

      val actual = Request.delete(URL.empty)
      assertTrue(actual == expected)
    },
    test("`#get`") {
      val expected = Request(
        method = Method.GET,
      )

      val actual = Request.get(URL.empty)
      assertTrue(actual == expected)
    },
    test("`#options`") {
      val expected = Request(
        method = Method.OPTIONS,
      )

      val actual = Request.options(URL.empty)
      assertTrue(actual == expected)
    },
    test("`#patch`") {
      val body     = Body.fromString("foo")
      val expected = Request(
        method = Method.PATCH,
        body = body,
      )

      val actual = Request.patch(URL.empty, body)
      assertTrue(actual == expected)
    },
    test("`#post`") {
      val body     = Body.fromString("foo")
      val expected = Request(
        method = Method.POST,
        body = body,
      )

      val actual = Request.post(URL.empty, body)
      assertTrue(actual == expected)
    },
    test("`#put`") {
      val body     = Body.fromString("foo")
      val expected = Request(
        method = Method.PUT,
        body = body,
      )

      val actual = Request.put(URL.empty, body)
      assertTrue(actual == expected)
    },
    test("path string") {
      val expected = Request(method = Method.GET, url = url"/foo/bar")
      val actual   = Request.get("/foo/bar")
      assertTrue(actual == expected)
    },
    test("absolute url string") {
      val expected = Request(method = Method.GET, url = url"https://foo.com/bar")
      val actual   = Request.get("https://foo.com/bar")
      assertTrue(actual == expected)
    },
    suite("ignore")(
      test("consumes the stream") {
        for {
          flag <- Ref.make(false)
          stream   = ZStream.succeed(1.toByte) ++ ZStream.fromZIO(flag.set(true).as(2.toByte))
          response = Request(body = Body.fromStreamChunked(stream))
          _ <- response.ignoreBody
          v <- flag.get
        } yield assertTrue(v)
      },
      test("ignores failures when consuming the stream") {
        for {
          flag1 <- Ref.make(false)
          flag2 <- Ref.make(false)
          stream   = ZStream.succeed(1.toByte) ++
            ZStream.fromZIO(flag1.set(true).as(2.toByte)) ++
            ZStream.fail(new Throwable("boom")) ++
            ZStream.fromZIO(flag1.set(true).as(2.toByte))
          response = Request(body = Body.fromStreamChunked(stream))
          _  <- response.ignoreBody
          v1 <- flag1.get
          v2 <- flag2.get
        } yield assertTrue(v1, !v2)
      },
    ),
    suite("collect")(
      test("materializes the stream") {
        val stream   = ZStream.succeed(1.toByte) ++ ZStream.succeed(2.toByte)
        val response = Request(body = Body.fromStreamChunked(stream))
        for {
          newResp <- response.collect
          body = newResp.body
          bytes <- body.asChunk
        } yield assertTrue(body.isComplete, body.isInstanceOf[Body.UnsafeBytes], bytes == Chunk[Byte](1, 2))
      },
      test("failures are preserved") {
        val err      = new Throwable("boom")
        val stream   = ZStream.succeed(1.toByte) ++ ZStream.fail(err) ++ ZStream.succeed(2.toByte)
        val response = Request(body = Body.fromStreamChunked(stream))
        for {
          newResp <- response.collect
          body = newResp.body
          bytes <- body.asChunk.either
        } yield assertTrue(body.isComplete, body.isInstanceOf[Body.ErrorBody], bytes == Left(err))
      },
    ),
    suiteAll("query")(
      test("add multiple query parmas") {
        val request        = Request
          .get("https://example.com")
          .addQueryParam("a", 1)
          .addQueryParam("b", 2)
          .addQueryParam("c", 3)
          .addQueryParam("d", 4)
        val expectedParams = QueryParams("a" -> "1", "b" -> "2", "c" -> "3", "d" -> "4")
        assertTrue(request.url.queryParams == expectedParams)
      },
    ),
    suite("addCookie") (
      test("add cookie to request") {
        val c1 = Cookie.Request("n1", "c1")
        val c2 = Cookie.Request("n2", "c2")

        val request =
          Request
            .get(URL.root)
            .addCookie(c1)
            .addCookie(c2)

        assertTrue(request.cookies == Chunk(c1, c2))
      },
      test("add cookies to request") {
        val c1 = Cookie.Request("n1", "c1")
        val c2 = Cookie.Request("n2", "c2")
        val c3 = Cookie.Request("n3", "c3")
        val c4 = Cookie.Request("n4", "c4")

        val request =
          Request
            .get(URL.root)
            .addCookie(c1)
            .addCookie(c2)
            .addCookies(c3, c4)

        assertTrue(request.cookies == Chunk(c1, c2, c3, c4))
      },
    )
  )

}
