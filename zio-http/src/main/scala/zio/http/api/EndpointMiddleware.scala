package zio.http.api

import zio.ZIO
import zio.http.api.internal.TextCodec
import zio.http.middleware.Auth
import zio.http.middleware.Auth.Credentials
import zio.http.model.Headers.BasicSchemeName
import zio.http.model.headers.values._
import zio.http.model.{Cookie, HTTP_CHARSET, HeaderNames, Headers, Method}
import zio.http.{Request, Response}

import java.util.Base64

/**
 * A description of endpoint middleware, in terms of what the middleware
 * requires from the request, and what it appends to the response.
 */
sealed trait EndpointMiddleware { self =>
  type In
  type Err
  type Out

  def input: HttpCodec[CodecType.Header with CodecType.Query with CodecType.Method, In]
  def output: HttpCodec[CodecType.Header, Out]
  def error: HttpCodec[CodecType.ResponseType, Err]
  def doc: Doc

  def ++(that: EndpointMiddleware)(implicit
    inCombiner: Combiner[In, that.In],
    outCombiner: Combiner[Out, that.Out],
    errAlternator: Alternator[Err, that.Err],
  ): EndpointMiddleware.Typed[inCombiner.Out, errAlternator.Out, outCombiner.Out] =
    EndpointMiddleware.Spec[inCombiner.Out, errAlternator.Out, outCombiner.Out](
      self.input ++ that.input,
      self.output ++ that.output,
      self.error | that.error,
      self.doc + that.doc,
    )

  def mapIn[MiddlewareIn2](
    f: HttpCodec[CodecType.Header with CodecType.Query with CodecType.Method, In] => HttpCodec[
      CodecType.Header with CodecType.Query with CodecType.Method,
      MiddlewareIn2,
    ],
  ): EndpointMiddleware.Typed[MiddlewareIn2, Err, Out] =
    EndpointMiddleware(f(input), output, error, doc)

  def mapOut[MiddlewareOut2](
    f: HttpCodec[CodecType.Header, Out] => HttpCodec[
      CodecType.Header,
      MiddlewareOut2,
    ],
  ): EndpointMiddleware.Typed[In, Err, MiddlewareOut2] =
    EndpointMiddleware(input, f(output), error, doc)

  def mapBoth[MiddlewareIn2, MiddlewareOut2](
    f: HttpCodec[CodecType.Header with CodecType.Query with CodecType.Method, In] => HttpCodec[
      CodecType.Header with CodecType.Query with CodecType.Method,
      MiddlewareIn2,
    ],
    g: HttpCodec[CodecType.Header, Out] => HttpCodec[
      CodecType.Header,
      MiddlewareOut2,
    ],
  ): EndpointMiddleware.Typed[MiddlewareIn2, Err, MiddlewareOut2] =
    mapIn(f).mapOut(g)

  def optional: EndpointMiddleware.Typed[Option[In], Err, Option[Out]] =
    self.optionalIn.optionalOut

  def optionalIn: EndpointMiddleware.Typed[Option[In], Err, Out] =
    self.mapIn(_.optional)

  def optionalOut: EndpointMiddleware.Typed[In, Err, Option[Out]] =
    self.mapOut(_.optional)
}
object EndpointMiddleware       {
  type Typed[In0, Err0, Out0] = EndpointMiddleware { type In = In0; type Err = Err0; type Out = Out0 }
  type None                   = EndpointMiddleware.None.type

  def apply[In0, Err0, Out0](
    input: HttpCodec[CodecType.Header with CodecType.Query with CodecType.Method, In0],
    output: HttpCodec[CodecType.Header, Out0],
    error: HttpCodec[CodecType.ResponseType, Err0],
    doc: Doc = Doc.empty,
  ): EndpointMiddleware.Typed[In0, Err0, Out0] = Spec(input, output, error, doc)

  case object None extends EndpointMiddleware {
    final type In  = Unit
    final type Err = Unused
    final type Out = Unit

    val input: HttpCodec[CodecType.Header with CodecType.Query with CodecType.Method, Unit] = HttpCodec.empty
    val output: HttpCodec[CodecType.Header, Unit]                                           = HttpCodec.empty
    val error: HttpCodec[CodecType.ResponseType, Unused]                                    = HttpCodec.unused
    val doc: Doc                                                                            = Doc.empty
  }
  final case class Spec[In0, Err0, Out0](
    input: HttpCodec[CodecType.Header with CodecType.Query with CodecType.Method, In0],
    output: HttpCodec[CodecType.Header, Out0],
    error: HttpCodec[CodecType.ResponseType, Err0],
    doc: Doc = Doc.empty,
  ) extends EndpointMiddleware { self =>
    final type In  = In0
    final type Err = Err0
    final type Out = Out0
  }

