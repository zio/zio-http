package zhttp.internal

import zio._

object AppPort {
  sealed trait Service {
    def set(n: Int): IO[Nothing, Unit]
    def get: IO[Nothing, Int]
  }

  def live: ZLayer[Any, Nothing, AppPort] = Ref
    .make(0)
    .map(ref => new Live(ref))
    .toLayer

  case class Live(ref: Ref[Int]) extends Service {
    override def set(n: Int): IO[Nothing, Unit] = ref.set(n)

    override def get: IO[Nothing, Int] = ref.get
  }

  def set(n: Int): ZIO[AppPort, Nothing, Unit] = ZIO.accessM(_.get.set(n))
  def get: ZIO[AppPort, Nothing, Int]          = ZIO.accessM[AppPort](_.get.get)
}
