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
          response <- errorHandled.toHttpApp.runZIO(request)
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
          response      <- errorHandled.toHttpApp.runZIO(request)
          result        <- p.await.catchAllCause(c => ZIO.succeed(c.prettyPrint))
          resultWarning <- ZIO.fromOption(response.headers.get(Header.Warning).map(_.text))

        } yield assertTrue(
          extractStatus(response) == Status.InternalServerError,
          resultWarning == "error accessing /endpoint",
          result.contains("hmm..."),
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
          response      <- errorHandled.toHttpApp.runZIO(request)
          resultWarning <- ZIO.fromOption(response.headers.get(Header.Warning).map(_.text))

        } yield assertTrue(
          extractStatus(response) == Status.InternalServerError,
          resultWarning == "error accessing /endpoint: hmm...",
        )
      },
      test("handleErrorCause should handle defects") {
        val route        = Method.GET / "endpoint" -> handler { (_: Request) => ZIO.dieMessage("hmm...") }
        val errorHandled = route.handleErrorCause(_ => Response.text("error").status(Status.InternalServerError))
        val request      = Request.get(URL.decode("/endpoint").toOption.get)
        for {
          response   <- errorHandled.toHttpApp.runZIO(request)
          bodyString <- response.body.asString
        } yield assertTrue(extractStatus(response) == Status.InternalServerError, bodyString == "error")
      },
      test("handleErrorCauseZIO should handle defects") {
        val route        = Method.GET / "endpoint" -> handler { (_: Request) => ZIO.dieMessage("hmm...") }
        val errorHandled =
          route.handleErrorCauseZIO(_ => ZIO.succeed(Response.text("error").status(Status.InternalServerError)))
        val request      = Request.get(URL.decode("/endpoint").toOption.get)
        for {
          response   <- errorHandled.toHttpApp.runZIO(request)
          bodyString <- response.body.asString
        } yield assertTrue(extractStatus(response) == Status.InternalServerError, bodyString == "error")
      },
      test("handleErrorRequestCause should handle defects") {
        val route        = Method.GET / "endpoint" -> handler { (_: Request) => ZIO.dieMessage("hmm...") }
        val errorHandled =
          route.handleErrorRequestCause((_, _) => Response.text("error").status(Status.InternalServerError))
        val request      = Request.get(URL.decode("/endpoint").toOption.get)
        for {
          response   <- errorHandled.toHttpApp.runZIO(request)
          bodyString <- response.body.asString
        } yield assertTrue(extractStatus(response) == Status.InternalServerError, bodyString == "error")
      },
      test("handleErrorRequestCauseZIO should handle defects") {
        val route        = Method.GET / "endpoint" -> handler { (_: Request) => ZIO.dieMessage("hmm...") }
        val errorHandled = route.handleErrorRequestCauseZIO((_, _) =>
          ZIO.succeed(Response.text("error").status(Status.InternalServerError)),
        )
        val request      = Request.get(URL.decode("/endpoint").toOption.get)
        for {
          response   <- errorHandled.toHttpApp.runZIO(request)
          bodyString <- response.body.asString
        } yield assertTrue(extractStatus(response) == Status.InternalServerError, bodyString == "error")
      },
    ),
  )
}
