package zio.http.api

import zio.schema.Schema
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * A [[zio.http.api.In]] represents an input to an API. In the HTTP protocol,
 * these inputs may come from the unconsumed portion of the HTTP path, the query
 * string parameters, the request headers, or the request body.
 *
 * An `In` is a purely declarative description of an input, and therefore, it
 * can be used to generate documentation, clients, and client libraries.
 */
sealed trait In[Input] {
  self =>
  import zio.http.api.In._

  def ??(doc: Doc): In[Input] = In.WithDoc(self, doc)

  def ++[Input2](that: In[Input2])(implicit combiner: Combiner[Input, Input2]): In[combiner.Out] =
    In.Combine(self, that, combiner)

  def /[Input2](that: In[Input2])(implicit combiner: Combiner[Input, Input2]): In[combiner.Out] =
    self ++ that

  def /(that: String): In[Input] = self ++ In.literal(that)

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
  private[api] sealed trait Atom[Input0] extends In[Input0]

  private[api] final case class Route[A](textCodec: TextCodec[A])                extends Atom[A]
  private[api] final case class InputBody[A](input: Schema[A])                   extends Atom[A]
  private[api] final case class Query[A](name: String, textCodec: TextCodec[A])  extends Atom[A]
  private[api] final case class Header[A](name: String, textCodec: TextCodec[A]) extends Atom[A]
  private[api] final case class IndexedAtom[A](atom: Atom[A], index: Int)        extends Atom[A]

  private[api] final case class WithDoc[A](in: In[A], doc: Doc)                   extends In[A]
  private[api] final case class Transform[X, A](api: In[X], f: X => A, g: A => X) extends In[A]

  private[api] final case class Combine[A1, A2, A](
    left: In[A1],
    right: In[A2],
    inputCombiner: Combiner.WithOut[A1, A2, A],
  ) extends In[A]
}
