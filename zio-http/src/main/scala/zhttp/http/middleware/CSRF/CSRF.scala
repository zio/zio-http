package zhttp.http.middleware.CSRF

import java.time.Instant

import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.util.AsciiString
import io.netty.util.AsciiString.toLowerCase
import zhttp.http._
import zhttp.http.middleware.{HttpMiddleware, Patch}
import zio.UIO

final class CSRF(headerName: String, cookieSetting: CSRF.CookieSetting, tokenGen: () => UIO[String]) {
  private def equalsIgnoreCase(a: Char, b: Char) = a == b || toLowerCase(a) == toLowerCase(b)

  // todo: Figure out a way to use it from HeaderExtension
  def contentEqualsIgnoreCase(a: CharSequence, b: CharSequence): Boolean = {
    if (a == b)
      true
    else if (a.length() != b.length())
      false
    else if (a.isInstanceOf[AsciiString]) {
      a.asInstanceOf[AsciiString].contentEqualsIgnoreCase(b)
    } else if (b.isInstanceOf[AsciiString]) {
      b.asInstanceOf[AsciiString].contentEqualsIgnoreCase(a)
    } else {
      (0 until a.length()).forall(i => equalsIgnoreCase(a.charAt(i), b.charAt(i)))
    }
  }

  private def getCSRFCookies(headers: List[Header]): Option[String] = {
    headers
      .find(p => contentEqualsIgnoreCase(p.name, HttpHeaderNames.COOKIE))
      .flatMap(a => Cookie.decode(a.value.toString).toOption)
      .find(p => p.name == cookieSetting.cookieName)
      .map(_.content)
  }

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
    sameSite: Option[Cookie.SameSite] = None,
    expires: Option[Instant] = None,
  )

  def apply(headerName: String, cookieSetting: CookieSetting, tokenGen: () => UIO[String]): CSRF =
    new CSRF(headerName, cookieSetting, tokenGen)

}
