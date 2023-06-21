package zio.http

import zio._

/**
 * A [[zio.http.HttpApp]] is a ready-to-execute HTTP application, which handles
 * all requests, and always produces valid responses, translating application
 * errors into HTTP errors.
 */
final case class HttpApp2[-Env](
  requestHandler: Handler[Env, Response, Request, Response],
  fatalHandler: Cause[Any] => ZIO[Env, Option[Nothing], Response],
) { self =>
  def ++[Env1 <: Env](that: HttpApp2[Env1]): HttpApp2[Env1] =
    HttpApp2(
      requestHandler.catchSome {
        case response if response.status == Status.NotFound => that.requestHandler
      },
      cause => self.fatalHandler(cause).orElse(that.fatalHandler(cause)),
    )
}
