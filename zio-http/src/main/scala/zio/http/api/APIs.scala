package zio.http.api

import zio.Chunk
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * A collection of individual [[zio.http.api.API]]s.
 */
sealed trait APIs[+Ids] { self =>
  def ++[Ids2](that: APIs[Ids2]): APIs[Ids with Ids2] = APIs.Concat[Ids, Ids2](self, that)

  def flatten: Chunk[API[_, _]] =
    this match {
      case APIs.Concat(a, b) => a.flatten ++ b.flatten
      case APIs.Single(a)    => Chunk.single(a)
    }
}
object APIs             {
  def apply(api: API[_, _]): APIs[api.Id] = Single(api)

  private final case class Single[Id, A, B](api: API[A, B])                    extends APIs[Id]
  private final case class Concat[Id1, Id2](left: APIs[Id1], right: APIs[Id2]) extends APIs[Id1 with Id2]
}
