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
import zio.test.TestAspect.{timeout, withLiveClock}
import zio.test.{Gen, TestEnvironment, assertTrue, assertZIO, checkAll}
import zio.{Exit, Scope, ZIO, durationInt}

import zio.http.Header.AccessControlAllowMethods
import zio.http.HttpAppMiddleware.cors
import zio.http.internal.middlewares.Cors.CorsConfig
import zio.http.internal.{DynamicServer, HttpGen, HttpRunnableSpec, severTestLayer, testClientLayer}

object StaticServerSpec extends HttpRunnableSpec {

  private val staticApp = Routes(
    Method.GET / "success"       -> handler(Response.ok),
    Method.GET / "failure"       -> handler(ZIO.fail(new RuntimeException("FAILURE"))),
    Method.GET / "die"           -> handler(ZIO.die(new RuntimeException("DIE"))),
    Method.GET / "get%2Fsuccess" -> handler(Response.ok),
  ).ignoreErrors.toApp

  // Use this route to test anything that doesn't require ZIO related computations.
  private val nonZIO = Routes(
    Method.ANY / "ExitSuccess" -> handler(Exit.succeed(Response.ok)),
    Method.ANY / "ExitFailure" -> handler(Exit.fail(new RuntimeException("FAILURE"))),
    Method.ANY / "throwable"   -> handler(throw new Exception("Throw inside Handler")),
  ).ignoreErrors.toApp

  private val staticAppWithCors = Http.collectZIO[Request] { case Method.GET -> Root / "success-cors" =>
    ZIO.succeed(Response.ok.addHeader(Header.Vary("test1", "test2")))
  } @@ cors(CorsConfig(allowedMethods = AccessControlAllowMethods(Method.GET, Method.POST)))

  private val app = serve { (nonZIO ++ staticApp ++ staticAppWithCors).withDefaultErrorResponse }

  private val methodGenWithoutHEAD: Gen[Any, Method] = Gen.fromIterable(
    List(
      Method.OPTIONS,
      Method.GET,
      Method.POST,
      Method.PUT,
      Method.PATCH,
      Method.DELETE,
      Method.TRACE,
      Method.CONNECT,
    ),
  )

  def nonZIOSpec = suite("NonZIOSpec")(
    test("200 response") {
      checkAll(HttpGen.method) { method =>
        val actual = status(method, Root / "ExitSuccess")
        assertZIO(actual)(equalTo(Status.Ok))
      }
    },
    test("500 response") {
      checkAll(methodGenWithoutHEAD) { method =>
        val actual = status(method, Root / "ExitFailure")
        assertZIO(actual)(equalTo(Status.InternalServerError))
      }
    },
    test("404 response ") {
      checkAll(methodGenWithoutHEAD) { method =>
        val actual = status(method, Root / "A")
        assertZIO(actual)(equalTo(Status.NotFound))
      }
    },
  )

  override def spec =
    suite("Server") {
      app
        .as(
          List(staticAppSpec, nonZIOSpec, throwableAppSpec, multiHeadersSpec),
        )
    }.provideSomeShared[TestEnvironment](
      DynamicServer.live,
      severTestLayer,
      testClientLayer,
      Scope.default,
    ) @@ timeout(30 seconds) @@ withLiveClock

  def staticAppSpec    =
    suite("StaticAppSpec")(
      test("200 response") {
        val actual = status(path = Root / "success")
        assertZIO(actual)(equalTo(Status.Ok))
      },
      test("500 response on failure") {
        val actual = status(path = Root / "failure")
        assertZIO(actual)(equalTo(Status.InternalServerError))
      },
      test("500 response on die") {
        val actual = status(path = Root / "die")
        assertZIO(actual)(equalTo(Status.InternalServerError))
      },
      test("404 response") {
        val actual = status(path = Root / "random")
        assertZIO(actual)(equalTo(Status.NotFound))
      },
      test("200 response with encoded path") {
        val actual = status(path = Root / "get%2Fsuccess")
        assertZIO(actual)(equalTo(Status.Ok))
      },
      test("Multiple 200 response") {
        for {
          data <- status(path = Root / "success").repeatN(1024)
        } yield assertTrue(data == Status.Ok)
      },
    )
  def throwableAppSpec = suite("ThrowableAppSpec") {
    test("Throw inside Handler") {
      for {
        status <- status(Method.GET, Root / "throwable")
      } yield assertTrue(status == Status.InternalServerError)
    }
  }

  def multiHeadersSpec = suite("Multi headers spec")(
    test("CORS headers should be properly encoded") {
      for {
        result <- headers(Method.GET, Root / "success-cors", Headers(Header.Origin("http", "example.com")))
      } yield {
        assertTrue(
          result.hasHeader(Header.AccessControlAllowMethods.name),
          result.hasHeader(Header.AccessControlAllowMethods(Method.GET, Method.POST)),
        )
      }
    },
  )
}
