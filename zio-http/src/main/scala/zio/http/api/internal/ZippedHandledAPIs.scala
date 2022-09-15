package zio.http.api.internal

import zio._
import zio.http.api._

object ZippedHandledAPIs {
  final case class Handle[R, E](
    handler: Chunk[Any] => ZIO[R, E, Any],
  ) extends ZippedHandledAPIs[R, E]

  final case class Alternatives[R, E](
    // TODO: Maybe make specialized Option. None signifies Root Route
    alternatives: Map[Option[In.Route[_]], ZippedHandledAPIs[R, E]],
  ) extends ZippedHandledAPIs[R, E]

  def fromZippableHandledAPI[R, E](api: ZippableHandledAPI[R, E, _]): ZippedHandledAPIs[R, E] =
    api.routeAtoms.init.foldRight[ZippedHandledAPIs[R, E]](
      Alternatives(
        Map(
          Some(api.routeAtoms.last) -> Handle(chunk =>
            ZIO.debug(s"Handling $chunk with API $api").asInstanceOf[ZIO[R, E, Any]],
          ),
        ),
      ),
    ) { case (route, acc) => Alternatives(Map(Some(route) -> acc)) }

}

// TODO: If we want, further "compress"/"optimize" these maps into sets of chunks
sealed trait ZippedHandledAPIs[-R, +E] { self =>
  import ZippedHandledAPIs._

  // simplified model
  def parse(route: List[String], acc: Chunk[Any] = Chunk.empty): Option[ZIO[R, E, Any]] =
    ???
  // route match {
  //   case Nil =>
  //     self match {
  //       case Handle(handler)            =>
  //         Some(handler(acc))
  //       case Alternatives(alternatives) =>
  //         alternatives.get(None) match {
  //           case Some(Handle(handler)) =>
  //             Some(handler(acc))
  //           case _                     => None
  //         }
  //     }

  //   case head :: tail =>
  //     self match {
  //       case Handle(_)                  => None
  //       case Alternatives(alternatives) =>
  //         alternatives.get(Some(In.Route(???))) match {
  //           case Some(_) =>
  //             ???
  //           case None        =>
  //             // TODO
  //             None
  //         }
  //     }
  // }

  // combine two apis, sharing the same prefix
  def merge[R1 <: R, E1 >: E](that: ZippedHandledAPIs[R1, E1]): ZippedHandledAPIs[R1, E1] = {
    (self.asInstanceOf[ZippedHandledAPIs[_, _]], that.asInstanceOf[ZippedHandledAPIs[_, _]]) match {
      case (Alternatives(map1), Alternatives(map2)) =>
        Alternatives(mergeWith(map1, map2)(_ merge _))
          .asInstanceOf[ZippedHandledAPIs[R1, E1]]

      case (Alternatives(map1), Handle(handler2)) =>
        Alternatives(mergeWith(map1, Map(Option.empty[In.Route[_]] -> Handle(handler2)))(_ merge _))
          .asInstanceOf[ZippedHandledAPIs[R1, E1]]

      case (Handle(handler1), Alternatives(map2)) =>
        Alternatives(mergeWith(Map(Option.empty[In.Route[_]] -> Handle(handler1)), map2)(_ merge _))
          .asInstanceOf[ZippedHandledAPIs[R1, E1]]
      //

      case (_, right) =>
        right.asInstanceOf[ZippedHandledAPIs[R1, E1]] // TODO: Throw exception
    }
  }

  def mergeWith[K, V](left: Map[K, V], right: Map[K, V])(f: (V, V) => V): Map[K, V] =
    left.foldLeft(right) { case (acc, (k, v)) =>
      acc.get(k) match {
        case Some(v2) => acc.updated(k, f(v, v2))
        case None     => acc.updated(k, v)
      }
    }

  def indent(string: String, amount: Int): String =
    string.split("\n").map(s => " " * amount + s).mkString("\n")

  def render: String =
    self match {
      case Alternatives(map) =>
        map.map { case (k, v) => indent(s"$k -> ${v.render}", 2) }.mkString("Alternatives(\n", ",\n", "\n)")

      case Handle(f) => s"Handle($f)"
    }

  // def longestCommonPrefix[A](left: Chunk[A], right: Chunk[A]): Chunk[A] = {
  //   val leftIterator  = left.chunkIterator
  //   val rightIterator = right.chunkIterator
  //   val builder       = ChunkBuilder.make[A]()
  //   var index         = 0
  //   var loop          = true
  //   while (loop && leftIterator.hasNextAt(index) && rightIterator.hasNextAt(index)) {
  //     val leftValue  = leftIterator.nextAt(index)
  //     val rightValue = rightIterator.nextAt(index)
  //     index += 1
  //     if (leftValue == rightValue) builder += leftValue
  //     else loop = false
  //   }
  //   builder.result()
  // }
}

// Alternatives(
//   Chunk(Route(Literal(users)),Route(int)),
//   Chunk(
//     Alternatives(
//       Chunk(Route(Literal(posts)),Route(int)),
//       Chunk(
//         Handle(zio.http.api.experiment.ZippedHandledAPIs$$$Lambda$12/0x00000008000b3840@7d68ef40)
//       )
//    ),
//    Handle(zio.http.api.experiment.ZippedHandledAPIs$$$Lambda$12/0x00000008000b3840@5b0abc94))
// )
