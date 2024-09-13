/*
 * Copyright 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
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

import zio.http.codec.{HttpCodec, StatusCodec}
import zio.http.endpoint.{AuthType, Endpoint}

object RouteSpec extends ZIOHttpSpec {

  def spec = suite("RouteSpec")(
    suite("Route#prefix")(
      test("prefix should add a prefix to the route") {
        val route =
          Method.GET / "foo" -> handler(Response.ok)

        val prefixed = route.nest("bar")

        assertTrue(prefixed.isDefinedAt(Request.get(url"/bar/foo")))
      },
    ),
    suite("Route#sandbox")(
      test("infallible route does not change under sandbox") {
        val route =
          Method.GET / "foo" -> handler(Response.ok)

        val ignored = route.sandbox

        for {
          result <- ignored.toHandler.run().merge
        } yield assertTrue(result.status == Status.Ok)
      },
      test("route dying with throwable ends in internal server error") {
        val route =
          Method.GET / "foo" ->
            Handler.die(new Throwable("boom"))

        val ignored = route.sandbox

        for {
          result <- ignored.toHandler.merge.run()
        } yield assertTrue(result.status == Status.InternalServerError)
      },
    ),
    suite("auto-sandboxing for middleware")(
      test("die error does not stop middleware from executing") {
        val route =
          Method.GET / "foo" ->
            Handler.die(new Throwable("boom"))

        val handler = route.toHandler

        for {
          ref <- Ref.make(0)
          middleware = Middleware.runBefore(ref.update(_ + 1)) ++ Middleware.runAfter(ref.update(_ + 1))
          _   <- (handler @@ middleware).run().exit
          cnt <- ref.get
        } yield assertTrue(cnt == 2)
      },
    ),
    suite("error handle")(
      test("handleErrorCauseZIO should execute a ZIO effect") {
        val route = Method.GET / "endpoint" -> handler { (_: Request) => ZIO.fail(new Exception("hmm...")) }
        for {
          p <- zio.Promise.make[Exception, String]

          errorHandled = route
            .handleErrorCauseZIO(c => p.failCause(c).as(Response.internalServerError))

          request = Request.get(URL.decode("/endpoint").toOption.get)
          response <- errorHandled.toRoutes.runZIO(request)
          result   <- p.await.catchAllCause(c => ZIO.succeed(c.prettyPrint))

        } yield assertTrue(response.status == Status.InternalServerError, result.contains("hmm..."))
      },
      test("handleErrorCauseZIO should not execute the ZIO effect if called on handled route") {
        val route = Route.handled(Method.GET / "endpoint")(Handler.fail(Response.status(Status.ExpectationFailed)))
        val errorHandled = route
          .handleErrorCauseZIO(_ => ZIO.die(new Exception("hmm...")))
        val request      = Request.get(URL.decode("/endpoint").toOption.get)
        for {
          response <- errorHandled.toRoutes.runZIO(request)
        } yield assertTrue(response.status == Status.ExpectationFailed)
      },
      test("handleErrorCauseZIO should execute the ZIO effect on die") {
        val route        = Route.handled(Method.GET / "endpoint")(Handler.fromZIO(ZIO.dieMessage("hmm...")))
        val errorHandled = route.handleErrorCauseZIO(_ => ZIO.succeed(Response.status(Status.ExpectationFailed)))
        val request      = Request.get(URL.decode("/endpoint").toOption.get)
        for {
          response <- errorHandled.toRoutes.runZIO(request)
        } yield assertTrue(response.status == Status.ExpectationFailed)
      },
      test("handleErrorCauseRequestZIO should produce an error based on the request") {
        val route = Method.GET / "endpoint" -> handler { (_: Request) => ZIO.fail(new Exception("hmm...")) }
        for {
          p <- zio.Promise.make[Exception, String]

          errorHandled = route
            .handleErrorRequestCauseZIO((req, c) =>
              p.failCause(c).as(Response.internalServerError(s"error accessing ${req.path.encode}")),
            )

          request = Request.get(URL.decode("/endpoint").toOption.get)
          response <- errorHandled.toRoutes.runZIO(request)
          result   <- p.await.catchAllCause(c => ZIO.succeed(c.prettyPrint))
          body     <- response.body.asString
        } yield assertTrue(
          response.status == Status.InternalServerError,
          result.contains("hmm..."),
          body == "error accessing /endpoint",
        )
      },
      test("handleErrorCauseRequest should produce an error based on the request") {
        val route        = Method.GET / "endpoint" -> handler { (_: Request) => ZIO.fail(new Exception("hmm...")) }
        val errorHandled =
          route.handleErrorRequest((e, req) =>
            Response.internalServerError(s"error accessing ${req.path.encode}: ${e.getMessage}"),
          )
        val request      = Request.get(URL.decode("/endpoint").toOption.get)
        for {
          response <- errorHandled.toRoutes.runZIO(request)
          body     <- response.body.asString

        } yield assertTrue(
          response.status == Status.InternalServerError,
          body == "error accessing /endpoint: hmm...",
        )
      },
      test("handleErrorCause should handle defects") {
        val route        = Method.GET / "endpoint" -> handler { (_: Request) => ZIO.dieMessage("hmm...") }
        val errorHandled = route.handleErrorCause(_ => Response.text("error").status(Status.InternalServerError))
        val request      = Request.get(URL.decode("/endpoint").toOption.get)
        for {
          response   <- errorHandled.toRoutes.runZIO(request)
          bodyString <- response.body.asString
        } yield assertTrue(response.status == Status.InternalServerError, bodyString == "error")
      },
      test("handleErrorCauseZIO should handle defects") {
        val route        = Method.GET / "endpoint" -> handler { (_: Request) => ZIO.dieMessage("hmm...") }
        val errorHandled =
          route.handleErrorCauseZIO(_ => ZIO.succeed(Response.text("error").status(Status.InternalServerError)))
        val request      = Request.get(URL.decode("/endpoint").toOption.get)
        for {
          response   <- errorHandled.toRoutes.runZIO(request)
          bodyString <- response.body.asString
        } yield assertTrue(response.status == Status.InternalServerError, bodyString == "error")
      },
      test("handleErrorRequestCause should handle defects") {
        val route        = Method.GET / "endpoint" -> handler { (_: Request) => ZIO.dieMessage("hmm...") }
        val errorHandled =
          route.handleErrorRequestCause((_, _) => Response.text("error").status(Status.InternalServerError))
        val request      = Request.get(URL.decode("/endpoint").toOption.get)
        for {
          response   <- errorHandled.toRoutes.runZIO(request)
          bodyString <- response.body.asString
        } yield assertTrue(response.status == Status.InternalServerError, bodyString == "error")
      },
      test("handleErrorRequestCauseZIO should handle defects") {
        val route        = Method.GET / "endpoint" -> handler { (_: Request) => ZIO.dieMessage("hmm...") }
        val errorHandled = route.handleErrorRequestCauseZIO((_, _) =>
          ZIO.succeed(Response.text("error").status(Status.InternalServerError)),
        )
        val request      = Request.get(URL.decode("/endpoint").toOption.get)
        for {
          response   <- errorHandled.toRoutes.runZIO(request)
          bodyString <- response.body.asString
        } yield assertTrue(response.status == Status.InternalServerError, bodyString == "error")
      },
      test(
        "Routes with context can eliminate environment type partially when elimination produces intersection type environment",
      ) {

        val authContext: HandlerAspect[Any, String] = HandlerAspect.customAuthProviding[String] { request =>
          {
            request.headers.get(Header.Authorization).flatMap {
              case Header.Authorization.Basic(uname, secret) if uname.reverse == secret.value.mkString =>
                Some(uname)
              case _                                                                                   =>
                None
            }
          }
        }

        val endpoint = Endpoint(RoutePattern(Method.GET, Path.root))
          .outCodec[String](StatusCodec.Ok ++ HttpCodec.content[String])
          .auth(AuthType.Basic)

        val effectWithTwoDependency: ZIO[Int & Long, Nothing, String] = for {
          int  <- ZIO.service[Int]
          long <- ZIO.service[Long]
        } yield s"effectWithTwoDependencyResult $int $long"

        val route: Route[Int & Long & String, Nothing] =
          endpoint.implement((_: Unit) => withContext((_: String) => "") *> effectWithTwoDependency)
        val routes                                     = Routes(route).@@[Int & Long](authContext)

        val env: ZEnvironment[Int with Long] = ZEnvironment(1).add(2L)
        val expected                         = "\"effectWithTwoDependencyResult 1 2\""
        for {
          response   <- routes
            .provideEnvironment(env)
            .apply(
              Request(
                headers = Headers("accept", "text") ++ Headers(Header.Authorization.Basic("123", "321")),
                method = Method.GET,
              ).path(Path.root),
            )
          bodyString <- response.body.asString
        } yield assertTrue(bodyString == expected)
      },
    ),
  )
}
