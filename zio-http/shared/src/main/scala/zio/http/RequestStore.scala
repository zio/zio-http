package zio.http

import zio.{Exit, FiberRef, Tag, Unsafe, ZIO}

/**
 * A fiber-local store for request-scoped values. A value is keyed by its type,
 * through its `Tag`: storing a second value of the same type replaces the
 * first.
 *
 * A forked fiber starts with a copy of the store of its parent. A change made
 * by a child fiber reaches the parent when the parent joins the child.
 *
 * {{{
 * import zio._
 *
 * case class RequestId(value: String)
 *
 * val program: UIO[Option[RequestId]] = RequestStore.set(RequestId("r-17")) *> RequestStore.get[RequestId]
 * // Some(RequestId(r-17))
 * }}}
 *
 * @see
 *   [[https://zio.dev/zio-http/reference/contextual/request-store/ RequestStore]]
 */
object RequestStore {

  private[http] val requestStore: FiberRef[Map[Tag[_], Any]] =
    FiberRef.unsafe.make[Map[Tag[_], Any]](Map.empty)(Unsafe.unsafe)

  /** Returns the stored value of type `A`, or `None` when there is none. */
  def get[A: Tag]: ZIO[Any, Nothing, Option[A]] =
    requestStore.get.map(_.get(implicitly[Tag[A]]).asInstanceOf[Option[A]])

  /**
   * Returns the stored value of type `A`, or `orElse` when there is none. The
   * default is not stored.
   */
  def getOrElse[A: Tag](orElse: => A): ZIO[Any, Nothing, A] =
    requestStore.get.map(_.getOrElse(implicitly[Tag[A]], orElse).asInstanceOf[A])

  /**
   * Returns the stored value of type `A`. Fails with a `NoSuchElementException`
   * when there is none.
   */
  def getOrFail[A: Tag]: ZIO[Any, NoSuchElementException, A] =
    get[A].flatMap {
      case Some(value) => Exit.succeed(value)
      case None        => Exit.fail(new NoSuchElementException(s"No value found for type ${implicitly[Tag[A]]}"))
    }

  /** Stores `a`, replacing the stored value of type `A`. */
  def set[A: Tag](a: A): ZIO[Any, Nothing, Unit] =
    requestStore.update(_.updated(implicitly[Tag[A]], a))

  /**
   * Replaces the stored value of type `A` with the result of `a` applied to it,
   * or to `None` when there is none.
   */
  def update[A: Tag](a: Option[A] => A): ZIO[Any, Nothing, Unit] =
    requestStore.modify { store =>
      val tag     = implicitly[Tag[A]]
      val current = store.get(tag).asInstanceOf[Option[A]]
      val updated = a(current)
      ((), store.updated(tag, updated))
    }

  /**
   * An aspect that stores the incoming `Request` before the handler runs. Read
   * it back with `getRequest`.
   */
  def storeRequest: HandlerAspect[Any, Unit] =
    Middleware.interceptIncomingHandler(handler((request: Request) => set(request).as((request, ()))))

  /**
   * Returns the `Request` stored by `storeRequest`, or `None` when there is
   * none.
   */
  def getRequest: ZIO[Any, Nothing, Option[Request]] =
    get[Request]

  /**
   * Returns the `Request` stored by `storeRequest`. Fails with a
   * `NoSuchElementException` when there is none.
   */
  def getRequestOrFail: ZIO[Any, NoSuchElementException, Request] =
    getOrFail[Request]
}