  final case class CsrfValidate(cookieOption: Option[Cookie[Request]], tokenValue: Option[String])

  val none: EndpointMiddleware.Typed[Unit, Unused, Unit] =
    EndpointMiddleware(HttpCodec.empty, HttpCodec.empty, HttpCodec.unused)

  def cookieOption(cookieName: String): EndpointMiddleware.Typed[Option[Cookie[Request]], Unused, Unit] =
    requireHeader(HeaderNames.cookie.toString()).optionalIn
      .mapIn(
        _.transformOrFail(
          {
            case Some(cookieList) => readCookie(cookieList, cookieName)
            case scala.None       => Right(scala.None)
          },
          {
            case Some(cookie) => writeCookie(cookie).map(Some(_))
            case scala.None   => Right(scala.None)
          },
        ),
      )

  def cookie(cookieName: String): EndpointMiddleware.Typed[Cookie[Request], Unused, Unit] = {
    cookieOption(cookieName).mapIn(
      _.transformOrFailLeft(
        {
          case Some(cookie) => Right(cookie)
          case scala.None   => Left(s"Cookie ${cookieName} not found")
        },
        value => Some(value),
      ),
    )
  }

  def csrfValidate(tokenName: String): EndpointMiddleware.Typed[CsrfValidate, Unit, Unit] = {
    val cookie: EndpointMiddleware.Typed[Option[Cookie[Request]], Unused, Unit] =
      EndpointMiddleware.cookieOption(tokenName)

    val tokenHeader =
      EndpointMiddleware.requireHeader(tokenName)

    val forbidden =
      EndpointMiddleware(HttpCodec.empty, HttpCodec.empty, StatusCodec.Forbidden)

    (cookie ++ tokenHeader.mapIn(_.optional) ++ forbidden).mapIn(
      _.transform(
        { case (a, b) =>
          CsrfValidate(a, b)
        },
        value => (value.cookieOption, value.tokenValue),
      ),
    )
  }

