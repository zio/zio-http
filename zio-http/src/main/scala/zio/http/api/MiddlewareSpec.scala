package zio.http.api

import zio.ZIO
import zio.http.api.internal.TextCodec
import zio.http.middleware.Auth
import zio.http.middleware.Auth.Credentials
import zio.http.model.Headers.BasicSchemeName
import zio.http.model.headers.values._
import zio.http.model.{Cookie, HTTP_CHARSET, HeaderNames, Headers}
import zio.http.{Request, Response}

import java.util.Base64
import zio.http.model.Method

final case class MiddlewareSpec[In, Err, Out](
  input: HttpCodec[CodecType.Header with CodecType.Query with CodecType.Method, In],
  output: HttpCodec[CodecType.Header, Out],
  error: HttpCodec[CodecType.ResponseType, Err],
  doc: Doc = Doc.empty,
) { self =>
  def ++[MiddlewareIn2, Err2, MiddlewareOut2](that: MiddlewareSpec[MiddlewareIn2, Err2, MiddlewareOut2])(implicit
    inCombiner: Combiner[In, MiddlewareIn2],
    outCombiner: Combiner[Out, MiddlewareOut2],
    errAlternator: Alternator[Err, Err2],
  ): MiddlewareSpec[inCombiner.Out, errAlternator.Out, outCombiner.Out] =
    MiddlewareSpec(self.input ++ that.input, self.output ++ that.output, self.error | that.error, doc)

  // def implement[R, S](
  //   incoming: In => ZIO[R, Err, S],
  // ): Middleware.Interceptor2[S, R, In, Err, Out] =
  //   Middleware.interceptZIO(self)(incoming)

  // def implementIncoming[R](
  //   incoming: In => ZIO[R, Err, Out],
  // ): Middleware[R, In, Err, Out] =
  //   implement(in => incoming(in))((out, _) => ZIO.succeedNow(out))

  def mapIn[MiddlewareIn2](
    f: HttpCodec[CodecType.Header with CodecType.Query with CodecType.Method, In] => HttpCodec[
      CodecType.Header with CodecType.Query with CodecType.Method,
      MiddlewareIn2,
    ],
  ): MiddlewareSpec[MiddlewareIn2, Err, Out] =
    copy(input = f(input))

  def mapOut[MiddlewareOut2](
    f: HttpCodec[CodecType.Header, Out] => HttpCodec[
      CodecType.Header,
      MiddlewareOut2,
    ],
  ): MiddlewareSpec[In, Err, MiddlewareOut2] =
    copy(output = f(output))

  def mapBoth[MiddlewareIn2, MiddlewareOut2](
    f: HttpCodec[CodecType.Header with CodecType.Query with CodecType.Method, In] => HttpCodec[
      CodecType.Header with CodecType.Query with CodecType.Method,
      MiddlewareIn2,
    ],
    g: HttpCodec[CodecType.Header, Out] => HttpCodec[
      CodecType.Header,
      MiddlewareOut2,
    ],
  ): MiddlewareSpec[MiddlewareIn2, Err, MiddlewareOut2] =
    mapIn(f).mapOut(g)

  def optional: MiddlewareSpec[Option[In], Err, Option[Out]] =
    self.optionalIn.optionalOut

  def optionalIn: MiddlewareSpec[Option[In], Err, Out] =
    self.mapIn(_.optional)

  def optionalOut: MiddlewareSpec[In, Err, Option[Out]] =
    self.mapOut(_.optional)

}

object MiddlewareSpec {

  final case class CsrfValidate(cookieOption: Option[Cookie[Request]], tokenValue: Option[String])

  val none: MiddlewareSpec[Unit, Unused, Unit] =
    MiddlewareSpec(HttpCodec.empty, HttpCodec.empty, HttpCodec.unused)

  def cookieOption(cookieName: String): MiddlewareSpec[Option[Cookie[Request]], Unused, Unit] =
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

  def cookie(cookieName: String): MiddlewareSpec[Cookie[Request], Unused, Unit] = {
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

  def csrfValidate(tokenName: String): MiddlewareSpec[CsrfValidate, Unit, Unit] = {
    val cookie: MiddlewareSpec[Option[Cookie[Request]], Unused, Unit] =
      MiddlewareSpec.cookieOption(tokenName)

    val tokenHeader =
      MiddlewareSpec.requireHeader(tokenName)

    val forbidden =
      MiddlewareSpec(HttpCodec.empty, HttpCodec.empty, StatusCodec.Forbidden)

    (cookie ++ tokenHeader.mapIn(_.optional) ++ forbidden).mapIn(
      _.transform(
        { case (a, b) =>
          CsrfValidate(a, b)
        },
        value => (value.cookieOption, value.tokenValue),
      ),
    )
  }

  val withContentBase: MiddlewareSpec[Unit, Unused, ContentBase] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.contentBase, HttpCodec.unused)

