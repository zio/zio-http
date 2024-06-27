package zio.http

import zio.{FiberRef, Tag, Unsafe, ZIO}

object RequestStore {

  private[http] val requestStore: FiberRef[Map[Tag[_], Any]] =
    FiberRef.unsafe.make[Map[Tag[_], Any]](Map.empty)(Unsafe.unsafe)

  def get[A: Tag]: ZIO[Any, Nothing, Option[A]] =
    requestStore.get.map(_.get(implicitly[Tag[A]]).asInstanceOf[Option[A]])

  def set[A: Tag](a: A): ZIO[Any, Nothing, Unit] =
    requestStore.update(_.updated(implicitly[Tag[A]], a))

  def update[A: Tag](a: Option[A] => A): ZIO[Any, Nothing, Unit] =
    for {
      current <- get[A]
      _       <- set(a(current))
    } yield ()

}
