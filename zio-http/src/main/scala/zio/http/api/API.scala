package zio.http.api

import zio.http.Method
import zio._

// Http[-R, +E, -A, +B] extends (A => ZIO[R, Option[E], B]) { self =>
// TODO: Index Atom
trait Schema[A]

final case class API[Input, Output](
  method: Method,
  in: In[Input],
//  outputCodec: OutputCodec[Input],
) { self =>
  def handle[R, E](f: Input => ZIO[R, E, Output]): HandledAPI[R, E, Input, Output] =
    HandledAPI(self, f)

  def in[Input2](in2: In[Input2])(implicit combiner: Combiner[Input, Input2]): API[combiner.Out, Output] =
    copy(in = self.in ++ in2)
}

object API {
  def delete[Input](route: In[Input]): API[Input, Unit] =
    API(Method.GET, route)

  def get[Input](route: In[Input]): API[Input, Unit] =
    API(Method.GET, route)

  def post[Input](route: In[Input]): API[Input, Unit] =
    API(Method.POST, route)

  def put[Input](route: In[Input]): API[Input, Unit] =
    API(Method.PUT, route)
}

final case class ZippableHandledAPI[-R, +E, Out](
  routeAtoms: Chunk[In.Route[_]],
  headerAtoms: Chunk[In.Header[_]],
  queryAtoms: Chunk[In.Query[_]],
  inputBodyAtom: Option[In.InputBody[_]],
  handler: Chunk[Any] => ZIO[R, E, Out],
)

object ZippableHandledAPI {
  def fromHandledAPI[R, E, Out](api: HandledAPI[R, E, _, Out]): ZippableHandledAPI[R, E, Out] = {
    val flattened                  = In.flatten(api.api.in)
    val routeAtoms                 = flattened.collect { case atom: In.Route[_] => atom }
    val headerAtoms                = flattened.collect { case atom: In.Header[_] => atom }
    val queryAtoms                 = flattened.collect { case atom: In.Query[_] => atom }
    val inputBodyAtom              = flattened.collectFirst { case atom: In.InputBody[_] => atom }
    def handler(chunk: Chunk[Any]) = ZIO.debug(s"Handling $chunk with API $api").asInstanceOf[ZIO[R, E, Out]]
    ZippableHandledAPI(routeAtoms, headerAtoms, queryAtoms, inputBodyAtom, handler)
  }

}

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

object CommonPrefixTesting extends App {
  import In._

  val api1 = API.get(literal("users") / int / literal("posts") / int)
  val api2 = API.get(literal("users") / int)
  val api3 = API.get(literal("users") / int / literal("posts"))

  val handled1 = HandledAPI(api1, (r: Any) => ZIO.debug(s"RESULT 1: $r"))
  val handled2 = HandledAPI(api2, (r: Any) => ZIO.debug(s"RESULT 2: $r"))
  val handled3 = HandledAPI(api3, (r: Any) => ZIO.debug(s"RESULT 3: $r"))

  val zippable1 = ZippableHandledAPI.fromHandledAPI(handled1)
  val zippable2 = ZippableHandledAPI.fromHandledAPI(handled2)
  val zippable3 = ZippableHandledAPI.fromHandledAPI(handled3)

  val combined =
    ZippedHandledAPIs.fromZippableHandledAPI(zippable1) merge
      ZippedHandledAPIs.fromZippableHandledAPI(zippable2) merge
      ZippedHandledAPIs.fromZippableHandledAPI(zippable3)

  println(combined.render)

}
