package zio.http.api

sealed trait In[Input] extends RouteInputs with QueryInputs with HeaderInputs {
  self =>

  def ++[Input2](that: In[Input2])(implicit combiner: Combiner[Input, Input2]): In[combiner.Out] =
    In.Combine(self, that, combiner)

  def map[Input2](f: Input => Input2): In[Input2] =
    In.Transform(self, f)

  def /[Input2](that: In[Input2])(implicit combiner: Combiner[Input, Input2]): In[combiner.Out] =
    self ++ that
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

  private[api] case object Empty                                                 extends Atom[Unit]
  private[api] final case class Route[A](textCodec: TextCodec[A])                extends Atom[A]
  private[api] final case class InputBody[A](input: Schema[A])                   extends Atom[A]
  private[api] final case class Query[A](name: String, textCodec: TextCodec[A])  extends Atom[A]
  private[api] final case class Header[A](name: String, textCodec: TextCodec[A]) extends Atom[A]

  // - 0
  // - pre-index
  private final case class IndexedAtom[A](atom: Atom[A], index: Int) extends Atom[A]
  private final case class Transform[X, A](api: In[X], f: X => A)    extends In[A]

  private final case class Combine[A1, A2, B1, B2, A, B](
    left: In[A1],
    right: In[A2],
    inputCombiner: Combiner.WithOut[A1, A2, A],
  ) extends In[A]

  import zio.Chunk

  def flatten(api: In[_]): Chunk[Atom[_]] =
    api match {
      case Combine(left, right, _) => flatten(left) ++ flatten(right)
      case atom: Atom[_]           => Chunk(atom)
      case map: Transform[_, _]    => flatten(map.api)
    }

  type Constructor[+A]   = Chunk[Any] => A
  type Deconstructor[-A] = A => Chunk[Any]

  private def indexed[A](api: In[A]): In[A] =
    indexedImpl(api, 0)._1

  private def indexedImpl[A](api: In[A], start: Int): (In[A], Int) =
    api.asInstanceOf[In[_]] match {
      case Combine(left, right, inputCombiner) =>
        val (left2, leftEnd)   = indexedImpl(left, start)
        val (right2, rightEnd) = indexedImpl(right, leftEnd)
        (Combine(left2, right2, inputCombiner).asInstanceOf[In[A]], rightEnd)
      case atom: Atom[_]                       =>
        (IndexedAtom(atom, start).asInstanceOf[In[A]], start + 1)
      case Transform(api, f)                   =>
        val (api2, end) = indexedImpl(api, start)
        (Transform(api2, f).asInstanceOf[In[A]], end)
    }

  def thread[A](api: In[A]): Constructor[A] =
    threadIndexed(indexed(api))

  private def threadIndexed[A](api: In[A]): Constructor[A] = {
    def coerce(any: Any): A = any.asInstanceOf[A]

    api match {
      case Combine(left, right, inputCombiner) =>
        val leftThread  = threadIndexed(left)
        val rightThread = threadIndexed(right)

        chunk => {
          val leftValue  = leftThread(chunk)
          val rightValue = rightThread(chunk)

          inputCombiner.combine(leftValue, rightValue)
        }

      case indexedAtom: IndexedAtom[_] =>
        chunk => coerce(chunk(indexedAtom.index))

      case transform: Transform[_, A] =>
        val threaded = threadIndexed(transform.api)
        chunk => transform.f(threaded(chunk))

      case atom: Atom[_] =>
        throw new RuntimeException(s"Atom $atom should have been wrapped in IndexedAtom")
    }
  }

}
