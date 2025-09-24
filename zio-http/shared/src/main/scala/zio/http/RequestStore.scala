package zio.http

import zio.{Exit, FiberRef, Tag, Unsafe, ZIO}

object RequestStore {

  private[http] val requestStore: FiberRef[Map[Tag[_], Any]] =
    FiberRef.unsafe.make[Map[Tag[_], Any]](Map.empty)(Unsafe.unsafe)

  def get[A: Tag]: ZIO[Any, Nothing, Option[A]] =
    requestStore.get.map(_.get(implicitly[Tag[A]]).asInstanceOf[Option[A]])

  def getOrElse[A: Tag](orElse: => A): ZIO[Any, Nothing, A] =
    get[A].map(_.getOrElse(orElse))

  def getOrFail[A: Tag]: ZIO[Any, NoSuchElementException, A] =
    get[A].flatMap {
      case Some(value) => Exit.succeed(value)
      case None        => Exit.fail(new NoSuchElementException(s"No value found for type ${implicitly[Tag[A]]}"))
    }

  def set[A: Tag](a: A): ZIO[Any, Nothing, Unit] =
    requestStore.update(_.updated(implicitly[Tag[A]], a))

  def update[A: Tag](a: Option[A] => A): ZIO[Any, Nothing, Unit] =
    for {
      current <- get[A]
      _       <- set(a(current))
    } yield ()

  def storeRequest: HandlerAspect[Any, Unit] =
    Middleware.interceptIncomingHandler(handler((request: Request) => set(request).as((request, ()))))

  def getRequest: ZIO[Any, Nothing, Option[Request]] =
    get[Request]

  def getRequestOrFail: ZIO[Any, NoSuchElementException, Request] =
    getOrFail[Request]
}
