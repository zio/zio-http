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

  def apply[In0, Out0](
    input: HttpCodec[CodecType.Header with CodecType.Query with CodecType.Method, In0],
    output: HttpCodec[CodecType.Header, Out0],
  ): EndpointMiddleware.Typed[In0, Nothing, Out0] = Spec[In0, Nothing, Out0](input, output, HttpCodec.halt, Doc.empty)

  def apply[In0, Out0](
    input: HttpCodec[CodecType.Header with CodecType.Query with CodecType.Method, In0],
    output: HttpCodec[CodecType.Header, Out0],
    doc: Doc,
  ): EndpointMiddleware.Typed[In0, Nothing, Out0] = Spec[In0, Nothing, Out0](input, output, HttpCodec.halt, doc)

  def apply[In0, Err0, Out0](
    input: HttpCodec[CodecType.Header with CodecType.Query with CodecType.Method, In0],
    output: HttpCodec[CodecType.Header, Out0],
    error: HttpCodec[CodecType.ResponseType, Err0],
    doc: Doc = Doc.empty,
  ): EndpointMiddleware.Typed[In0, Err0, Out0] = Spec(input, output, error, doc)

  case object None extends EndpointMiddleware {
    final type In  = Unit
    final type Err = zio.ZNothing
    final type Out = Unit

    val input: HttpCodec[CodecType.Header with CodecType.Query with CodecType.Method, Unit] = HttpCodec.empty
    val output: HttpCodec[CodecType.Header, Unit]                                           = HttpCodec.empty
    val error: HttpCodec[CodecType.ResponseType, Nothing]                                   = HttpCodec.halt
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

  val none: EndpointMiddleware.Typed[Unit, Nothing, Unit] =
    EndpointMiddleware(HttpCodec.empty, HttpCodec.empty)

  def cookieOption(cookieName: String): EndpointMiddleware.Typed[Option[Cookie[Request]], Nothing, Unit] =
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

  def cookie(cookieName: String): EndpointMiddleware.Typed[Cookie[Request], Nothing, Unit] = {
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
    val cookie: EndpointMiddleware.Typed[Option[Cookie[Request]], Nothing, Unit] =
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

  val withContentBase: EndpointMiddleware.Typed[Unit, Nothing, ContentBase] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.contentBase)

  val withContentDisposition: EndpointMiddleware.Typed[Unit, Nothing, ContentDisposition] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.contentDisposition)

  val withContentEncoding: EndpointMiddleware.Typed[Unit, Nothing, ContentEncoding]                 =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.contentEncoding)
  val withContentLanguage: EndpointMiddleware.Typed[Unit, Nothing, ContentLanguage]                 =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.contentLanguage)
  def withContentLength: EndpointMiddleware.Typed[Unit, Nothing, ContentLength]                     =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.contentLength)
  val withContentLocation: EndpointMiddleware.Typed[Unit, Nothing, ContentLocation]                 =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.contentLocation)
  val withContentMd5: EndpointMiddleware.Typed[Unit, Nothing, ContentMd5]                           =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.contentMd5)
  val withContentRange: EndpointMiddleware.Typed[Unit, Nothing, ContentRange]                       =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.contentRange)
  def withContentSecurityPolicy: EndpointMiddleware.Typed[Unit, Nothing, ContentSecurityPolicy]     =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.contentSecurityPolicy)
  val withContentTransferEncoding: EndpointMiddleware.Typed[Unit, Nothing, ContentTransferEncoding] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.contentTransferEncoding)
  val withContentType: EndpointMiddleware.Typed[Unit, Nothing, ContentType]                         =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.contentType)

  val addCookie: EndpointMiddleware.Typed[Unit, Nothing, Cookie[Response]] =
    EndpointMiddleware(
      HttpCodec.empty,
      HeaderCodec.setCookie.transformOrFail(
        _ => Left("Cannot add cookie"),
        value => Right(ResponseCookie.CookieValue(value)),
      ),
    )

  /**
   * Add specified header to the response
   */
  def addHeader(key: String, value: String): EndpointMiddleware.Typed[Unit, Nothing, Unit] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.header(key, TextCodec.constant(value)))

  def addHeader(header: Headers.Header): EndpointMiddleware.Typed[Unit, Nothing, Unit] =
    addHeader(header.key.toString, header.value.toString)

  def addHeaders(headers: Headers): EndpointMiddleware.Typed[Unit, Nothing, Unit] =
    headers.headersAsList.map(addHeader(_)).reduce(_ ++ _)

  val addCorrelationId: EndpointMiddleware.Typed[Unit, Nothing, String] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.header("-x-correlation-id", TextCodec.string))

  val withAccessControlAllowOrigin: EndpointMiddleware.Typed[Unit, Nothing, AccessControlAllowOrigin] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.accessControlAllowOrigin)

  def withAuthorization(value: CharSequence): EndpointMiddleware.Typed[Unit, Nothing, Unit] =
    addHeader(HeaderNames.authorization.toString, value.toString)

  def withBasicAuthorization(username: String, password: String): EndpointMiddleware.Typed[Unit, Nothing, Unit] = {
    val authString    = String.format("%s:%s", username, password)
    val encodedAuthCB = new String(Base64.getEncoder.encode(authString.getBytes(HTTP_CHARSET)), HTTP_CHARSET)
    val value         = String.format("%s %s", BasicSchemeName, encodedAuthCB)
    addHeader(HeaderNames.authorization.toString, value)
  }

  val withAccessControlAllowMaxAge: EndpointMiddleware.Typed[Unit, Nothing, AccessControlMaxAge] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.accessControlMaxAge)

  val withExpires: EndpointMiddleware.Typed[Unit, Nothing, Expires] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.expires)

  val withConnection: EndpointMiddleware.Typed[Unit, Nothing, Connection] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.connection)

  val withTransferEncoding: EndpointMiddleware.Typed[Unit, Nothing, TransferEncoding] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.transferEncoding)

  val withProxyAuthenticate: EndpointMiddleware.Typed[Unit, Nothing, ProxyAuthenticate] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.proxyAuthenticate)

  val withProxyAuthorization: EndpointMiddleware.Typed[Unit, Nothing, ProxyAuthorization] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.proxyAuthorization)

  val withReferer: EndpointMiddleware.Typed[Unit, Nothing, Referer] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.referer)

  val withRetryAfter: EndpointMiddleware.Typed[Unit, Nothing, RetryAfter] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.retryAfter)

  val withAccessControlAllowCredentials: EndpointMiddleware.Typed[Unit, Nothing, AccessControlAllowCredentials] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.accessControlAllowCredentials)

  val withAccessControlAllowMethods: EndpointMiddleware.Typed[Unit, Nothing, AccessControlAllowMethods] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.accessControlAllowMethods)

  val withIfRange: EndpointMiddleware.Typed[Unit, Nothing, IfRange] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.ifRange)

  val auth: EndpointMiddleware.Typed[Auth.Credentials, Nothing, Unit] =
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

  def requireHeader(name: String): EndpointMiddleware.Typed[String, Nothing, Unit] =
    EndpointMiddleware(HeaderCodec.header(name, TextCodec.string), HttpCodec.empty)

  val withAccept: EndpointMiddleware.Typed[Unit, Nothing, Accept] =
    EndpointMiddleware(HttpCodec.empty, HeaderCodec.accept)

  val withAcceptEncoding: EndpointMiddleware.Typed[Unit, Nothing, AcceptEncoding] =
    EndpointMiddleware(
      HttpCodec.empty,
      HeaderCodec.acceptEncoding,
    )

  val withAcceptLanguage: EndpointMiddleware.Typed[Unit, Nothing, AcceptLanguage] =
    EndpointMiddleware(
      HttpCodec.empty,
      HeaderCodec.acceptLanguage,
    )

  val withAcceptPatch: EndpointMiddleware.Typed[Unit, Nothing, AcceptPatch] =
    EndpointMiddleware(
      HttpCodec.empty,
      HeaderCodec.acceptPatch,
    )

  val withAcceptRanges: EndpointMiddleware.Typed[Unit, Nothing, AcceptRanges] =
    EndpointMiddleware(
      HttpCodec.empty,
      HeaderCodec.acceptRanges,
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
