package zio.http.api

import scala.language.implicitConversions
import zio.http._
import zio.http.model._
import zio.http.api.internal.{RichTextCodec, TextCodec}
import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.stream.ZStream
import zio.schema.Schema
import zio.schema.codec.Codec

/**
 * A [[zio.http.api.HttpCodec]] represents a codec for a part of an HTTP
 * request. HttpCodec the HTTP protocol, these parts may be the unconsumed
 * portion of the HTTP path (a route codec), the query string parameters (a
 * query codec), the request headers (a header codec), or the request body (a
 * body codec).
 *
 * A HttpCodec is a purely declarative description of an input, and therefore,
 * it can be used to generate documentation, clients, and client libraries.
 *
 * HttpCodecs are a bit like invertible multi-channel parsers.
 */
sealed trait HttpCodec[-AtomTypes, Value] {
  self =>
  private lazy val encoderDecoder = zio.http.api.internal.EncoderDecoder(self)

  /**
   * Returns a new codec that is the same as this one, but has attached docs,
   * which will render whenever docs are generated from the codec.
   */
  def ??(doc: Doc): HttpCodec[AtomTypes, Value] = HttpCodec.WithDoc(self, doc)

  /**
   * Returns a new codec, where the value produced by this one is optional. This
   * method may only be called on header and query codecs.
   */
  def optional(implicit
    ev: CodecType.Header with CodecType.Query with CodecType.Method <:< AtomTypes,
  ): HttpCodec[AtomTypes, Option[Value]] =
    HttpCodec.Optional(HttpCodec.updateOptional(self))

  /**
   * Returns a new codec that is the composition of this codec and the specified
   * codec. For codecs that include route codecs, the routes will be decoded
   * sequentially from left to right.
   */
  def ++[AtomTypes1 <: AtomTypes, Value2](that: HttpCodec[AtomTypes1, Value2])(implicit
    combiner: Combiner[Value, Value2],
  ): HttpCodec[AtomTypes1, combiner.Out] =
    HttpCodec.Combine[AtomTypes1, AtomTypes1, Value, Value2, combiner.Out](self, that, combiner)

  /**
   * Combines two query codecs into another query codec.
   */
  def &[Value2](
    that: QueryCodec[Value2],
  )(implicit
    combiner: Combiner[Value, Value2],
    ev: CodecType.Query <:< AtomTypes,
  ): QueryCodec[combiner.Out] =
    self.asQuery ++ that

  /**
   * Combines two route codecs into another route codec.
   */
  def /[Value2](
    that: RouteCodec[Value2],
  )(implicit
    combiner: Combiner[Value, Value2],
    ev: CodecType.Route <:< AtomTypes,
  ): RouteCodec[combiner.Out] =
    self.asRoute ++ that

  /**
   * Reinterprets this codec as a query codec assuming evidence that this
   * interpretation is sound.
   */
  final def asQuery(implicit ev: CodecType.Query <:< AtomTypes): QueryCodec[Value] =
    self.asInstanceOf[QueryCodec[Value]]

  /**
   * Reinterpets this codec as a route codec assuming evidence that this
   * interpretation is sound.
   */
  final def asRoute(implicit ev: CodecType.Route <:< AtomTypes): RouteCodec[Value] =
    self.asInstanceOf[RouteCodec[Value]]

  /**
   * Uses this codec to decode the Scala value from a request.
   */
  final def decodeRequest(request: Request)(implicit trace: Trace): Task[Value] =
    decode(request.url, Status.Ok, request.method, request.headers, request.body)

  /**
   * Uses this codec to decode the Scala value from a response.
   */
  final def decodeResponse(response: Response)(implicit trace: Trace): Task[Value] =
    decode(URL.empty, response.status, Method.GET, response.headers, response.body)

  private final def decode(url: URL, status: Status, method: Method, headers: Headers, body: Body)(implicit
    trace: Trace,
  ): Task[Value] =
    encoderDecoder.decode(url, status, method, headers, body)

  /**
   * Uses this codec to encode the Scala value into a request.
   */
  final def encodeRequest(value: Value): Request =
    encodeWith(value)((url, status, method, headers, body) =>
      Request(
        url = url,
        method = method.getOrElse(Method.GET),
        headers = headers,
        body = body,
        version = Version.Http_1_1,
        remoteAddress = None,
      ),
    )

  /**
   * Uses this codec to encode the Scala value as a response.
   */
  final def encodeResponse[Z](value: Value): Response =
    encodeWith(value)((url, status, method, headers, body) => Response(headers = headers, body = body))

  /**
   * Uses this codec to encode the Scala value as a response patch.
   */
  final def encodeResponsePatch[Z](value: Value): Response.Patch =
    encodeWith(value)((url, status, method, headers, body) => Response.Patch(addHeaders = headers, setStatus = status))

  private final def encodeWith[Z](value: Value)(
    f: (URL, Option[Status], Option[Method], Headers, Body) => Z,
  ): Z =
    encoderDecoder.encodeWith(value)(f)

  /**
   * Transforms the type parameter of this HttpCodec from `Value` to `Value2`.
   * Due to the fact that HttpCodec is invariant in its type parameter, the
   * transformation requires not just a function from `Value` to `Value2`, but
   * also, a function from `Value2` to `Value`.
   *
   * One of these functions will be used in decoding, for example, when the
   * endpoint is invoked on the server. The other of these functions will be
   * used in encoding, for example, when a client calls the endpoint on the
   * server.
   */
  def transform[Value2](f: Value => Value2, g: Value2 => Value): HttpCodec[AtomTypes, Value2] =
    HttpCodec.TransformOrFail[AtomTypes, Value, Value2](self, in => Right(f(in)), output => Right(g(output)))

