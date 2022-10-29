package zio.http.api

import zio.ZIO
import zio.http.api.internal.TextCodec
import zio.http.middleware.Auth
import zio.http.middleware.Auth.Credentials
import zio.http.model.headers.values._
import zio.http.model.{Cookie, HeaderNames}
import zio.http.{Request, Response}

final case class MiddlewareSpec[MiddlewareIn, MiddlewareOut](
  middlewareIn: HttpCodec[CodecType.Header with CodecType.Query, MiddlewareIn],
  middlewareOut: HttpCodec[CodecType.Header, MiddlewareOut],
) { self =>
  def ++[MiddlewareIn2, MiddlewareOut2](that: MiddlewareSpec[MiddlewareIn2, MiddlewareOut2])(implicit
    inCombiner: Combiner[MiddlewareIn, MiddlewareIn2],
    outCombiner: Combiner[MiddlewareOut, MiddlewareOut2],
  ): MiddlewareSpec[inCombiner.Out, outCombiner.Out] =
    MiddlewareSpec(self.middlewareIn ++ that.middlewareIn, self.middlewareOut ++ that.middlewareOut)

  def implement[R, S](
    incoming: MiddlewareIn => ZIO[R, Nothing, Middleware.Control[S]],
  ): Middleware.Interceptor2[S, R, MiddlewareIn, MiddlewareOut] =
    Middleware.interceptZIO(self)(incoming)

  def implementIncoming[R](
    incoming: MiddlewareIn => ZIO[R, Nothing, MiddlewareOut],
  ): Middleware[R, MiddlewareIn, MiddlewareOut] =
    Middleware.fromFunctionZIO(self)(incoming)

  def implementIncomingControl[R](
    incoming: MiddlewareIn => ZIO[R, Nothing, Middleware.Control[MiddlewareOut]],
  ): Middleware[R, MiddlewareIn, MiddlewareOut] =
    implement[R, MiddlewareOut](in => incoming(in))((out, _) => ZIO.succeedNow(out))

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

  def optional: MiddlewareSpec[Option[MiddlewareIn], Option[MiddlewareOut]] =
    self.optionalIn.optionalOut

  def optionalIn: MiddlewareSpec[Option[MiddlewareIn], MiddlewareOut] =
    self.mapIn(_.optional)

  def optionalOut: MiddlewareSpec[MiddlewareIn, Option[MiddlewareOut]] =
    self.mapOut(_.optional)

}

object MiddlewareSpec {

  final case class CsrfValidate(cookieOption: Option[Cookie[Request]], tokenValue: Option[String])

  def none: MiddlewareSpec[Unit, Unit] =
    MiddlewareSpec(HttpCodec.empty, HttpCodec.empty)

  def cookieOption(cookieName: String): MiddlewareSpec[Option[Cookie[Request]], Unit] =
    requireHeader(HeaderNames.cookie.toString()).optionalIn
      .mapIn(
        _.transformOrFail(
          {
            case Some(cookieList) => readCookie(cookieList, cookieName)
            case None             => Right(None)
          },
          {
            case Some(cookie) => writeCookie(cookie).map(Some(_))
            case None         => Right(None)
          },
        ),
      )

  def cookie(cookieName: String): MiddlewareSpec[Cookie[Request], Unit] = {
    cookieOption(cookieName).mapIn(
      _.transformOrFailLeft(
        {
          case Some(cookie) => Right(cookie)
          case None         => Left(s"Cookie ${cookieName} not found")
        },
        value => Some(value),
      ),
    )
  }

  def csrfValidate(tokenName: String): MiddlewareSpec[CsrfValidate, Unit] = {
    val cookie: MiddlewareSpec[Option[Cookie[Request]], Unit] =
      MiddlewareSpec.cookieOption(tokenName)

    val tokenHeader =
      MiddlewareSpec.requireHeader(tokenName)

    (cookie ++ tokenHeader.mapIn(_.optional)).mapIn(
      _.transform(
        { case (a, b) =>
          CsrfValidate(a, b)
        },
        value => (value.cookieOption, value.tokenValue),
      ),
    )
  }

  def withContentBase: MiddlewareSpec[Unit, ContentBase] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.contentBase)

  def withContentDisposition: MiddlewareSpec[Unit, ContentDisposition] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.contentDisposition)

  def withContentEncoding: MiddlewareSpec[Unit, ContentEncoding]                 =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.contentEncoding)
  def withContentLanguage: MiddlewareSpec[Unit, ContentLanguage]                 =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.contentLanguage)
  def withContentLength: MiddlewareSpec[Unit, ContentLength]                     =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.contentLength)
  def withContentLocation: MiddlewareSpec[Unit, ContentLocation]                 =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.contentLocation)
  def withContentMd5: MiddlewareSpec[Unit, ContentMd5]                           =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.contentMd5)
  def withContentRange: MiddlewareSpec[Unit, ContentRange]                       =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.contentRange)
  def withContentSecurityPolicy: MiddlewareSpec[Unit, ContentSecurityPolicy]     =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.contentSecurityPolicy)
  def withContentTransferEncoding: MiddlewareSpec[Unit, ContentTransferEncoding] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.contentTransferEncoding)
  def withContentType: MiddlewareSpec[Unit, ContentType]                         =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.contentType)

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

  def addCorrelationId: MiddlewareSpec[Unit, String] = {

    MiddlewareSpec(HttpCodec.empty, HeaderCodec.header("-x-correlation-id", TextCodec.string))
  }

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

  private[api] def decodeHttpBasic(encoded: String): Option[Credentials] = {
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

  private[api] def readCookie(
    cookieList: String,
    cookieName: String,
  ): Either[String, Option[Cookie[Request]]] =
    Cookie
      .decode[Request](cookieList)
      .map(list => list.find(_.name == cookieName))
      .left
      .map(_.getMessage)

  private[api] def writeCookie(cookieRequest: Cookie[Request]): Either[String, String] =
    cookieRequest
      .encode(validate = false)
      .left
      .map(_.getMessage)

}
