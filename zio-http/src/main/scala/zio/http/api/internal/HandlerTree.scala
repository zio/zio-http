package zio.http.api.internal

import zio._
import zio.http._
import zio.http.api._

sealed trait HandlerTree[-R, +E] { self =>
  import HandlerTree._

  def add[R1 <: R, E1 >: E](handledAPI: HandledAPI[R1, E1, Request, Response]): HandlerTree[R1, E1] =
    merge(HandlerTree.single(handledAPI))

  def merge[R1 <: R, E1 >: E](that: HandlerTree[R1, E1]): HandlerTree[R1, E1] =
    (self, that) match {
      case (Branch(map1), Branch(map2)) =>
        Branch(mergeWith(map1, map2)(_ merge _))

      case (Branch(map1), Leaf(handler2)) =>
        Branch(mergeWith(map1, Map(Option.empty[TextCodec[_]] -> Leaf(handler2)))(_ merge _))

      case (Leaf(handler1), Branch(map2)) =>
        Branch(mergeWith(Map(Option.empty[TextCodec[_]] -> Leaf(handler1)), map2)(_ merge _))

      case (_, right) =>
        right

    }

  def lookup(request: Request): Option[HandlerMatch[R, E, Request, Response]] =
    HandlerTree.lookup(request.path.segments.collect { case Path.Segment.Text(text) => text }, 0, self, Chunk.empty)

  // lazy val maxAtoms: Int = ???

  private def mergeWith[K, V](left: Map[K, V], right: Map[K, V])(f: (V, V) => V): Map[K, V] =
    left.foldLeft(right) { case (acc, (k, v)) =>
      acc.get(k) match {
        case Some(v2) => acc.updated(k, f(v, v2))
        case None     => acc.updated(k, v)
      }
    }
}

object HandlerTree {

  val empty: HandlerTree[Any, Nothing] =
    Branch(Map.empty)

  def single[R, E](handledAPI: HandledAPI[R, E, Request, Response]): HandlerTree[R, E] = {
    val routeCodecs =
      In.flatten(handledAPI.api.in).routes

    routeCodecs.foldRight[HandlerTree[R, E]](Leaf(handledAPI)) { case (codec, acc) =>
      Branch(Map(Some(codec) -> acc))
    }
  }

  def fromIterable[R, E](handledAPIs: Iterable[HandledAPI[R, E, Request, Response]]): HandlerTree[R, E] =
    handledAPIs.foldLeft[HandlerTree[R, E]](empty)(_ add _)

  private final case class Leaf[-R, +E](handledApi: HandledAPI[R, E, Request, Response]) extends HandlerTree[R, E]

  private final case class Branch[-R, +E](
    children: Map[Option[TextCodec[_]], HandlerTree[R, E]],
  ) extends HandlerTree[R, E]

  // TODO: optimize (tailrec, etc.)
  private def lookup[R, E](
    segments: Vector[String],
    index: Int,
    current: HandlerTree[R, E],
    results: Chunk[Any],
  ): Option[HandlerMatch[R, E, Request, Response]] =
    current match {
      case Leaf(handler) =>
        if (index < segments.length) None
        else Some(HandlerMatch(handler, results))
      case Branch(map)   =>
        if (index == segments.length)
          map.get(None) match {
            case Some(handlerTree) =>
              lookup(segments, index, handlerTree, results)
            case None              =>
              None
          }
        else
          map.collectFirst { case (Some(codec), handler) =>
            codec.decode(segments(index)) match {
              case Some(value) =>
                lookup(segments, index + 1, handler, results :+ value)
              case None        =>
                None
            }
          }.flatten
    }
}

object HandlerTreeExamples extends ZIOAppDefault {
  import In._

  val api1: HandledAPI[Any, Nothing, Int, Unit] =
    API.get(literal("users") / int).handle { case (id: Int) => ZIO.debug(s"API1 RESULT parsed: users/$id") }

  val api2: HandledAPI[Any, Nothing, (Int, String, Int), Unit] =
    API
      .get(literal("users") / int / literal("posts") / query("name") / int)
      .handle { case (id1, query, id2) =>
        ZIO.debug(s"API2 RESULT parsed: users/$id1/posts/$id2?name=$query")
      }

  def cast[R, E](handled: HandledAPI[R, E, _, _]): HandledAPI[R, E, Request, Response] =
    handled.asInstanceOf[HandledAPI[R, E, Request, Response]]

  val tree: HandlerTree[Any, Nothing] = HandlerTree.fromIterable(Chunk(cast(api1), cast(api2)))

  val request = Request(url = URL.fromString("/users/100/posts/200?name=adam").toOption.get)
  println(s"Looking up $request")

  val result = tree.lookup(request).get
  println(s"Match Results: ${result.results}")
  println(s"Match API: ${api2 == result.handledApi}")

  val run = result.run(request).debug("run")
}
