package zio.http.api.internal

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;
class Memoized[K, A] private (compute: K => A) { self =>
  private var map: Map[K, A] = Map()

  def get(api: K): A = {
    map.get(api) match {
      case Some(a) => a
      case None    =>
        val a = compute(api)
        map = map.updated(api, a)
        a
    }
  }
}
object Memoized                                {
  def apply[K, A](compute: K => A): Memoized[K, A] = new Memoized(compute)
}
