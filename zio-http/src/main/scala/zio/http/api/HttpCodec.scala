package zio.http.api

import scala.language.implicitConversions

import zio.http._
import zio.http.model._

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;
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
  private val encoderDecoder = zio.http.api.internal.EncoderDecoder(self)

  def ??(doc: Doc): HttpCodec[AtomTypes, Value] = HttpCodec.WithDoc(self, doc)

  def ++[AtomTypes1 <: AtomTypes, Value2](that: HttpCodec[AtomTypes1, Value2])(implicit
    combiner: Combiner[Value, Value2],
  ): HttpCodec[AtomTypes1, combiner.Out] =
    HttpCodec.Combine[AtomTypes1, AtomTypes1, Value, Value2, combiner.Out](self, that, combiner)

  def &[Value2](
    that: QueryCodec[Value2],
  )(implicit
    combiner: Combiner[Value, Value2],
    ev: CodecType.Query <:< AtomTypes,
  ): QueryCodec[combiner.Out] =
    self.asQuery ++ that

  def /[Value2](
    that: RouteCodec[Value2],
  )(implicit
    combiner: Combiner[Value, Value2],
    ev: CodecType.Route <:< AtomTypes,
  ): RouteCodec[combiner.Out] =
    self.asRoute ++ that

  final def asQuery(implicit ev: CodecType.Query <:< AtomTypes): QueryCodec[Value] =
    self.asInstanceOf[QueryCodec[Value]]

  final def asRoute(implicit ev: CodecType.Route <:< AtomTypes): RouteCodec[Value] =
    self.asInstanceOf[RouteCodec[Value]]

  final def decode(codec: Codec)(url: URL, status: Status, method: Method, headers: Headers, body: Body): Task[Value] =
    encoderDecoder.decode(codec)(url, status, method, headers, body)

  final def encode(codec: Codec)(value: Value): (URL, Status, Method, Headers, Body) =
    encoderDecoder.encode(codec)(value)

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

}

object HttpCodec extends HeaderCodecs with QueryCodecs with RouteCodecs {
  implicit def stringToLiteral(s: String): RouteCodec[Unit] = RouteCodec.literal(s)

  def empty: HttpCodec[Any, Unit] =
    Empty

  private[api] sealed trait Atom[-AtomTypes, Value0] extends HttpCodec[AtomTypes, Value0]

  private[api] case object Empty                                                 extends Atom[Any, Unit]
  private[api] final case class Status[A](textCodec: TextCodec[A])               extends Atom[CodecType.Status, A]
  private[api] final case class Route[A](textCodec: TextCodec[A])                extends Atom[CodecType.Route, A]
  private[api] final case class Body[A](schema: Schema[A])                       extends Atom[CodecType.Body, A]
  private[api] final case class BodyStream[A](schema: Schema[A])
      extends Atom[CodecType.Body, ZStream[Any, Throwable, A]]
  private[api] final case class Query[A](name: String, textCodec: TextCodec[A])  extends Atom[CodecType.Query, A]
  private[api] final case class Method[A](methodCodec: TextCodec[A])             extends Atom[CodecType.Method, A]
  private[api] final case class Header[A](name: String, textCodec: TextCodec[A]) extends Atom[CodecType.Header, A]
  private[api] final case class IndexedAtom[AtomType, A](atom: Atom[AtomType, A], index: Int) extends Atom[AtomType, A]
  private[api] final case class WithDoc[AtomType, A](in: HttpCodec[AtomType, A], doc: Doc)
      extends HttpCodec[AtomType, A]
  private[api] final case class TransformOrFail[AtomType, X, A](
    api: HttpCodec[AtomType, X],
    f: X => Either[String, A],
    g: A => Either[String, X],
  ) extends HttpCodec[AtomType, A]

  private[api] final case class Combine[AtomType1, AtomType2, A1, A2, A](
    left: HttpCodec[AtomType1, A1],
    right: HttpCodec[AtomType2, A2],
    inputCombiner: Combiner.WithOut[A1, A2, A],
  ) extends HttpCodec[AtomType1 with AtomType2, A]
}
