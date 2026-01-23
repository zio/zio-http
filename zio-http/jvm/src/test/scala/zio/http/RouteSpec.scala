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
  def extractStatus(response: Response): Status = response.status

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
        } yield assertTrue(extractStatus(result) == Status.Ok)
      },
      test("route dying with throwable ends in internal server error") {
        val route =
          Method.GET / "foo" ->
            Handler.die(new Throwable("boom"))

        val ignored = route.sandbox

        for {
          result <- ignored.toHandler.merge.run()
        } yield assertTrue(extractStatus(result) == Status.InternalServerError)
      },
      test("sandbox logs defects") {
        val route =
          Method.GET / "foo" ->
            Handler.die(new RuntimeException("boom"))

        for {
          _       <- route.sandbox.toRoutes.runZIO(Request.get(url"/foo")).ignore
          entries <- ZTestLogger.logOutput
        } yield assertTrue(
          entries.exists(e =>
            e.logLevel == LogLevel.Error &&
              e.message().contains("Unhandled exception in request handler"),
          ),
        )
      },
      test("sandbox logs typed failures") {
        val route =
          Method.GET / "foo" ->
            Handler.fail(new Exception("typed error"))

        for {
          _       <- route.sandbox.toRoutes.runZIO(Request.get(url"/foo")).ignore
          entries <- ZTestLogger.logOutput
        } yield assertTrue(
          entries.exists(e =>
            e.logLevel == LogLevel.Error &&
              e.message().contains("Unhandled exception in request handler"),
          ),
        )
      },
      test("sandbox does not log for successful requests") {
        val route =
          Method.GET / "foo" -> handler(Response.ok)

        for {
          _       <- route.sandbox.toRoutes.runZIO(Request.get(url"/foo"))
          entries <- ZTestLogger.logOutput
        } yield assertTrue(
          !entries.exists(e => e.message().contains("Unhandled exception in request handler")),
        )
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
    suite("transform with error handling (#3228)")(
      test("transform with catchAllDefect should intercept defects before sandboxing") {
        val routes = Routes(
          Method.POST / "fail" -> Handler.fromZIO(ZIO.dieMessage("Defect")),
        )

        def logDefects(handle: Handler[Any, Response, Request, Response]): Handler[Any, Response, Request, Response] =
          Handler.scoped[Any] {
            handler { (req: Request) =>
              handle(req).catchAllDefect { e =>
                ZIO
                  .logErrorCause("Application defect", Cause.die(e))
                  .as(Response.internalServerError("Something went wrong"))
              }
            }
          }

        for {
          _       <- routes.transform(logDefects).runZIO(Request.post("/fail", Body.empty)).ignore
          entries <- ZTestLogger.logOutput
          errorLog = entries.find(_.message() == "Application defect")
        } yield assertTrue(
          errorLog.isDefined,
          errorLog.get.logLevel == LogLevel.Error,
        )
      },
      test("transform with catchAllDefect should not affect successful requests") {
        val routes = Routes(
          Method.GET / "ok" -> handler(Response.ok),
        )

        def logDefects(handle: Handler[Any, Response, Request, Response]): Handler[Any, Response, Request, Response] =
          Handler.scoped[Any] {
            handler { (req: Request) =>
              handle(req).catchAllDefect { e =>
                ZIO
                  .logErrorCause("Application defect", Cause.die(e))
                  .as(Response.internalServerError("Something went wrong"))
              }
            }
          }

        for {
          response <- routes.transform(logDefects).runZIO(Request.get(url"/ok"))
          entries  <- ZTestLogger.logOutput
        } yield assertTrue(
          response.status == Status.Ok,
          !entries.exists(_.message() == "Application defect"),
        )
      },
    ),
    test("Middleware is applied to not found handler") {
      val routes = Routes(Method.GET / "foo" -> handler(Response.ok))

      for {
        ref <- Ref.make(0)
        middleware = Middleware.runBefore(ref.update(_ + 1)) ++ Middleware.runAfter(ref.update(_ + 1))
        response <- (routes @@ middleware).run(Request.get(url"/bar"))
        cnt      <- ref.get
      } yield assertTrue(response.status == Status.NotFound, cnt == 2)
    },
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

        } yield assertTrue(extractStatus(response) == Status.InternalServerError, result.contains("hmm..."))
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
          extractStatus(response) == Status.InternalServerError,
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
          extractStatus(response) == Status.InternalServerError,
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
        } yield assertTrue(extractStatus(response) == Status.InternalServerError, bodyString == "error")
      },
      test("handleErrorCause should pass through responses in error channel of handled routes") {
        val route        = Method.GET / "endpoint" -> handler { (_: Request) => ZIO.fail(Response.ok) }
        val errorHandled = route.handleErrorCause(_ => Response.text("error").status(Status.InternalServerError))
        val request      = Request.get(URL.decode("/endpoint").toOption.get)
        errorHandled.toRoutes.runZIO(request).map(response => assertTrue(extractStatus(response) == Status.Ok))
      },
      test("handleErrorCauseZIO should handle defects") {
        val route        = Method.GET / "endpoint" -> handler { (_: Request) => ZIO.dieMessage("hmm...") }
        val errorHandled = route.handleErrorCauseZIO { _ =>
          ZIO.succeed(Response.text("error").status(Status.InternalServerError))
        }
        val request      = Request.get(URL.decode("/endpoint").toOption.get)
        for {
          response   <- errorHandled.toRoutes.runZIO(request)
          bodyString <- response.body.asString
        } yield assertTrue(extractStatus(response) == Status.InternalServerError, bodyString == "error")
      },
      test("handleErrorCauseZIO should pass through responses in error channel of handled routes") {
        val route        = Method.GET / "endpoint" -> handler { (_: Request) => ZIO.fail(Response.ok) }
        val errorHandled = route.handleErrorCauseZIO { _ =>
          ZIO.succeed(Response.text("error").status(Status.InternalServerError))
        }
        val request      = Request.get(URL.decode("/endpoint").toOption.get)
        errorHandled.toRoutes.runZIO(request).map(response => assertTrue(extractStatus(response) == Status.Ok))
      },
      test("handleErrorCauseZIO should not call function when route succeeds") {
        val route   = Method.GET / "endpoint" -> handler { (_: Request) => ZIO.attempt(Response.ok) }
        val request = Request.get(URL.decode("/endpoint").toOption.get)
        for {
          ref <- Ref.make(false)
          errorHandled = route.handleErrorCauseZIO { _ =>
            ref.set(true) *> ZIO.succeed(Response.text("error").status(Status.InternalServerError))
          }
          response <- errorHandled.toRoutes.runZIO(request)
          refValue <- ref.get
        } yield assertTrue(extractStatus(response) == Status.Ok, !refValue)
      },
      test("tapErrorZIO is not called when the route succeeds") {
        val route       = Method.GET / "endpoint" -> handler { (_: Request) => ZIO.attempt(Response.ok) }
        val errorTapped = route.tapErrorZIO(_ => ZIO.log("tapErrorZIO")).sandbox
        for {
          _      <- errorTapped(Request.get("/endpoint"))
          didLog <- ZTestLogger.logOutput.map(out => out.find(_.message() == "tapErrorZIO").isDefined)
        } yield assertTrue(!didLog)
      },
      test("tapErrorZIO is called when the route fails with an error") {
        val route       = Method.GET / "endpoint" -> handler { (_: Request) => ZIO.fail(new Exception("hm...")) }
        val errorTapped = route.tapErrorZIO(_ => ZIO.log("tapErrorZIO")).sandbox
        for {
          _      <- errorTapped(Request.get("/endpoint")).sandbox
          didLog <- ZTestLogger.logOutput.map(out => out.find(_.message() == "tapErrorZIO").isDefined)
        } yield assertTrue(didLog)
      },
      test("tapErrorZIO is not called when the route fails with a defect") {
        val route: Route[Any, Unit] = Method.GET / "endpoint" -> handler { (_: Request) =>
          ZIO.die(new Exception("hm..."))
        }
        val errorTapped             = route.tapErrorZIO(_ => ZIO.log("tapErrorZIO")).sandbox
        for {
          _      <- errorTapped(Request.get("/endpoint")).sandbox
          didLog <- ZTestLogger.logOutput.map(out => out.find(_.message() == "tapErrorZIO").isDefined)
        } yield assertTrue(!didLog)
      },
      test("tapErrorCauseZIO is not called when the route succeeds") {
        val route       = Method.GET / "endpoint" -> handler { (_: Request) => ZIO.attempt(Response.ok) }
        val causeTapped = route.tapErrorCauseZIO(_ => ZIO.log("tapErrorCauseZIO")).sandbox
        for {
          _      <- causeTapped(Request.get("/endpoint"))
          didLog <- ZTestLogger.logOutput.map(out => out.find(_.message() == "tapErrorCauseZIO").isDefined)
        } yield assertTrue(!didLog)
      },
      test("tapErrorCauseZIO is called when the route fails with an error") {
        val route       = Method.GET / "endpoint" -> handler { (_: Request) => ZIO.fail(new Exception("hm...")) }
        val causeTapped = route.tapErrorCauseZIO(_ => ZIO.log("tapErrorCauseZIO")).sandbox
        for {
          _      <- causeTapped(Request.get("/endpoint")).sandbox
          didLog <- ZTestLogger.logOutput.map(out => out.find(_.message() == "tapErrorCauseZIO").isDefined)
        } yield assertTrue(didLog)
      },
      test("tapErrorCauseZIO is called when the route fails with a defect") {
        val route: Route[Any, Unit] = Method.GET / "endpoint" -> handler { (_: Request) =>
          ZIO.die(new Exception("hm..."))
        }
        val causeTapped             = route.tapErrorCauseZIO(_ => ZIO.log("tapErrorCauseZIO")).sandbox
        for {
          _      <- causeTapped(Request.get("/endpoint")).sandbox
          didLog <- ZTestLogger.logOutput.map(out => out.find(_.message() == "tapErrorCauseZIO").isDefined)
        } yield assertTrue(didLog)
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
    test("Handled#toHandler should not suspend") {
      val request = Request(headers = Headers.empty, method = Method.GET)
      val ok      = (Method.GET / "foo" -> handler(Response.ok)).toHandler

      assertTrue(Exit.succeed(Response.ok) == ok(request))
    },
  )
}