  def transformOrFail[Value2](
    f: Value => Either[String, Value2],
    g: Value2 => Either[String, Value],
  ): HttpCodec[AtomTypes, Value2] =
    HttpCodec.TransformOrFail[AtomTypes, Value, Value2](self, f, g)

  def transformOrFailLeft[Value2](
    f: Value => Either[String, Value2],
    g: Value2 => Value,
  ): HttpCodec[AtomTypes, Value2] =
    HttpCodec.TransformOrFail[AtomTypes, Value, Value2](self, f, output => Right(g(output)))

  def transformOrFailRight[Value2](
    f: Value => Value2,
    g: Value2 => Either[String, Value],
  ): HttpCodec[AtomTypes, Value2] =
    HttpCodec.TransformOrFail[AtomTypes, Value, Value2](self, in => Right(f(in)), g)

  /**
   * Transforms the type parameter to `Unit` by specifying that all possible
   * values that can be decoded from this `HttpCodec` are in fact equivalent to
   * the provided canonical value.
   *
   * Note: You should NOT use this method on any codec which can decode
   * semantically distinct values.
   */
  def unit(canonical: Value): HttpCodec[AtomTypes, Unit] =
    self.transform(_ => (), _ => canonical)
}

object HttpCodec extends HeaderCodecs with QueryCodecs with RouteCodecs {
  implicit def stringToLiteral(s: String): RouteCodec[Unit] = RouteCodec.literal(s)

  def empty: HttpCodec[Any, Unit] =
    Empty

  private[api] sealed trait Atom[-AtomTypes, Value0] extends HttpCodec[AtomTypes, Value0]

  private[api] final case class Status[A](textCodec: TextCodec[A]) extends Atom[CodecType.Status, A] { self =>
    def erase: Status[Any] = self.asInstanceOf[Status[Any]]
  }
  private[api] final case class Route[A](textCodec: TextCodec[A])  extends Atom[CodecType.Route, A]  { self =>
    def erase: Route[Any] = self.asInstanceOf[Route[Any]]
  }
  private[api] final case class Body[A](schema: Schema[A])         extends Atom[CodecType.Body, A]
  private[api] final case class BodyStream[A](schema: Schema[A])
      extends Atom[CodecType.Body, ZStream[Any, Throwable, A]]
  private[api] final case class Query[A](name: String, textCodec: TextCodec[A], optional: Boolean)
      extends Atom[CodecType.Query, A] {
    self =>
    def erase: Query[Any] = self.asInstanceOf[Query[Any]]
  }

  private[api] final case class Method[A](methodCodec: TextCodec[A]) extends Atom[CodecType.Method, A] { self =>
    def erase: Method[Any] = self.asInstanceOf[Method[Any]]
  }

  private[api] final case class Header[A](
    name: String,
    textCodec: Either[TextCodec[A], RichTextCodec[A]],
    optional: Boolean,
  ) extends Atom[CodecType.Header, A] {
    self =>
    def erase: Header[Any] = self.asInstanceOf[Header[Any]]
  }

  private[api] final case class Optional[AtomType, A](in: HttpCodec[AtomType, A]) extends HttpCodec[AtomType, Option[A]]

  private[api] final case class IndexedAtom[AtomType, A](atom: Atom[AtomType, A], index: Int) extends Atom[AtomType, A]

  private[api] final case class WithDoc[AtomType, A](in: HttpCodec[AtomType, A], doc: Doc)
      extends HttpCodec[AtomType, A]

  private[api] final case class TransformOrFail[AtomType, X, A](
    api: HttpCodec[AtomType, X],
    f: X => Either[String, A],
    g: A => Either[String, X],
  ) extends HttpCodec[AtomType, A]

  private[api] case object Empty extends HttpCodec[Any, Unit]

  private[api] final case class Combine[AtomType1, AtomType2, A1, A2, A](
    left: HttpCodec[AtomType1, A1],
    right: HttpCodec[AtomType2, A2],
    inputCombiner: Combiner.WithOut[A1, A2, A],
  ) extends HttpCodec[AtomType1 with AtomType2, A]

  private[api] def updateOptional[AtomTypes, A](api: HttpCodec[AtomTypes, A]): HttpCodec[AtomTypes, A] = {
    def loop[B](api: HttpCodec[AtomTypes, B]): HttpCodec[AtomTypes, B] =
      api match {
        case atom: HttpCodec.Atom[AtomTypes, B] =>
          atom match {
            case HttpCodec.Header(name, textCodec, _) => HttpCodec.Header(name, textCodec, optional = true)
            case HttpCodec.Query(name, codec, _)      => HttpCodec.Query(name, codec, optional = true)
            case body: HttpCodec.Body[_]              => body
            case method: HttpCodec.Method[_]          => method
            case route: HttpCodec.Route[_]            => route
            case status: HttpCodec.Status[_]          => status
            case bodyStream: HttpCodec.BodyStream[_]  => bodyStream
            case i: HttpCodec.IndexedAtom[_, _]       =>
              val result: HttpCodec[_, _] = loop(i.atom)
              HttpCodec.IndexedAtom(result.asInstanceOf[Atom[AtomTypes, B]], i.index)
          }

        case empty: HttpCodec.Empty.type                   => empty
        case HttpCodec.WithDoc(in, doc)                    => HttpCodec.WithDoc(updateOptional(in), doc)
        case HttpCodec.TransformOrFail(api, f, g)          => HttpCodec.TransformOrFail(updateOptional(api), f, g)
        case optional: HttpCodec.Optional[_, _]            => HttpCodec.Optional(updateOptional(optional.in))
        case HttpCodec.Combine(left, right, inputCombiner) =>
          HttpCodec.Combine(loop(left), loop(right), inputCombiner)
      }

    loop(api)
  }
}
