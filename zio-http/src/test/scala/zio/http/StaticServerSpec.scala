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
import zio.test.TestAspect.timeout
import zio.test.{Gen, TestEnvironment, assertTrue, assertZIO, checkAll}
import zio.{Exit, Scope, ZIO, durationInt}

import zio.http.HttpAppMiddleware.cors
import zio.http.internal.{DynamicServer, HttpGen, HttpRunnableSpec, severTestLayer}
import zio.http.middleware.Cors.CorsConfig
import zio.http.model._

object StaticServerSpec extends HttpRunnableSpec {

  private val staticApp = Http.collectZIO[Request] {
    case Method.GET -> !! / "success"       => ZIO.succeed(Response.ok)
    case Method.GET -> !! / "failure"       => ZIO.fail(new RuntimeException("FAILURE"))
    case Method.GET -> !! / "die"           => ZIO.die(new RuntimeException("DIE"))
    case Method.GET -> !! / "get%2Fsuccess" => ZIO.succeed(Response.ok)
  }

  // Use this route to test anything that doesn't require ZIO related computations.
  private val nonZIO = Http.collectExit[Request] {
    case _ -> !! / "ExitSuccess" => Exit.succeed(Response.ok)
    case _ -> !! / "ExitFailure" => Exit.fail(new RuntimeException("FAILURE"))
    case _ -> !! / "throwable"   => throw new Exception("Throw inside Handler")
  }

  private val staticAppWithCors = Http.collectZIO[Request] { case Method.GET -> !! / "success-cors" =>
    ZIO.succeed(Response.ok.withHeader(Header.Vary("test1")).withHeader(Header.Vary("test2")))
  } @@ cors(CorsConfig(allowedMethods = Some(Set(Method.GET, Method.POST))))

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
        val actual = status(method, !! / "ExitSuccess")
        assertZIO(actual)(equalTo(Status.Ok))
      }
    },
    test("500 response") {
      checkAll(methodGenWithoutHEAD) { method =>
        val actual = status(method, !! / "ExitFailure")
        assertZIO(actual)(equalTo(Status.InternalServerError))
      }
    },
    test("404 response ") {
      checkAll(methodGenWithoutHEAD) { method =>
        val actual = status(method, !! / "A")
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
      Client.default,
      Scope.default,
    ) @@
      timeout(30 seconds)

  def staticAppSpec    =
    suite("StaticAppSpec")(
      test("200 response") {
        val actual = status(path = !! / "success")
        assertZIO(actual)(equalTo(Status.Ok))
      },
      test("500 response on failure") {
        val actual = status(path = !! / "failure")
        assertZIO(actual)(equalTo(Status.InternalServerError))
      },
      test("500 response on die") {
        val actual = status(path = !! / "die")
        assertZIO(actual)(equalTo(Status.InternalServerError))
      },
      test("404 response") {
        val actual = status(path = !! / "random")
        assertZIO(actual)(equalTo(Status.NotFound))
      },
      test("200 response with encoded path") {
        val actual = status(path = !! / "get%2Fsuccess")
        assertZIO(actual)(equalTo(Status.Ok))
      },
      test("Multiple 200 response") {
        for {
          data <- status(path = !! / "success").repeatN(1024)
        } yield assertTrue(data == Status.Ok)
      },
    )
  def throwableAppSpec = suite("ThrowableAppSpec") {
    test("Throw inside Handler") {
      for {
        status <- status(Method.GET, !! / "throwable")
      } yield assertTrue(status == Status.InternalServerError)
    }
  }

  def multiHeadersSpec = suite("Multi headers spec")(
    test("Multiple headers should have the value combined in a single header") {
      for {
        result <- headers(Method.GET, !! / "success-cors")
      } yield {
        assertTrue(
          result.hasHeader(HeaderNames.vary),
          result.header(Header.Vary).contains(Header.Vary("test1", "test2")),
        )
      }
    },
    test("CORS headers should be properly encoded") {
      for {
        result <- headers(Method.GET, !! / "success-cors", Headers(Header.Origin.Value("", "example.com")))
      } yield {
        assertTrue(
          result.hasHeader(HeaderNames.accessControlAllowMethods),
          result
            .header(Header.AccessControlAllowMethods)
            .contains(Header.AccessControlAllowMethods(Method.GET, Method.POST)),
        )
      }
    },
  )
}
