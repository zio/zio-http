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

import scala.collection.mutable
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

  private val failingRoute: Route[Session] =
    Method.GET / "fail" -> handler((_: Session) => Response.internalServerError)

  private def rotated(
    validate: String => Option[Session],
    create: Session => String,
  ): Routes[Any] =
    Routes(securedRoute) @@ Middleware.rotateCookie[Session](CookieName, validate, create, Some(300L))

  private def rotatedFailing(
    validate: String => Option[Session],
    create: Session => String,
  ): Routes[Any] =
    Routes(failingRoute) @@ Middleware.rotateCookie[Session](CookieName, validate, create, Some(300L))

  private def secureAttrs(name: String, value: String, maxAge: Option[Long]): ResponseCookie =
    ResponseCookie(
      name,
      value,
      path = Some(Path.root),
      maxAge = maxAge,
      isSecure = true,
      isHttpOnly = true,
      sameSite = Some(SameSite.Strict),
    )

  private def asResponse(result: Response | Halt): Response =
    result match {
      case response: Response => response
      case halt: Halt         => halt.response
    }

  private def cleared: Response =
    Response.unauthorized.addCookie(secureAttrs(CookieName, "", Some(0L)))

  def spec = suite("Middleware.rotateCookie")(
    test("valid old cookie rotates: new value set, old invalidated via user store") {
      val store       = mutable.Map("old-1" -> Session("alice"))
      val invalidated = mutable.ListBuffer.empty[String]
      val validate    = (token: String) => store.get(token)
      val create      = (_: Session) => {
        store.remove("old-1")
        invalidated += "old-1"
        "new-1"
      }
      val request     = Request.get(URL.root / "secure").addCookie(RequestCookie(CookieName, "old-1"))
      val response    = asResponse(dispatch(rotated(validate, create), request))
      val cookie      = response.cookies.find(_.name == CookieName)
      assertTrue(
        response == Response.text("hello alice").addCookie(secureAttrs(CookieName, "new-1", Some(300L))),
        cookie.map(_.value).contains("new-1"),
        cookie.flatMap(_.maxAge).contains(300L),
        cookie.flatMap(_.path).contains(Path.root),
        cookie.exists(_.isSecure),
        cookie.exists(_.isHttpOnly),
        cookie.flatMap(_.sameSite).contains(SameSite.Strict),
        invalidated.contains("old-1"),
        store.get("old-1").isEmpty,
      )
    },
    test("invalid old cookie is cleared and yields 401") {
      val store    = Map("good-9" -> Session("bob"))
      val validate = (token: String) => store.get(token)
      val create   = (_: Session) => "fresh-1"
      val request  = Request.get(URL.root / "secure").addCookie(RequestCookie(CookieName, "bogus"))
      assertTrue(dispatch(rotated(validate, create), request) == cleared)
    },
    test("missing cookie yields cleared 401 without touching the store") {
      var touched  = false
      val validate = (_: String) => {
        touched = true
        Option.empty[Session]
      }
      val create   = (_: Session) => {
        touched = true
        "fresh-1"
      }
      val result   = dispatch(rotated(validate, create), Request.get(URL.root / "secure"))
      assertTrue(
        result == cleared,
        !touched,
      )
    },
    test("throwing validate yields cleared cookie plus 401, no leak") {
      val validate = (_: String) => throw new RuntimeException("boom-secret")
      val create   = (_: Session) => "fresh-1"
      val request  = Request.get(URL.root / "secure").addCookie(RequestCookie(CookieName, "old-1"))
      assertTrue(dispatch(rotated(validate, create), request) == cleared)
    },
    test("downstream 500 passes through unchanged without rotation") {
      val validate = (_: String) => Some(Session("alice"))
      val create   = (_: Session) => "fresh-1"
      val request  = Request.get(URL.root / "fail").addCookie(RequestCookie(CookieName, "old-1"))
      val result   = dispatch(rotatedFailing(validate, create), request)
      assertTrue(
        result == Response.internalServerError,
        asResponse(result).cookies.isEmpty,
      )
    },
    test("throwing create yields cleared cookie plus 401, no leak") {
      val validate = (_: String) => Some(Session("alice"))
      val create   = (_: Session) => throw new RuntimeException("mint-boom")
      val request  = Request.get(URL.root / "secure").addCookie(RequestCookie(CookieName, "old-1"))
      assertTrue(dispatch(rotated(validate, create), request) == cleared)
    },
  )
}
