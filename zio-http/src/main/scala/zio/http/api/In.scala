package zio.http.api

import zio.schema.Schema

sealed trait In[Input] {
  self =>

  def ??(doc: Doc): In[Input] = In.WithDoc(self, doc)

  def ++[Input2](that: In[Input2])(implicit combiner: Combiner[Input, Input2]): In[combiner.Out] =
    In.Combine(self, that, combiner)

  def /[Input2](that: In[Input2])(implicit combiner: Combiner[Input, Input2]): In[combiner.Out] =
    self ++ that

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
  sealed trait Atom[Input0] extends In[Input0]

  // In
  //   .get("users" / id)
  //   .input[User]
  //   .map { case (id, user) =>
  //     UserWithId(id, user)
  //   }
  //   .input("posts")
  // "users" / id / "posts"

  // private[api] case object Empty                                                 extends Atom[Unit]
  private[api] final case class Route[A](textCodec: TextCodec[A])                extends Atom[A]
  private[api] final case class InputBody[A](input: Schema[A])                   extends Atom[A]
  private[api] final case class Query[A](name: String, textCodec: TextCodec[A])  extends Atom[A]
  private[api] final case class Header[A](name: String, textCodec: TextCodec[A]) extends Atom[A]
  private[api] final case class WithDoc[A](in: In[A], doc: Doc)                  extends In[A]

  // - 0
  // - pre-index
  private final case class IndexedAtom[A](atom: Atom[A], index: Int)         extends Atom[A]
  private final case class Transform[X, A](api: In[X], f: X => A, g: A => X) extends In[A]

  private final case class Combine[A1, A2, B1, B2, A, B](
    left: In[A1],
    right: In[A2],
    inputCombiner: Combiner.WithOut[A1, A2, A],
  ) extends In[A]

  import zio.Chunk

  def flatten(in: In[_]): FlattenedAtoms = {
    var result = FlattenedAtoms.empty
    flattenedAtoms(in).foreach { atom =>
      result = result.append(atom)
    }
    result
  }

  private def flattenedAtoms(in: In[_]): Chunk[Atom[_]] =
    in match {
      case Combine(left, right, _) => flattenedAtoms(left) ++ flattenedAtoms(right)
      case atom: Atom[_]           => Chunk(atom)
      case map: Transform[_, _]    => flattenedAtoms(map.api)
      case WithDoc(api, _)         => flattenedAtoms(api)
    }

  type Constructor[+A] = InputResults => A

  private def indexed[A](api: In[A]): In[A] =
    indexedImpl(api, AtomIndices())._1

  private def indexedImpl[A](api: In[A], indices: AtomIndices): (In[A], AtomIndices) =
    api.asInstanceOf[In[_]] match {
      case Combine(left, right, inputCombiner) =>
        val (left2, leftIndices)   = indexedImpl(left, indices)
        val (right2, rightIndices) = indexedImpl(right, leftIndices)
        (Combine(left2, right2, inputCombiner).asInstanceOf[In[A]], rightIndices)
      case atom: Atom[_]                       =>
        (IndexedAtom(atom, indices.get(atom)).asInstanceOf[In[A]], indices.increment(atom))
      case Transform(api, f, g)                =>
        val (api2, resultIndices) = indexedImpl(api, indices)
        (Transform(api2, f, g).asInstanceOf[In[A]], resultIndices)

      case WithDoc(api, _) => indexedImpl(api.asInstanceOf[In[A]], indices)
    }

  def thread[A](api: In[A]): Constructor[A] =
    threadIndexed(indexed(api))

  private def threadIndexed[A](api: In[A]): Constructor[A] = {
    def coerce(any: Any): A = any.asInstanceOf[A]

    api match {
      case Combine(left, right, inputCombiner) =>
        val leftThread  = threadIndexed(left)
        val rightThread = threadIndexed(right)

        results => {
          val leftValue  = leftThread(results)
          val rightValue = rightThread(results)

          inputCombiner.combine(leftValue, rightValue)
        }

      case IndexedAtom(_: Route[_], index)     =>
        results => coerce(results.routes(index))
      case IndexedAtom(_: Header[_], index)    =>
        results => coerce(results.headers(index))
      case IndexedAtom(_: Query[_], index)     =>
        results => coerce(results.queries(index))
      case IndexedAtom(_: InputBody[_], index) =>
        results => coerce(results.inputBody(index))

      case transform: Transform[_, A] =>
        val threaded = threadIndexed(transform.api)
        results => transform.f(threaded(results))

      case WithDoc(api, _) => threadIndexed(api)

      case atom: Atom[_] =>
        throw new RuntimeException(s"Atom $atom should have been wrapped in IndexedAtom")
    }
  }

  // Private Helper Classes

  private[api] final case class FlattenedAtoms(
    routes: Chunk[TextCodec[_]],
    queries: Chunk[Query[_]],
    headers: Chunk[Header[_]],
    inputBodies: Chunk[InputBody[_]],
  ) {
    def append(atom: Atom[_]) = atom match {
      case route: Route[_]         => copy(routes = routes.appended(route.textCodec))
      case query: Query[_]         => copy(queries = queries.appended(query))
      case header: Header[_]       => copy(headers = headers.appended(header))
      case inputBody: InputBody[_] => copy(inputBodies = inputBodies.appended(inputBody))
      case _: IndexedAtom[_]       => throw new RuntimeException("IndexedAtom should not be appended to FlattenedAtoms")
    }
  }

  private[api] object FlattenedAtoms {
    val empty = FlattenedAtoms(Chunk.empty, Chunk.empty, Chunk.empty, Chunk.empty)
  }

  private[api] final case class InputResults(
    routes: Chunk[Any] = Chunk.empty,
    queries: Chunk[Any] = Chunk.empty,
    headers: Chunk[Any] = Chunk.empty,
    inputBody: Chunk[Any] = Chunk.empty,
  )

  private final case class AtomIndices(
    route: Int = 0,
    query: Int = 0,
    header: Int = 0,
    inputBody: Int = 0,
  ) {
    def increment(atom: Atom[_]): AtomIndices = {
      atom match {
        case _: Route[_]       => copy(route = route + 1)
        case _: Query[_]       => copy(query = query + 1)
        case _: Header[_]      => copy(header = header + 1)
        case _: InputBody[_]   => copy(inputBody = inputBody + 1)
        case _: IndexedAtom[_] => throw new RuntimeException("IndexedAtom should not be passed to increment")
      }
    }

    def get(atom: Atom[_]): Int =
      atom match {
        case _: Route[_]       => route
        case _: Query[_]       => query
        case _: Header[_]      => header
        case _: InputBody[_]   => inputBody
        case _: IndexedAtom[_] => throw new RuntimeException("IndexedAtom should not be passed to get")
      }
  }
}
