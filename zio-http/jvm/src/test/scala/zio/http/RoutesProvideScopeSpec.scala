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
import zio.test._

import zio.http.endpoint.Endpoint

/**
 * Regression tests for https://github.com/zio/zio-http/issues/4318:
 * `Routes.provide` (and the sibling layer operators) must preserve the
 * per-request `Scope` instead of replacing the whole environment, otherwise
 * `Endpoint.implementScoped` handlers die with
 * `Defect in zio.ZEnvironment: Could not find Scope`.
 */
object RoutesProvideScopeSpec extends ZIOHttpSpec {

  val aspect: HandlerAspect[Int, String] =
    HandlerAspect.customAuthProvidingZIO[Int, String] { _ =>
      ZIO.serviceWith[Int](n => Some(n.toString))
    }

  def implementScopedBody: Unit => ZIO[Scope & String, Nothing, String] =
    _ =>
      for {
        _   <- ZIO.service[Scope]
        ctx <- ZIO.service[String]
      } yield ctx

  def spec = suite("RoutesProvideScopeSpec")(
    test("Routes.provide preserves the per-request Scope") {
      val good: Routes[Any, Nothing] =
        Routes(Endpoint(Method.GET / "good").out[String].implementScoped(implementScopedBody))
          .@@[Any, String](aspect.provideEnvironment(ZEnvironment(42)))
      val bad: Routes[Any, Nothing]  =
        Routes(Endpoint(Method.GET / "bad").out[String].implementScoped(implementScopedBody))
          .@@[Int, String](aspect)
          .provide(42)

      for {
        goodResponse <- good.runZIO(Request.get(URL.decode("/good").toOption.get))
        goodBody     <- goodResponse.body.asString.orDie
        badResponse  <- bad.runZIO(Request.get(URL.decode("/bad").toOption.get))
        badBody      <- badResponse.body.asString.orDie
      } yield assertTrue(
        goodResponse.status == Status.Ok,
        badResponse.status == Status.Ok,
        goodBody == "\"42\"",
        badBody == "\"42\"",
      )
    },
    test("Handler.provideLayer preserves the per-request Scope") {
      val handler: Handler[Int, Response, Request, Response] =
        Handler.scoped[Int] {
          Handler.fromFunctionZIO[Request](_ =>
            ZIO.serviceWithZIO[Scope](_ => ZIO.serviceWith[Int](n => Response.text(n.toString))),
          )
        }
      val app                                                = handler.provideLayer(ZLayer.succeed(42))

      for {
        response <- app.runZIO(Request.get(URL.root))
        body     <- response.body.asString.orDie
      } yield assertTrue(body == "42")
    },
    test("Handler.provideSomeLayer preserves the per-request Scope") {
      val handler: Handler[Int, Response, Request, Response] =
        Handler.scoped[Int] {
          Handler.fromFunctionZIO[Request](_ =>
            ZIO.serviceWithZIO[Scope](_ => ZIO.serviceWith[Int](n => Response.text(n.toString))),
          )
        }
      // R0 = String (a proper subset of the ambient env) so that a Scope-wiping
      // implementation loses the request Scope; R0 = Any would pass everything through.
      val stringToInt: ZLayer[String, Nothing, Int]          =
        ZLayer.fromZIO(ZIO.serviceWith[String](_.length))
      val app = handler.provideSomeLayer(stringToInt).provideEnvironment(ZEnvironment("hello!"))

      for {
        response <- app.runZIO(Request.get(URL.root))
        body     <- response.body.asString.orDie
      } yield assertTrue(body == "6")
    },
  ).provide(Scope.default)
}
