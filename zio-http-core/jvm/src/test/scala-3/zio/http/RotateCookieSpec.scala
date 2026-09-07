/*
 * Copyright 2026 the ZIO HTTP contributors.
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
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern.MethodSyntax
import zio.blocks.scope.Scope
import zio.http.RouteBinding._
import zio.test._

object RotateCookieSpec extends ZIOSpecDefault {

  final case class Session(user: String)

  private val CookieName = "session"

  private def dispatch(routes: Routes[Any], request: Request): Response | Halt = {
    val route     = routes.routes.head
    val extracted = route.pattern
      .decode(request.method, request.url.path)
      .getOrElse(throw new RuntimeException("path did not match"))
    route.handler.handle(request, Context.empty, extracted, Scope.global)
  }

  private val securedRoute: Route[Session] =
    Method.GET / "secure" -> handler((session: Session) => Response.text(s"hello ${session.user}"))

  private def rotated(
    validate: String => ZIO[Any, Nothing, Option[Session]],
    create: Session => ZIO[Any, Nothing, String],
  ): Routes[Any] =
    Routes(securedRoute) @@ Middleware.rotateCookie[Session](CookieName, validate, create, Some(300L))

  private def asResponse(result: Response | Halt): Response =
    result match {
      case response: Response => response
      case halt: Halt         => halt.response
    }

  private def cleared: Response =
    Response.unauthorized.addCookie(ResponseCookie(CookieName, "", maxAge = Some(0L)))

  def spec = suite("Middleware.rotateCookie")(
    test("valid old cookie rotates: new value set, old invalidated via user store") {
      for {
        store       <- Ref.make(Map("old-1" -> Session("alice")))
        invalidated <- Ref.make(List.empty[String])
        validate     = (token: String) => store.get.map(_.get(token))
        create       = (_: Session) =>
          store.update(_ - "old-1") *> invalidated.update("old-1" :: _) *> ZIO.succeed("new-1")
        request      = Request.get(URL.root / "secure").addCookie(RequestCookie(CookieName, "old-1"))
        result      <- ZIO.attempt(dispatch(rotated(validate, create), request))
        response     = asResponse(result)
        cookie       = response.cookies.find(_.name == CookieName)
        gone        <- invalidated.get
        remaining   <- store.get
      } yield assertTrue(
        response == Response.text("hello alice").addCookie(ResponseCookie(CookieName, "new-1", maxAge = Some(300L))),
        cookie.map(_.value).contains("new-1"),
        cookie.flatMap(_.maxAge).contains(300L),
        gone.contains("old-1"),
        remaining.get("old-1").isEmpty,
      )
    },
    test("invalid old cookie is cleared and yields 401") {
      for {
        store    <- Ref.make(Map("good-9" -> Session("bob")))
        validate  = (token: String) => store.get.map(_.get(token))
        create    = (_: Session) => ZIO.succeed("fresh-1")
        request   = Request.get(URL.root / "secure").addCookie(RequestCookie(CookieName, "bogus"))
        result   <- ZIO.attempt(dispatch(rotated(validate, create), request))
      } yield assertTrue(result == cleared)
    },
    test("missing cookie yields cleared 401 without touching the store") {
      for {
        touched    <- Ref.make(false)
        validate    = (_: String) => touched.set(true).as(Option.empty[Session])
        create      = (_: Session) => touched.set(true).as("fresh-1")
        result     <- ZIO.attempt(dispatch(rotated(validate, create), Request.get(URL.root / "secure")))
        wasTouched <- touched.get
      } yield assertTrue(
        result == cleared,
        !wasTouched,
      )
    },
    test("validate defect is swallowed: cleared cookie plus 401, no leak") {
      val validate = (_: String) => ZIO.die(new RuntimeException("boom-secret"))
      val create   = (_: Session) => ZIO.succeed("fresh-1")
      val request  = Request.get(URL.root / "secure").addCookie(RequestCookie(CookieName, "old-1"))
      val result   = dispatch(rotated(validate, create), request)
      assertTrue(result == cleared)
    },
  )
}
