package zio.http.api.internal

import zio._
import zio.http._
import zio.http.api._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok RemoveUnused.imports;

sealed trait HandlerTree[-R, +E] { self =>
  import HandlerTree._

  def add[R1 <: R, E1 >: E](handledAPI: Service.HandledAPI[R1, E1, _, _, _]): HandlerTree[R1, E1] =
    merge(HandlerTree.single(handledAPI))

  def generateError(request: Request): String = s"The path ${request.path} does not match any route"

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

  def lookup(request: Request): Option[HandlerMatch[R, E, _, _]] =
    HandlerTree.lookup(request.path.segments.collect { case Path.Segment.Text(text) => text }, 0, self, Chunk.empty)

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

  def single[R, E](handledAPI: Service.HandledAPI[R, E, _, _, _]): HandlerTree[R, E] = {
    val routeCodecs =
      Mechanic.flatten(handledAPI.api.input).routes

    routeCodecs.foldRight[HandlerTree[R, E]](Leaf(handledAPI)) { case (codec, acc) =>
      Branch(Map(Some(codec) -> acc))
    }
  }

  def fromService[R, E](service: Service[R, E, _]): HandlerTree[R, E] =
    fromIterable(Service.flatten(service))

  def fromIterable[R, E](handledAPIs: Iterable[Service.HandledAPI[R, E, _, _, _]]): HandlerTree[R, E] =
    handledAPIs.foldLeft[HandlerTree[R, E]](empty)(_ add _)

  private final case class Leaf[-R, +E](handledApi: Service.HandledAPI[R, E, _, _, _]) extends HandlerTree[R, E]

  private final case class Branch[-R, +E](
    children: Map[Option[TextCodec[_]], HandlerTree[R, E]],
  ) extends HandlerTree[R, E]

  // TODO: optimize (tailrec, etc.)
  private def lookup[R, E](
    segments: Vector[String],
    index: Int,
    current: HandlerTree[R, E],
    results: Chunk[Any],
  ): Option[HandlerMatch[R, E, _, _]] =
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
