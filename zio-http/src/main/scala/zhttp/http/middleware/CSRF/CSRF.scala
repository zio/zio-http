package zhttp.http.middleware.CSRF

import java.time.Instant

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http._
import zhttp.http.middleware.{HttpMiddleware, Patch}
import zio.UIO

final class CSRF(headerName: String, cookieSetting: CSRF.CookieSetting, tokenGen: () => UIO[String]) {
  private def getCSRFCookies(headers: List[Header]): Option[String] = headers
    .find(p => p.name == HttpHeaderNames.COOKIE)
    .flatMap(a => Cookie.decode(a.value.toString).toOption)
    .find(p => p.name == cookieSetting.cookieName)
    .map(_.content)

  def generateToken: HttpMiddleware[Any, Nothing] = HttpMiddleware.makeM((_, _, headers) => {
    for {
      cookie <- UIO(getCSRFCookies(headers))
      token  <- cookie match {
        case Some(_) => UIO.none
        case None    => tokenGen().map(Some(_))
      }
    } yield token
  }) {
    case (_, _, token) => {
      token match {
        case Some(t) =>
          UIO(
            Patch.addHeaders(
              List(
                Header(
                  "Set-Cookie",
                  Cookie(
                    name = cookieSetting.cookieName,
                    content = t,
                    isHttpOnly = cookieSetting.httpOnly,
                    domain = cookieSetting.domain,
                    path = cookieSetting.path.map(Path(_)),
                    sameSite = cookieSetting.sameSite,
                    expires = cookieSetting.expires,
                  ).encode,
                ),
              ),
            ),
          )
        case None    => UIO(Patch.empty)
      }
    }
  }
  def checkToken: HttpMiddleware[Any, Nothing]    = HttpMiddleware.make((_, _, headers) => {
    val headerVal = headers.find(p => p.name == headerName).map(_.value.toString)
    val cookieVal = getCSRFCookies(headers)
    (headerVal, cookieVal) match {
      case (Some(hv), Some(cv)) => hv == cv
      case _                    => false
    }
  }) {
    case (_, _, verified) => {
      if (verified) {
        Patch.empty
      } else {
        Patch.setStatus(Status.UNAUTHORIZED)
      }
    }
  }
}
object CSRF                                                                                          {
  case class CookieSetting(
    cookieName: String,
    secure: Boolean = false,
    httpOnly: Boolean = false,
    domain: Option[String] = None,
    path: Option[String] = None,
    sameSite: Option[Cookie.SameSite] = Some(Cookie.SameSite.Lax),
    expires: Option[Instant] = None,
  )

  def apply(headerName: String, cookieSetting: CookieSetting, tokenGen: () => UIO[String]): CSRF =
    new CSRF(headerName, cookieSetting, tokenGen)

}
