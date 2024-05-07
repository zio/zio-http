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
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{timeout, withLiveClock}
import zio.test.{Gen, TestEnvironment, assertTrue, assertZIO, checkAll}

import zio.http.Header.AccessControlAllowMethods
import zio.http.Middleware.{CorsConfig, cors}
import zio.http.internal.{DynamicServer, HttpGen, HttpRunnableSpec, serverTestLayer, testClientLayer}

object StaticServerSpec extends HttpRunnableSpec {

  private val staticApp = Routes(
    Method.GET / "success"       -> handler(Response.ok),
    Method.GET / "failure"       -> handler(ZIO.fail(new RuntimeException("FAILURE"))),
    Method.GET / "die"           -> handler(ZIO.die(new RuntimeException("DIE"))),
    Method.GET / "get%2Fsuccess" -> handler(Response.ok),
  ).sandbox

  // Use this route to test anything that doesn't require ZIO related computations.
  private val nonZIO = Routes(
    Method.ANY / "ExitSuccess" -> handler(Exit.succeed(Response.ok)),
    Method.ANY / "ExitFailure" -> handler(Exit.fail(new RuntimeException("FAILURE"))),
    Method.ANY / "throwable"   -> handlerTODO("Throw inside Handler"),
  ).sandbox

  private val staticAppWithCors = Routes(
    Method.GET / "success-cors" -> handler(Response.ok.addHeader(Header.Vary("test1", "test2"))),
  ) @@ cors(CorsConfig(allowedMethods = AccessControlAllowMethods(Method.GET, Method.POST)))

  private val combined: Routes[Any, Response] = nonZIO ++ staticApp ++ staticAppWithCors

  private val app = serve { combined }

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

  private val methodGenWithoutOPTIONS: Gen[Any, Method] = Gen.fromIterable(
    List(
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
      checkAll(methodGenWithoutOPTIONS) { method =>
        val actual = status(method, Path.root / "ExitSuccess")
        assertZIO(actual)(equalTo(Status.Ok))
      }
    },
    test("500 response") {
      checkAll(methodGenWithoutOPTIONS) { method =>
        val actual = status(method, Path.root / "ExitFailure")
        assertZIO(actual)(equalTo(Status.InternalServerError))
      }
    },
    test("404 response ") {
      checkAll(methodGenWithoutHEAD) { method =>
        val actual = status(method, Path.root / "A")
        assertZIO(actual)(equalTo(Status.NotFound))
      }
    },
  )

  override def spec =
    suite("StaticServerSpec") {
      app
        .as(
          List(staticAppSpec, nonZIOSpec, throwableAppSpec, multiHeadersSpec),
        )
    }.provideSome[DynamicServer & Server & Client](Scope.default)
      .provideShared(
        DynamicServer.live,
        serverTestLayer,
        testClientLayer,
      ) @@ withLiveClock

  def staticAppSpec    =
    suite("StaticAppSpec")(
      test("200 response") {
        val actual = status(path = Path.root / "success")
        assertZIO(actual)(equalTo(Status.Ok))
      },
      test("500 response on failure") {
        val actual = status(path = Path.root / "failure")
        assertZIO(actual)(equalTo(Status.InternalServerError))
      },
      test("500 response on die") {
        val actual = status(path = Path.root / "die")
        assertZIO(actual)(equalTo(Status.InternalServerError))
      },
      test("404 response") {
        val actual = status(path = Path.root / "random")
        assertZIO(actual)(equalTo(Status.NotFound))
      },
      test("200 response with encoded path") {
        val actual = status(path = Path.root / "get%2Fsuccess")
        assertZIO(actual)(equalTo(Status.Ok))
      },
      test("Multiple 200 response") {
        for {
          data <- status(path = Path.root / "success").repeatN(1024)
        } yield assertTrue(data == Status.Ok)
      },
    )
  def throwableAppSpec = suite("ThrowableAppSpec") {
    test("Throw inside Handler") {
      for {
        status <- status(Method.GET, Path.root / "throwable")
      } yield assertTrue(status == Status.InternalServerError)
    }
  }

  def multiHeadersSpec = suite("Multi headers spec")(
    test("CORS headers should be properly encoded") {
      for {
        result <- headers(Method.GET, Path.root / "success-cors", Headers(Header.Origin("http", "example.com")))
      } yield {
        assertTrue(
          result.hasHeader(Header.AccessControlAllowMethods.name),
          result.hasHeader(Header.AccessControlAllowMethods(Method.GET, Method.POST)),
        )
      }
    },
  )
}
