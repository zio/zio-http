package zio.http.endpoint.internal

import zio._

private[http] class MemoizedZIO[K, E, A] private (compute: K => IO[E, A]) { self =>
  private val mapRef: Ref[Map[K, Promise[E, A]]] = Ref.unsafe.make(Map[K, Promise[E, A]]())(Unsafe.unsafe)

  def get(k: K)(implicit trace: Trace): IO[E, A] = {
    ZIO.fiberIdWith { fiberId =>
      for {
        p      <- Promise.make[E, A]
        effect <- mapRef.modify[IO[E, A]] { map =>
          map.get(k) match {
            case Some(promise) => (promise.await, map)
            case None          =>
              val promise = Promise.unsafe.make[E, A](fiberId)(Unsafe.unsafe)
              (compute(k).exit.tap(exit => promise.done(exit)).flatten, map + (k -> promise))
          }
        }
        result <- effect
      } yield result
    }
  }
}
private[http] object MemoizedZIO                                          {
  def apply[K, E, A](compute: K => IO[E, A]): MemoizedZIO[K, E, A] = new MemoizedZIO(compute)
}
