package zio.http.api

import sun.text.normalizer.ICUBinary.Authenticate
import zio.http.model.Headers
import zio.schema.Schema
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * A [[zio.http.api.In]] represents an input to an API. In the HTTP protocol,
 * these inputs may come from the unconsumed portion of the HTTP path, the query
 * string parameters, the request headers, or the request body.
 *
 * An `In` is a purely declarative description of an input, and therefore, it
 * can be used to generate documentation, clients, and client libraries.
 *
 * Here Raw represents the original components that was used to create `Input`
 */
sealed trait In[-AtomTypes, Input] {
  self =>
  import zio.http.api.In._

  def ??(doc: Doc): In[Input] = In.WithDoc(self, doc)

  def ++[Input2](that: In[Input2])(implicit combiner: Combiner[Input, Input2]): In[combiner.Out] =
    In.Combine(self, that, combiner)

  def /[Input2](
    that: In[In.RouteType, Input2],
  )(implicit combiner: Combiner[Input, Input2], ev: AtomTypes =:= In.RouteType): In[In.RouteType, combiner.Out] =
    self ++ that

  def bodySchema: Option[Schema[_]] =
    self match {
      case Route(_)                => None
      case InputBody(schema)       => Some(schema)
      case Query(_, _)             => None
      case Header(_, _)            => None
      case IndexedAtom(atom, _)    => atom.bodySchema
      case Transform(in, _, _)     => in.bodySchema
      case WithDoc(in, _)          => in.bodySchema
      case Combine(left, right, _) => left.bodySchema orElse right.bodySchema
    }

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
  def transform[Input2](f: Input => Input2, g: Input2 => Input): In[Input2] =
    In.Transform(self, f, g)
}

object In extends RouteInputs with QueryInputs with HeaderInputs {

  type RouteType
  type BodyType
  type QueryType
  type HeaderType

  def empty: In[Any, Unit] =
    Empty

  private[api] sealed trait Atom[-AtomTypes, Input0] extends In[AtomTypes, Input0]

  private[api] case object Empty                                                 extends Atom[Any, Unit]
  private[api] final case class Route[A](textCodec: TextCodec[A])                extends Atom[RouteType, A]
  // TODO; Rename to Body
  private[api] final case class InputBody[A](input: Schema[A])                   extends Atom[BodyType, A]
  // private[api] final case class BodyStream[A](element: Schema[A])                   extends Atom[BodyType, ZStream[Any, Throwable, A]] // and delete Out
  private[api] final case class Query[A](name: String, textCodec: TextCodec[A])  extends Atom[QueryType, A]
  private[api] final case class Header[A](name: String, textCodec: TextCodec[A]) extends Atom[HeaderType, A]
  private[api] final case class IndexedAtom[AtomType, A](atom: Atom[AtomType, A], index: Int) extends Atom[AtomType, A]
  private[api] final case class WithDoc[AtomType, A](in: In[AtomType, A], doc: Doc)           extends In[AtomType, A]
  private[api] final case class Transform[AtomType, X, A](api: In[AtomType, X], f: X => A, g: A => X)
      extends In[AtomType, A]

  private[api] final case class Combine[AtomType1, AtomType2, A1, A2, A](
    left: In[AtomType1, A1],
    right: In[AtomType2, A2],
    inputCombiner: Combiner.WithOut[A1, A2, A],
  ) extends In[AtomType1 with AtomType2, A]
}
