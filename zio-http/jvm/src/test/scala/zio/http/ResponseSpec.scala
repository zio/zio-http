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

import zio.schema.codec.JsonCodec._
import zio.schema.{DeriveSchema, Schema}

import zio.http.ErrorResponseConfig.ErrorFormat

object ResponseSpec extends ZIOHttpSpec {
  def extractStatus(response: Response): Status = response.status
  private val location: URL                     = URL.decode("www.google.com").toOption.get
  case class Person(name: String, age: Int)
  implicit val schema: Schema[Person]           = DeriveSchema.gen[Person]

  def spec = suite("Response")(
    suite("fromCause")(
      test("from Response") {
        val cause = Cause.fail(Response.ok)

        assertTrue(Response.fromCause(cause) == Response.ok)
      },
      test("from IllegalArgumentException") {
        val cause            = Cause.fail(new IllegalArgumentException)
        val responseWithBody = Response.fromCause(cause, ErrorResponseConfig(withErrorBody = true))
        val responseNoBody   = Response.fromCause(cause, ErrorResponseConfig(withErrorBody = false))

        val expectedErrorMsg =
          """<!DOCTYPE html><html><head><title>ZIO Http - BadRequest</title><style>
            | body {
            |   font-family: monospace;
            |   font-size: 16px;
            |   background-color: #edede0;
            | }
            |</style></head><body><div style="margin: auto; padding: 2em 4em; max-width: 80%"><h1>BadRequest</h1><div><div style="text-align: center"><div style="font-size: 20em">400</div><div></div><div></div></div></div></div></body></html>""".stripMargin

        val expectedErrorBody = Body.fromString(expectedErrorMsg).contentType(MediaType.text.html)

        assertTrue(
          extractStatus(responseWithBody) == Status.BadRequest,
          extractStatus(responseNoBody) == Status.BadRequest,
          responseWithBody.body == expectedErrorBody,
          responseNoBody.body == Body.empty,
        )
      },
      test("from String") {
        val cause = Cause.fail("error")

        val responseWithBody = Response.fromCause(cause, ErrorResponseConfig(withErrorBody = true))
        val responseNoBody   = Response.fromCause(cause, ErrorResponseConfig(withErrorBody = false))
        val expectedErrorMsg = "Exception in thread \"zio-fiber-\" java.lang.String: error"
        val expectedBody     = Body.fromString(expectedErrorMsg).contentType(MediaType.text.plain)

        assertTrue(
          extractStatus(responseWithBody) == Status.InternalServerError,
          extractStatus(responseNoBody) == Status.InternalServerError,
          responseWithBody.body == expectedBody,
          responseNoBody.body == Body.empty,
        )
      },
    ),
    suite("fromThrowable")(
      test("from Throwable") {
        val throwable               = new Throwable
        val stackTrace10            = throwable.getStackTrace.take(10).mkString("\n", "\n", "")
        val stackTraceFull          = throwable.getStackTrace.mkString("\n", "\n", "")
        val responseWithBody        = Response.fromThrowable(throwable, ErrorResponseConfig(withErrorBody = true))
        val responseWithStacktrace  =
          Response.fromThrowable(throwable, ErrorResponseConfig(withErrorBody = true, withStackTrace = true))
        val responseWithStacktrace2 = Response.fromThrowable(
          throwable,
          ErrorResponseConfig(withErrorBody = true, withStackTrace = true, maxStackTraceDepth = 0),
        )
        val responseNoBody          = Response.fromThrowable(throwable, ErrorResponseConfig(withErrorBody = false))
        val expectedErrorMsg        =
          """<!DOCTYPE html><html><head><title>ZIO Http - InternalServerError</title><style>
            | body {
            |   font-family: monospace;
            |   font-size: 16px;
            |   background-color: #edede0;
            | }
            |</style></head><body><div style="margin: auto; padding: 2em 4em; max-width: 80%"><h1>InternalServerError</h1><div><div style="text-align: center"><div style="font-size: 20em">500</div><div></div><div></div></div></div></div></body></html>""".stripMargin
        def expectedErrorMsgStackTrace(stackTrace: String) =
          s"""<!DOCTYPE html><html><head><title>ZIO Http - InternalServerError</title><style>
             | body {
             |   font-family: monospace;
             |   font-size: 16px;
             |   background-color: #edede0;
             | }
             |</style></head><body><div style="margin: auto; padding: 2em 4em; max-width: 80%"><h1>InternalServerError</h1><div><div style="text-align: center"><div style="font-size: 20em">500</div><div></div><div>$stackTrace</div></div></div></div></body></html>""".stripMargin
        val expectedBody                = Body.fromString(expectedErrorMsg).contentType(MediaType.text.`html`)
        val expectedBodyWithStackTrace  =
          Body.fromString(expectedErrorMsgStackTrace(stackTrace10)).contentType(MediaType.text.`html`)
        val expectedBodyWithStackTrace2 =
          Body.fromString(expectedErrorMsgStackTrace(stackTraceFull)).contentType(MediaType.text.`html`)
        assertTrue(
          extractStatus(responseNoBody) == Status.InternalServerError,
          extractStatus(responseWithBody) == Status.InternalServerError,
          responseNoBody.body == Body.empty,
          responseWithBody.body == expectedBody,
          responseWithStacktrace.body == expectedBodyWithStackTrace,
          responseWithStacktrace2.body == expectedBodyWithStackTrace2,
        )
      },
      test("from IllegalArgumentException") {
        val exception               = new IllegalArgumentException("Some message")
        val stackTrace10            = exception.getStackTrace.take(10).mkString("\n", "\n", "")
        val stackTraceFull          = exception.getStackTrace.mkString("\n", "\n", "")
        val responseWithBody        = Response.fromThrowable(exception, ErrorResponseConfig(withErrorBody = true))
        val responseWithStacktrace  =
          Response.fromThrowable(exception, ErrorResponseConfig(withErrorBody = true, withStackTrace = true))
        val responseWithStacktrace2 = Response.fromThrowable(
          exception,
          ErrorResponseConfig(withErrorBody = true, withStackTrace = true, maxStackTraceDepth = 0),
        )
        val responseNoBody          = Response.fromThrowable(exception, ErrorResponseConfig(withErrorBody = false))
        val expectedErrorMsg        =
          """<!DOCTYPE html><html><head><title>ZIO Http - BadRequest</title><style>
            | body {
            |   font-family: monospace;
            |   font-size: 16px;
            |   background-color: #edede0;
            | }
            |</style></head><body><div style="margin: auto; padding: 2em 4em; max-width: 80%"><h1>BadRequest</h1><div><div style="text-align: center"><div style="font-size: 20em">400</div><div>Some message</div><div></div></div></div></div></body></html>""".stripMargin
        def expectedErrorMsgStackTrace(stackTrace: String) =
          s"""<!DOCTYPE html><html><head><title>ZIO Http - BadRequest</title><style>
             | body {
             |   font-family: monospace;
             |   font-size: 16px;
             |   background-color: #edede0;
             | }
             |</style></head><body><div style="margin: auto; padding: 2em 4em; max-width: 80%"><h1>BadRequest</h1><div><div style="text-align: center"><div style="font-size: 20em">400</div><div>Some message</div><div>${stackTrace}</div></div></div></div></body></html>""".stripMargin
        val expectedErrorBody                = Body.fromString(expectedErrorMsg).contentType(MediaType.text.html)
        val expectedErrorBodyWithStackTrace  =
          Body.fromString(expectedErrorMsgStackTrace(stackTrace10)).contentType(MediaType.text.html)
        val expectedErrorBodyWithStackTrace2 =
          Body.fromString(expectedErrorMsgStackTrace(stackTraceFull)).contentType(MediaType.text.html)
        assertTrue(
          extractStatus(responseWithBody) == Status.BadRequest,
          extractStatus(responseNoBody) == Status.BadRequest,
          responseWithBody.body == expectedErrorBody,
          responseNoBody.body == Body.empty,
          responseWithStacktrace.body == expectedErrorBodyWithStackTrace,
          responseWithStacktrace2.body == expectedErrorBodyWithStackTrace2,
        )
      },
      test("from IllegalArgumentException as json") {
        val exception                                      = new IllegalArgumentException("Some message")
        val stackTrace10                                   = exception.getStackTrace.take(10).mkString("\n", "\n", "")
        val stackTraceFull                                 = exception.getStackTrace.mkString("\n", "\n", "")
        val responseWithBody                               =
          Response.fromThrowable(exception, ErrorResponseConfig(withErrorBody = true, errorFormat = ErrorFormat.Json))
        val responseWithStacktrace                         = Response.fromThrowable(
          exception,
          ErrorResponseConfig(withErrorBody = true, withStackTrace = true, errorFormat = ErrorFormat.Json),
        )
        val responseWithStacktrace2                        = Response.fromThrowable(
          exception,
          ErrorResponseConfig(
            withErrorBody = true,
            withStackTrace = true,
            maxStackTraceDepth = 0,
            errorFormat = ErrorFormat.Json,
          ),
        )
        def expectedErrorMsgStackTrace(stackTrace: String) =
          s"""{"status": "${Status.BadRequest.code}", "message": "Some message", "stackTrace": "$stackTrace"}"""
        val expectedErrorBodyWithStackTrace                =
          Body.fromString(expectedErrorMsgStackTrace(stackTrace10)).contentType(MediaType.application.json)
        val expectedErrorBodyWithStackTrace2               =
          Body.fromString(expectedErrorMsgStackTrace(stackTraceFull)).contentType(MediaType.application.json)
        assertTrue(
          extractStatus(responseWithBody) == Status.BadRequest,
          extractStatus(responseWithStacktrace) == Status.BadRequest,
          extractStatus(responseWithStacktrace2) == Status.BadRequest,
          responseWithStacktrace.body == expectedErrorBodyWithStackTrace,
          responseWithStacktrace2.body == expectedErrorBodyWithStackTrace2,
        )
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
    suite("toHandler")(
      test("should convert response to handler") {
        val ok   = Response.ok
        val http = ok.toHandler
        assertZIO(http.runZIO(()))(equalTo(ok))
      },
    ),
    suiteAll("bodyAs")(
      test("Read a json body") {
        val person   = Person("John", 42)
        val body     = Body.fromString("""{"name":"John","age":42}""")
        val response = Response(body = body)
        response.bodyAs[Person].map(p => assertTrue(p == person))
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
