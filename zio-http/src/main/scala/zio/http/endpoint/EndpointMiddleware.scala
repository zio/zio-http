package zio.http.endpoint

import zio.ZIO
import zio.http.codec.TextCodec
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

  def implement[R, S](incoming: In => ZIO[R, Err, S])(
    outgoing: S => ZIO[R, Err, Out],
  ): RoutesMiddleware[R, S, this.type] =
    RoutesMiddleware.make[this.type](this)(incoming)(outgoing)

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
  ): EndpointMiddleware.Typed[In0, Nothing, Out0] = Spec[In0, Nothing, Out0](input, output, HttpCodec.unused, Doc.empty)

  def apply[In0, Out0](
    input: HttpCodec[CodecType.Header with CodecType.Query with CodecType.Method, In0],
    output: HttpCodec[CodecType.Header, Out0],
    doc: Doc,
  ): EndpointMiddleware.Typed[In0, Nothing, Out0] = Spec[In0, Nothing, Out0](input, output, HttpCodec.unused, doc)

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
    val error: HttpCodec[CodecType.ResponseType, zio.ZNothing]                              = HttpCodec.unused
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

  /**
   * Add specified header to the response
   */
  def addHeader[A](headerCodec: HeaderCodec[A]): EndpointMiddleware.Typed[Unit, Nothing, A] =
    EndpointMiddleware(HttpCodec.empty, headerCodec)

  val auth: EndpointMiddleware.Typed[WWWAuthenticate, Nothing, Unit] =
    requireHeader(HeaderCodec.wwwAuthenticate)

  def cookie: EndpointMiddleware.Typed[RequestCookie, Nothing, Unit] =
    requireHeader(HeaderCodec.cookie)

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

  val none: EndpointMiddleware.Typed[Unit, Nothing, Unit] =
    EndpointMiddleware(HttpCodec.empty, HttpCodec.empty)

  def requireHeader[A](codec: HeaderCodec[A]): EndpointMiddleware.Typed[A, Nothing, Unit] =
    EndpointMiddleware(codec, HttpCodec.empty)
}
