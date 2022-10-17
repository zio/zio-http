package zio.http.api

import zio.ZIO
import zio.http.middleware.Auth
import zio.http.middleware.Auth.Credentials
import zio.http.model.HeaderNames

import java.util.Base64

final case class MiddlewareSpec[MiddlewareIn, MiddlewareOut](
  middlewareIn: HttpCodec[CodecType.Header with CodecType.Query, MiddlewareIn],
  middlewareOut: HttpCodec[CodecType.Header with CodecType.Query, MiddlewareOut],
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
    f: HttpCodec[CodecType.Header with CodecType.Query, MiddlewareOut] => HttpCodec[
      CodecType.Header with CodecType.Query,
      MiddlewareOut2,
    ],
  ): MiddlewareSpec[MiddlewareIn, MiddlewareOut2] =
    copy(middlewareOut = f(middlewareOut))

  def mapBoth[MiddlewareIn2, MiddlewareOut2](
    f: HttpCodec[CodecType.Header with CodecType.Query, MiddlewareIn] => HttpCodec[
      CodecType.Header with CodecType.Query,
      MiddlewareIn2,
    ],
    g: HttpCodec[CodecType.Header with CodecType.Query, MiddlewareOut] => HttpCodec[
      CodecType.Header with CodecType.Query,
      MiddlewareOut2,
    ],
  ): MiddlewareSpec[MiddlewareIn2, MiddlewareOut2] =
    mapIn(f).mapOut(g)

}

object MiddlewareSpec {

  def none: MiddlewareSpec[Unit, Unit] =
    MiddlewareSpec(HttpCodec.empty, HttpCodec.empty)

  /**
   * Add specified header to the response
   */
  def addHeader(key: String, value: String): MiddlewareSpec[Unit, Unit] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.header(key, TextCodec.constant(value)))

  def auth: MiddlewareSpec[Auth.Credentials, Unit] =
    requireHeader(HeaderNames.wwwAuthenticate.toString)
      .mapIn(
        _.transformOrFailLeft(
          s => {
            println("going to decoded")
            decodeHttpBasic(s).fold(Left("Failed to decode headers"): Either[String, Credentials])(Right(_))
          },
          c => s"${c.uname}:${c.upassword}",
        ),
      )

  def requireHeader(name: String): MiddlewareSpec[String, Unit] =
    MiddlewareSpec(HeaderCodec.header(name, TextCodec.string), HttpCodec.empty)

  private def decodeHttpBasic(encoded: String): Option[Credentials] = {
    val decoded    = new String(Base64.getDecoder.decode(encoded))
    val colonIndex = decoded.indexOf(":")
    if (colonIndex == -1)
      None
    else {
      val username = decoded.substring(0, colonIndex)
      val password =
        if (colonIndex == decoded.length - 1)
          ""
        else
          decoded.substring(colonIndex + 1)
      Some(Credentials(username, password))
    }
  }
}
