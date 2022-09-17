package zio.http.api.internal

import zio.http.api.API

class APIMetadata[A] private (compute: API[_, _] => A) { self =>
  private var map: Map[API[_, _], A] = Map()

  def get(api: API[_, _]): A = {
    map.get(api) match {
      case Some(a) => a
      case None    =>
        val a = compute(api)
        map = map.updated(api, a)
        a
    }
  }
}
object APIMetadata                                     {
  def apply[A](compute: API[_, _] => A): APIMetadata[A] = new APIMetadata(compute)
}
