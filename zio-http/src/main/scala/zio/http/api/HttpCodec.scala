package zio.http.api

import scala.language.implicitConversions

import zio.stream.ZStream
import zio.schema.Schema
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * A [[zio.http.api.HttpCodec]] represents a codec for a part of an HTTP
 * request. In the HTTP protocol, these parts may be the unconsumed portion of
 * the HTTP path (a route codec), the query string parameters (a query codec),
 * the request headers (a header codec), or the request body (a body codec).
 *
 * A `HttpCodec` is a purely declarative description of an input, and therefore,
 * it can be used to generate documentation, clients, and client libraries.
 *
 * HttpCodecs are a bit like invertible multi-channel parsers.
 */
sealed trait HttpCodec[-AtomTypes, Input] {
  self =>

  def ??(doc: Doc): HttpCodec[AtomTypes, Input] = HttpCodec.WithDoc(self, doc)

  def ++[AtomTypes1 <: AtomTypes, Input2](that: HttpCodec[AtomTypes1, Input2])(implicit
    combiner: Combiner[Input, Input2],
  ): HttpCodec[AtomTypes1, combiner.Out] =
    HttpCodec.Combine[AtomTypes1, AtomTypes1, Input, Input2, combiner.Out](self, that, combiner)

  def /[Input2](
    that: RouteCodec[Input2],
  )(implicit
    combiner: Combiner[Input, Input2],
    ev: CodecType.Route <:< AtomTypes,
  ): RouteCodec[combiner.Out] =
    self.asRoute ++ that

  def asRoute(implicit ev: CodecType.Route <:< AtomTypes): RouteCodec[Input] =
    self.asInstanceOf[RouteCodec[Input]]

  def bodySchema: Option[Schema[_]] =
    HttpCodec.bodySchema(self)

  /**
   * Transforms the type parameter of this `In` from `Input` to `Input2`. Due to
   * the fact that `In` is invariant in its type parameter, the transformation
   * requires not just a function from `Input` to `Input2`, but also, a function
   * from `Input2` to `Input`.
   *
   * One of these functions will be used in decoding, for example, when the
   * endpoint is invoked on the server. The other of these functions will be
   * used in encoding, for example, when a client calls the endpoint on the
   * server.
   */
  def transform[Input2](f: Input => Input2, g: Input2 => Input): HttpCodec[AtomTypes, Input2] =
    HttpCodec.TransformOrFail[AtomTypes, Input, Input2](self, in => Right(f(in)), output => Right(g(output)))

  def transformOrFailLeft[Input2](
    f: Input => Either[String, Input2],
    g: Input2 => Input,
  ): HttpCodec[AtomTypes, Input2] =
    HttpCodec.TransformOrFail[AtomTypes, Input, Input2](self, f, output => Right(g(output)))

  def transformOrFailRight[Input2](
    f: Input => Input2,
    g: Input2 => Either[String, Input],
  ): HttpCodec[AtomTypes, Input2] =
    HttpCodec.TransformOrFail[AtomTypes, Input, Input2](self, in => Right(f(in)), g)

}

object HttpCodec extends HeaderCodecs with QueryCodecs with RouteCodecs {
  implicit def stringToLiteral(s: String): RouteCodec[Unit] = RouteCodec.literal(s)

  def empty: HttpCodec[Any, Unit] =
    Empty

  private[api] sealed trait Atom[-AtomTypes, Input0] extends HttpCodec[AtomTypes, Input0]

  private[api] case object Empty                                  extends Atom[Any, Unit]
  private[api] final case class Route[A](textCodec: TextCodec[A]) extends Atom[CodecType.Route, A]
  private[api] final case class Body[A](input: Schema[A])         extends Atom[CodecType.Body, A]
  private[api] final case class BodyStream[A](element: Schema[A])
      extends Atom[CodecType.Body, ZStream[Any, Throwable, A]] // and delete Out
  private[api] final case class Query[A](name: String, textCodec: TextCodec[A])  extends Atom[CodecType.Query, A]
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

  private[api] def bodySchema[AtomTypes, Input](in: HttpCodec[AtomTypes, Input]): Option[Schema[_]] = {
    in match {
      case Body(schema)              => Some(schema)
      case BodyStream(elementSchema) => Some(elementSchema)
      case IndexedAtom(atom, _)      => bodySchema(atom)
      case TransformOrFail(in, _, _) => bodySchema(in)
      case WithDoc(in, _)            => bodySchema(in)
      case Combine(left, right, _)   => bodySchema(left) orElse bodySchema(right)
      case _                         => None
    }
  }
}
