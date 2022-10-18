package zio.http.api

import zio.ZIO
import zio.http.Response
import zio.http.middleware.Auth
import zio.http.middleware.Auth.Credentials
import zio.http.model.{Cookie, HeaderNames}

final case class MiddlewareSpec[MiddlewareIn, MiddlewareOut](
  middlewareIn: HttpCodec[CodecType.Header with CodecType.Query, MiddlewareIn],
  middlewareOut: HttpCodec[CodecType.Header, MiddlewareOut],
) { self =>
  def ++[MiddlewareIn2, MiddlewareOut2](that: MiddlewareSpec[MiddlewareIn2, MiddlewareOut2])(implicit
    inCombiner: Combiner[MiddlewareIn, MiddlewareIn2],
    outCombiner: Combiner[MiddlewareOut, MiddlewareOut2],
  ): MiddlewareSpec[inCombiner.Out, outCombiner.Out] =
    MiddlewareSpec(self.middlewareIn ++ that.middlewareIn, self.middlewareOut ++ that.middlewareOut)

  def implement[R, E](f: MiddlewareIn => ZIO[R, E, MiddlewareOut]): Middleware[R, E, MiddlewareIn, MiddlewareOut] =
    Middleware.HandlerZIO[R, E, MiddlewareIn, MiddlewareOut](self, f)

  def mapIn[MiddlewareIn2](
    f: HttpCodec[CodecType.Header with CodecType.Query, MiddlewareIn] => HttpCodec[
      CodecType.Header with CodecType.Query,
      MiddlewareIn2,
    ],
  ): MiddlewareSpec[MiddlewareIn2, MiddlewareOut] =
    copy(middlewareIn = f(middlewareIn))

  def mapOut[MiddlewareOut2](
    f: HttpCodec[CodecType.Header, MiddlewareOut] => HttpCodec[
      CodecType.Header,
      MiddlewareOut2,
    ],
  ): MiddlewareSpec[MiddlewareIn, MiddlewareOut2] =
    copy(middlewareOut = f(middlewareOut))

  def mapBoth[MiddlewareIn2, MiddlewareOut2](
    f: HttpCodec[CodecType.Header with CodecType.Query, MiddlewareIn] => HttpCodec[
      CodecType.Header with CodecType.Query,
      MiddlewareIn2,
    ],
    g: HttpCodec[CodecType.Header, MiddlewareOut] => HttpCodec[
      CodecType.Header,
      MiddlewareOut2,
    ],
  ): MiddlewareSpec[MiddlewareIn2, MiddlewareOut2] =
    mapIn(f).mapOut(g)

}

object MiddlewareSpec {

  def none: MiddlewareSpec[Unit, Unit] =
    MiddlewareSpec(HttpCodec.empty, HttpCodec.empty)

  def addCookie: MiddlewareSpec[Unit, Cookie[Response]] =
    MiddlewareSpec(
      HttpCodec.empty,
      HeaderCodec.cookie.transformOrFail(
        str => Cookie.decode[Response](str).left.map(_.getMessage),
        _.encode(false).left.map(_.getMessage),
      ),
    )

  /**
   * Add specified header to the response
   */
  def addHeader(key: String, value: String): MiddlewareSpec[Unit, Unit] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.header(key, TextCodec.constant(value)))

  def addCorrelationId: MiddlewareSpec[Unit, String] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.header("-x-correlation-id", TextCodec.string))

  def auth: MiddlewareSpec[Auth.Credentials, Unit] =
    requireHeader(HeaderNames.wwwAuthenticate.toString)
      .mapIn(
        _.transformOrFailLeft(
          s => decodeHttpBasic(s).fold(Left("Failed to decode headers"): Either[String, Credentials])(Right(_)),
          c => s"${c.uname}:${c.upassword}",
        ),
      )

  def requireHeader(name: String): MiddlewareSpec[String, Unit] =
    MiddlewareSpec(HeaderCodec.header(name, TextCodec.string), HttpCodec.empty)

  private def decodeHttpBasic(encoded: String): Option[Credentials] = {
    val colonIndex = encoded.indexOf(":")
    if (colonIndex == -1)
      None
    else {
      val username = encoded.substring(0, colonIndex)
      val password =
        if (colonIndex == encoded.length - 1)
          ""
        else
          encoded.substring(colonIndex + 1)
      Some(Credentials(username, password))
    }
  }
}
