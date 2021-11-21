package example

import zhttp.http._
import zhttp.http.middleware.HttpMiddleware.{addCookie, csrf}
import zio.UIO

object CSRF extends App {
  val app: HttpApp[Any, Nothing] = HttpApp.collectM {
    case Method.GET -> !! / "safeEndpoint"    =>
      UIO(Response.ok)
    case Method.POST -> !! / "unsafeEndpoint" => UIO(Response.ok)
  }
  app @@ csrf("x-token", "token").when((method, _, _) => method == Method.POST) @@
    addCookie(Cookie("token", "randomToken")).when((method, _, _) => method == Method.GET)

}
