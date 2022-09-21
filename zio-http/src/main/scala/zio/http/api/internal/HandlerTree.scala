package zio.http.api.internal

import zio._
import zio.http._
import zio.http.api._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

sealed trait HandlerTree[-R, +E] { self =>
  import HandlerTree._

  def add[R1 <: R, E1 >: E](handledAPI: Service.HandledAPI[R1, E1, _, _, _]): HandlerTree[R1, E1] =
    merge(HandlerTree.single(handledAPI))

  def generateError(request: Request): String = s"The path ${request.path} does not match any route"

  def merge[R1 <: R, E1 >: E](that: HandlerTree[R1, E1]): HandlerTree[R1, E1] =
    (self, that) match {
      case (Branch(constants1, parsers1, leaf), Branch(constants2, parsers2, leaf2)) =>
        Branch(
          mergeWith(constants1, constants2)(_ merge _),
          mergeWith(parsers1, parsers2)(_ merge _),
          leaf orElse leaf2,
        )
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
    Branch(Map.empty, Map.empty, None)

  def single[R, E](handledAPI: Service.HandledAPI[R, E, _, _, _]): HandlerTree[R, E] = {
    val routeCodecs =
      Mechanic.flatten(handledAPI.api.input).routes

    val result = routeCodecs.foldRight[HandlerTree[R, E]](Branch(Map.empty, Map.empty, Some(handledAPI))) {
      case (codec, acc) =>
        codec match {
          case TextCodec.Constant(string) =>
            Branch(Map(string -> acc), Map.empty, None)
          case codec                      =>
            Branch(Map.empty, Map(codec -> acc), None)
        }
    }

    result
  }

  def fromService[R, E](service: Service[R, E, _]): HandlerTree[R, E] =
    fromIterable(Service.flatten(service))

  def fromIterable[R, E](handledAPIs: Iterable[Service.HandledAPI[R, E, _, _, _]]): HandlerTree[R, E] =
    handledAPIs.foldLeft[HandlerTree[R, E]](empty)(_ add _)

  private final case class Branch[-R, +E](
    constants: Map[String, HandlerTree[R, E]],
    parsers: Map[TextCodec[_], HandlerTree[R, E]],
    leaf: Option[Service.HandledAPI[R, E, _, _, _]],
  ) extends HandlerTree[R, E]

  private def lookup[R, E](
    segments: Vector[String],
    index: Int,
    current: HandlerTree[R, E],
    results: Chunk[Any],
  ): Option[HandlerMatch[R, E, _, _]] = {
    current match {
      case Branch(constants, parsers, leaf) =>
        if (index == segments.length)
          leaf match {
            case Some(handler) =>
              Some(HandlerMatch(handler, results))
            case None          =>
              None
          }
        else {
          constants.get(segments(index)) match {
            case Some(handler) =>
              lookup(segments, index + 1, handler, results :+ ())
            case None          =>
              parsers.collectFirst { case (codec, handler) =>
                codec.decode(segments(index)) match {
                  case Some(value) =>
                    val newResults = results :+ value
                    lookup(segments, index + 1, handler, newResults)
                  case None        =>
                    None
                }
              }.flatten
          }
        }
    }
  }
}
