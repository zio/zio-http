package zhttp.http.middleware

import io.netty.handler.codec.http.HttpHeaderNames
import pdi.jwt._
import pdi.jwt.algorithms.JwtHmacAlgorithm
import zhttp.http.HeaderExtension.BasicSchemeName
import zhttp.http.{Header, HeaderExtension, HttpApp, HttpError}
import zio.ZIO

/**
 * Authentication Middlewares for HttpApp.
 */
sealed trait AuthMiddleware[-R, +E] { self =>
  def apply[R1 <: R, E1 >: E](app: HttpApp[R1, E1]): HttpApp[R1, E1] = AuthMiddleware.execute(self, app)

  def ++[R1 <: R, E1 >: E](other: AuthMiddleware[R1, E1]): AuthMiddleware[R1, E1] =
    self combine other

  def combine[R1 <: R, E1 >: E](other: AuthMiddleware[R1, E1]): AuthMiddleware[R1, E1] =
    AuthMiddleware.Combine(self, other)

}
object AuthMiddleware {
  private final case class AuthFunction[R, E](f: List[Header] => ZIO[R, E, Boolean], h: List[Header])
      extends AuthMiddleware[R, E]
  private final case class Combine[R, E](self: AuthMiddleware[R, E], other: AuthMiddleware[R, E])
      extends AuthMiddleware[R, E]

  /**
   * creates a middleware that check the content of X-ACCESS-TOKEN header and try to decode a JwtClaim
   */
  def jwt(secretKey: String, algo: Seq[JwtHmacAlgorithm] = Seq(JwtAlgorithm.HS512)): AuthMiddleware[Any, Nothing] =
    AuthFunction(
      { h =>
        HeadersHolder(h)
          .getHeader("X-ACCESS-TOKEN")
          .flatMap(header => Jwt.decode(header.value.toString, secretKey, algo).toOption)
          .fold(ZIO.succeed(false))(_ => ZIO.succeed(true))
      },
      List.empty[Header],
    )

  /**
   * creates a middleware for basic authentication
   */
  def basicAuth[R, E](f: (String, String) => ZIO[R, E, Boolean]): AuthMiddleware[R, E] = AuthFunction(
    { h =>
      HeadersHolder(h).getBasicAuthorizationCredentials match {
        case Some((username, password)) => f(username, password)
        case None                       => ZIO.succeed(false)
      }
    },
    List(Header(HttpHeaderNames.WWW_AUTHENTICATE, BasicSchemeName)),
  )

  /**
   * creates a generic authentication middleware
   */
  def authFunction[R, E](
    f: List[Header] => ZIO[R, E, Boolean],
    h: List[Header] = List.empty[Header],
  ): AuthMiddleware[R, E] = AuthFunction(f, h)

  /**
   * Applies the middleware on an HttpApp
   */
  private[zhttp] def execute[R, E](mid: AuthMiddleware[R, E], app: HttpApp[R, E]): HttpApp[R, E] = mid match {
    case AuthFunction(f, h)   =>
      HttpApp.fromFunctionM { req =>
        for {
          bool <- f(req.headers)
          res  <-
            if (bool) ZIO.succeed(app)
            else ZIO.succeed(HttpApp.response(HttpError.Unauthorized().toResponse.addHeaders(h)))
        } yield res
      }
    case Combine(self, other) => other(self(app))
  }

  final case class HeadersHolder(headers: List[Header]) extends HeaderExtension[HeadersHolder] { self =>
    override def addHeaders(headers: List[Header]): HeadersHolder =
      HeadersHolder(self.headers ++ headers)

    override def removeHeaders(headers: List[String]): HeadersHolder =
      HeadersHolder(self.headers.filterNot(h => headers.contains(h.name)))
  }

}
