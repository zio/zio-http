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

import zio._
import zio.test.Assertion._
import zio.test._

import zio.stream.ZStream

object ResponseSpec extends ZIOHttpSpec {
  def extractStatus(response: Response): Status = response.status
  private val location: URL                     = URL.decode("www.google.com").toOption.get

  def spec = suite("Response")(
    suite("fromCause")(
      test("from Response") {
        val cause = Cause.fail(Response.ok)

        assertTrue(Response.fromCause(cause) == Response.ok)
      },
      test("from IllegalArgumentException") {
        val cause = Cause.fail(new IllegalArgumentException)

        assertTrue(extractStatus(Response.fromCause(cause)) == Status.BadRequest)
      },
      test("from String") {
        val cause = Cause.fail("error")

        assertTrue(extractStatus(Response.fromCause(cause)) == Status.InternalServerError)
      },
    ),
    suite("fromThrowable")(
      test("from Throwable") {
        assertTrue(extractStatus(Response.fromThrowable(new Throwable)) == Status.InternalServerError)
      },
      test("from IllegalArgumentException") {
        assertTrue(extractStatus(Response.fromThrowable(new IllegalArgumentException)) == Status.BadRequest)
      },
    ),
    suite("redirect")(
      test("Temporary redirect should produce a response with a TEMPORARY_REDIRECT") {
        val x = Response.redirect(location)
        assertTrue(
          extractStatus(x) == Status.TemporaryRedirect,
          x.header(Header.Location).contains(Header.Location(location)),
        )
      },
      test("Temporary redirect should produce a response with a location") {
        val x = Response.redirect(location)
        assertTrue(
          x.header(Header.Location).contains(Header.Location(location)),
        )
      },
      test("Permanent redirect should produce a response with a PERMANENT_REDIRECT") {
        val x = Response.redirect(location, isPermanent = true)
        assertTrue(extractStatus(x) == Status.PermanentRedirect)
      },
      test("Permanent redirect should produce a response with a location") {
        val x = Response.redirect(location, isPermanent = true)
        assertTrue(
          x.headerOrFail(Header.Location).contains(Right(Header.Location(location))),
        )
      },
    ),
    suite("cookie")(
      test("should include multiple SetCookie") {
        val firstCookie  = Cookie.Response("first", "value")
        val secondCookie = Cookie.Response("second", "value2")
        val res          =
          Response.ok.addCookie(firstCookie).addCookie(secondCookie)
        assert(res.headers(Header.SetCookie))(
          hasSameElements(
            Seq(Header.SetCookie(firstCookie), Header.SetCookie(secondCookie)),
          ),
        )
      },
    ),
    suite("json")(
      test("Json should set content type to ApplicationJson") {
        val x = Response.json("""{"message": "Hello"}""")
        assertTrue(x.header(Header.ContentType).contains(Header.ContentType(MediaType.application.json)))
      },
    ),
    suite("toHttp")(
      test("should convert response to Http") {
        val ok   = Response.ok
        val http = ok.toHandler
        assertZIO(http.runZIO(()))(equalTo(ok))
      },
    ),
    suite("ignore")(
      test("consumes the stream") {
        for {
          flag <- Ref.make(false)
          stream   = ZStream.succeed(1.toByte) ++ ZStream.fromZIO(flag.set(true).as(2.toByte))
          response = Response(body = Body.fromStreamChunked(stream))
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
          response = Response(body = Body.fromStreamChunked(stream))
          _  <- response.ignoreBody
          v1 <- flag1.get
          v2 <- flag2.get
        } yield assertTrue(v1, !v2)
      },
    ),
    suite("collect")(
      test("materializes the stream") {
        val stream   = ZStream.succeed(1.toByte) ++ ZStream.succeed(2.toByte)
        val response = Response(body = Body.fromStreamChunked(stream))
        for {
          newResp <- response.collect
          body = newResp.body
          bytes <- body.asChunk
        } yield assertTrue(body.isComplete, body.isInstanceOf[Body.UnsafeBytes], bytes == Chunk[Byte](1, 2))
      },
      test("failures are preserved") {
        val err      = new Throwable("boom")
        val stream   = ZStream.succeed(1.toByte) ++ ZStream.fail(err) ++ ZStream.succeed(2.toByte)
        val response = Response(body = Body.fromStreamChunked(stream))
        for {
          newResp <- response.collect
          body = newResp.body
          bytes <- body.asChunk.either
        } yield assertTrue(body.isComplete, body.isInstanceOf[Body.ErrorBody], bytes == Left(err))
      },
    ),
  )
}