  val withContentBase: EndpointMiddleware.Typed[Unit, Unused, ContentBase] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.contentBase, HttpCodec.unused)

  val withContentDisposition: EndpointMiddleware.Typed[Unit, Unused, ContentDisposition] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.contentDisposition, HttpCodec.unused)

  val withContentEncoding: EndpointMiddleware.Typed[Unit, Unused, ContentEncoding]                 =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.contentEncoding, HttpCodec.unused)
  val withContentLanguage: EndpointMiddleware.Typed[Unit, Unused, ContentLanguage]                 =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.contentLanguage, HttpCodec.unused)
  def withContentLength: EndpointMiddleware.Typed[Unit, Unused, ContentLength]                     =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.contentLength, HttpCodec.unused)
  val withContentLocation: EndpointMiddleware.Typed[Unit, Unused, ContentLocation]                 =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.contentLocation, HttpCodec.unused)
  val withContentMd5: EndpointMiddleware.Typed[Unit, Unused, ContentMd5]                           =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.contentMd5, HttpCodec.unused)
  val withContentRange: EndpointMiddleware.Typed[Unit, Unused, ContentRange]                       =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.contentRange, HttpCodec.unused)
  def withContentSecurityPolicy: EndpointMiddleware.Typed[Unit, Unused, ContentSecurityPolicy]     =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.contentSecurityPolicy, HttpCodec.unused)
  val withContentTransferEncoding: EndpointMiddleware.Typed[Unit, Unused, ContentTransferEncoding] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.contentTransferEncoding, HttpCodec.unused)
  val withContentType: EndpointMiddleware.Typed[Unit, Unused, ContentType]                         =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.contentType, HttpCodec.unused)

  val addCookie: EndpointMiddleware.Typed[Unit, Unused, Cookie[Response]] =
    EndpointMiddleware(
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
  def addHeader(key: String, value: String): EndpointMiddleware.Typed[Unit, Unused, Unit] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.header(key, TextCodec.constant(value)), HttpCodec.unused)

  def addHeader(header: Headers.Header): EndpointMiddleware.Typed[Unit, Unused, Unit] =
    addHeader(header.key.toString, header.value.toString)

  def addHeaders(headers: Headers): EndpointMiddleware.Typed[Unit, Unused, Unit] =
    headers.headersAsList.map(addHeader(_)).reduce(_ ++ _)

  val addCorrelationId: EndpointMiddleware.Typed[Unit, Unused, String] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.header("-x-correlation-id", TextCodec.string), HttpCodec.unused)

  val withAccessControlAllowOrigin: EndpointMiddleware.Typed[Unit, Unused, AccessControlAllowOrigin] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.accessControlAllowOrigin, HttpCodec.unused)

  def withAuthorization(value: CharSequence): EndpointMiddleware.Typed[Unit, Unused, Unit] =
    addHeader(HeaderNames.authorization.toString, value.toString)

  def withBasicAuthorization(username: String, password: String): EndpointMiddleware.Typed[Unit, Unused, Unit] = {
    val authString    = String.format("%s:%s", username, password)
    val encodedAuthCB = new String(Base64.getEncoder.encode(authString.getBytes(HTTP_CHARSET)), HTTP_CHARSET)
    val value         = String.format("%s %s", BasicSchemeName, encodedAuthCB)
    addHeader(HeaderNames.authorization.toString, value)
  }

  val withAccessControlAllowMaxAge: EndpointMiddleware.Typed[Unit, Unused, AccessControlMaxAge] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.accessControlMaxAge, HttpCodec.unused)

  val withExpires: EndpointMiddleware.Typed[Unit, Unused, Expires] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.expires, HttpCodec.unused)

  val withConnection: EndpointMiddleware.Typed[Unit, Unused, Connection] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.connection, HttpCodec.unused)

  val withTransferEncoding: EndpointMiddleware.Typed[Unit, Unused, TransferEncoding] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.transferEncoding, HttpCodec.unused)

  val withProxyAuthenticate: EndpointMiddleware.Typed[Unit, Unused, ProxyAuthenticate] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.proxyAuthenticate, HttpCodec.unused)

  val withProxyAuthorization: EndpointMiddleware.Typed[Unit, Unused, ProxyAuthorization] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.proxyAuthorization, HttpCodec.unused)

  val withReferer: EndpointMiddleware.Typed[Unit, Unused, Referer] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.referer, HttpCodec.unused)

  val withRetryAfter: EndpointMiddleware.Typed[Unit, Unused, RetryAfter] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.retryAfter, HttpCodec.unused)

  val withAccessControlAllowCredentials: EndpointMiddleware.Typed[Unit, Unused, AccessControlAllowCredentials] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.accessControlAllowCredentials, HttpCodec.unused)

  val withAccessControlAllowMethods: EndpointMiddleware.Typed[Unit, Unused, AccessControlAllowMethods] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.accessControlAllowMethods, HttpCodec.unused)

  val withIfRange: EndpointMiddleware.Typed[Unit, Unused, IfRange] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.ifRange, HttpCodec.unused)

  val auth: EndpointMiddleware.Typed[Auth.Credentials, Unused, Unit] =
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

  val cors: EndpointMiddleware.Typed[CorsInput, CorsError, CorsOutput] =
    EndpointMiddleware(
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
  ): EndpointMiddleware.Typed[I, E, O] =
    EndpointMiddleware(
      request,
      response,
      error,
    )

  def requireHeader(name: String): EndpointMiddleware.Typed[String, Unused, Unit] =
    EndpointMiddleware(HeaderCodec.header(name, TextCodec.string), HttpCodec.empty, HttpCodec.unused)

  val withAccept: EndpointMiddleware.Typed[Unit, Unused, Accept] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.accept, HttpCodec.unused)

  val withAcceptEncoding: EndpointMiddleware.Typed[Unit, Unused, AcceptEncoding] =
    EndpointMiddleware(
      HttpCodec.empty,
      HeaderCodec.acceptEncoding,
      HttpCodec.unused,
    )

  val withAcceptLanguage: EndpointMiddleware.Typed[Unit, Unused, AcceptLanguage] =
    EndpointMiddleware(
      HttpCodec.empty,
      HeaderCodec.acceptLanguage,
      HttpCodec.unused,
    )

  val withAcceptPatch: EndpointMiddleware.Typed[Unit, Unused, AcceptPatch] =
    EndpointMiddleware(
      HttpCodec.empty,
      HeaderCodec.acceptPatch,
      HttpCodec.unused,
    )

  val withAcceptRanges: EndpointMiddleware.Typed[Unit, Unused, AcceptRanges] =
    EndpointMiddleware(
      HttpCodec.empty,
      HeaderCodec.acceptRanges,
      HttpCodec.unused,
    )

  private[api] def decodeHttpBasic(encoded: String): Option[Credentials] = {
    val colonIndex = encoded.indexOf(":")
    if (colonIndex == -1)
      scala.None
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