  val withContentDisposition: MiddlewareSpec[Unit, Unused, ContentDisposition] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.contentDisposition, HttpCodec.unused)

  val withContentEncoding: MiddlewareSpec[Unit, Unused, ContentEncoding]                 =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.contentEncoding, HttpCodec.unused)
  val withContentLanguage: MiddlewareSpec[Unit, Unused, ContentLanguage]                 =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.contentLanguage, HttpCodec.unused)
  def withContentLength: MiddlewareSpec[Unit, Unused, ContentLength]                     =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.contentLength, HttpCodec.unused)
  val withContentLocation: MiddlewareSpec[Unit, Unused, ContentLocation]                 =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.contentLocation, HttpCodec.unused)
  val withContentMd5: MiddlewareSpec[Unit, Unused, ContentMd5]                           =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.contentMd5, HttpCodec.unused)
  val withContentRange: MiddlewareSpec[Unit, Unused, ContentRange]                       =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.contentRange, HttpCodec.unused)
  def withContentSecurityPolicy: MiddlewareSpec[Unit, Unused, ContentSecurityPolicy]  =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.contentSecurityPolicy, HttpCodec.unused)
  val withContentTransferEncoding: MiddlewareSpec[Unit, Unused, ContentTransferEncoding] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.contentTransferEncoding, HttpCodec.unused)
  val withContentType: MiddlewareSpec[Unit, Unused, ContentType]                         =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.contentType, HttpCodec.unused)

  val addCookie: MiddlewareSpec[Unit, Unused, Cookie[Response]] =
    MiddlewareSpec(
      HttpCodec.empty,
      HeaderCodec.setCookie.transformOrFail(
        _ => Left("Cannot add cookie"),
        value => Right(ResponseCookie.CookieValue(value)),
      ),
      HttpCodec.unused,
    )

  /**
   * Add specified header to the response
   */
  def addHeader(key: String, value: String): MiddlewareSpec[Unit, Unused, Unit] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.header(key, TextCodec.constant(value)), HttpCodec.unused)

  def addHeader(header: Headers.Header): MiddlewareSpec[Unit, Unused, Unit] =
    addHeader(header.key.toString, header.value.toString)

  def addHeaders(headers: Headers): MiddlewareSpec[Unit, Unused, Unit] =
    headers.headersAsList.map(addHeader(_)).reduce(_ ++ _)

  val addCorrelationId: MiddlewareSpec[Unit, Unused, String] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.header("-x-correlation-id", TextCodec.string), HttpCodec.unused)

  val withAccessControlAllowOrigin: MiddlewareSpec[Unit, Unused, AccessControlAllowOrigin] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.accessControlAllowOrigin, HttpCodec.unused)

  def withAuthorization(value: CharSequence): MiddlewareSpec[Unit, Unused, Unit] =
    addHeader(HeaderNames.authorization.toString, value.toString)

  def withBasicAuthorization(username: String, password: String): MiddlewareSpec[Unit, Unused, Unit] = {
    val authString    = String.format("%s:%s", username, password)
    val encodedAuthCB = new String(Base64.getEncoder.encode(authString.getBytes(HTTP_CHARSET)), HTTP_CHARSET)
    val value         = String.format("%s %s", BasicSchemeName, encodedAuthCB)
    addHeader(HeaderNames.authorization.toString, value)
  }

  val withAccessControlAllowMaxAge: MiddlewareSpec[Unit, Unused, AccessControlMaxAge] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.accessControlMaxAge, HttpCodec.unused)

  val withExpires: MiddlewareSpec[Unit, Unused, Expires] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.expires, HttpCodec.unused)

  val withConnection: MiddlewareSpec[Unit, Unused, Connection] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.connection, HttpCodec.unused)

  val withTransferEncoding: MiddlewareSpec[Unit, Unused, TransferEncoding] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.transferEncoding, HttpCodec.unused)

  val withProxyAuthenticate: MiddlewareSpec[Unit, Unused, ProxyAuthenticate] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.proxyAuthenticate, HttpCodec.unused)

  val withProxyAuthorization: MiddlewareSpec[Unit, Unused, ProxyAuthorization] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.proxyAuthorization, HttpCodec.unused)

  val withReferer: MiddlewareSpec[Unit, Unused, Referer] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.referer, HttpCodec.unused)

  val withRetryAfter: MiddlewareSpec[Unit, Unused, RetryAfter] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.retryAfter, HttpCodec.unused)

  val withAccessControlAllowCredentials: MiddlewareSpec[Unit, Unused, AccessControlAllowCredentials] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.accessControlAllowCredentials, HttpCodec.unused)

  val withAccessControlAllowMethods: MiddlewareSpec[Unit, Unused, AccessControlAllowMethods] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.accessControlAllowMethods, HttpCodec.unused)

  val withIfRange: MiddlewareSpec[Unit, Unused, IfRange] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.ifRange, HttpCodec.unused)

  val auth: MiddlewareSpec[Auth.Credentials, Unused, Unit] =
    requireHeader(HeaderNames.wwwAuthenticate.toString)
      .mapIn(
        _.transformOrFailLeft(
          s => decodeHttpBasic(s).fold(Left("Failed to decode headers"): Either[String, Credentials])(Right(_)),
          c => s"${c.uname}:${c.upassword}",
        ),
      )

  type CorsInput =
    Either[(Origin, AccessControlRequestMethod), (Method, Origin)]

  type CorsError =
    (
      AccessControlAllowHeaders,
      AccessControlAllowOrigin,
      AccessControlAllowMethods,
      Option[AccessControlAllowCredentials],
    )

  type CorsOutput =
    (
      AccessControlExposeHeaders,
      AccessControlAllowOrigin,
      AccessControlAllowMethods,
      Option[AccessControlAllowCredentials],
    )

  val cors: MiddlewareSpec[CorsInput, CorsError, CorsOutput] =
    MiddlewareSpec(
      input = (MethodCodec.options ++
        HeaderCodec.origin ++
        HeaderCodec.accessControlRequestMethod) |
        (MethodCodec.method ++ HeaderCodec.origin),
      output = HeaderCodec.accessControlExposeHeaders ++
        HeaderCodec.accessControlAllowOrigin ++
        HeaderCodec.accessControlAllowMethods ++
        HeaderCodec.accessControlAllowCredentials.optional,
      error = HeaderCodec.accessControlAllowHeaders ++
        HeaderCodec.accessControlAllowOrigin ++
        HeaderCodec.accessControlAllowMethods ++
        HeaderCodec.accessControlAllowCredentials.optional ++
        StatusCodec.NoContent,
    )

  def customAuth[I, E, O](
    request: HeaderCodec[I],
    response: HeaderCodec[O],
    error: HttpCodec[CodecType.ResponseType, E],
  ): MiddlewareSpec[I, E, O] =
    MiddlewareSpec(
      request,
      response,
      error,
    )

  def requireHeader(name: String): MiddlewareSpec[String, Unused, Unit] =
    MiddlewareSpec(HeaderCodec.header(name, TextCodec.string), HttpCodec.empty, HttpCodec.unused)

  val withAccept: MiddlewareSpec[Unit, Unused, Accept] =
    MiddlewareSpec(HttpCodec.empty, HeaderCodec.accept, HttpCodec.unused)

  val withAcceptEncoding: MiddlewareSpec[Unit, Unused, AcceptEncoding] =
    MiddlewareSpec(
      HttpCodec.empty,
      HeaderCodec.acceptEncoding,
      HttpCodec.unused,
    )

  val withAcceptLanguage: MiddlewareSpec[Unit, Unused, AcceptLanguage] =
    MiddlewareSpec(
      HttpCodec.empty,
      HeaderCodec.acceptLanguage,
      HttpCodec.unused,
    )

  val withAcceptPatch: MiddlewareSpec[Unit, Unused, AcceptPatch] =
    MiddlewareSpec(
      HttpCodec.empty,
      HeaderCodec.acceptPatch,
      HttpCodec.unused,
    )

  val withAcceptRanges: MiddlewareSpec[Unit, Unused, AcceptRanges] =
    MiddlewareSpec(
      HttpCodec.empty,
      HeaderCodec.acceptRanges,
      HttpCodec.unused,
    )

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
