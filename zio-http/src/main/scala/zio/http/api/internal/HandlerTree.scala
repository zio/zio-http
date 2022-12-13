package zio.http.api.internal

import zio._
import zio.http._
import zio.http.api._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;
import scala.annotation.tailrec

case class HandlerTree[-R, +E](
  constants: Map[String, HandlerTree[R, E]],
  parsers: Map[TextCodec[_], HandlerTree[R, E]],
  leaf: Option[Endpoints.HandledEndpoint[R, _ <: E, _, _, _]],
) { self =>

  def add[R1 <: R, E1 >: E](handledAPI: Endpoints.HandledEndpoint[R1, E1, _, _, _]): HandlerTree[R1, E1] =
    merge(HandlerTree.single(handledAPI))

  def generateError(request: Request): String = s"The path ${request.path} does not match any route"

  def merge[R1 <: R, E1 >: E](that: HandlerTree[R1, E1]): HandlerTree[R1, E1] = {
    HandlerTree[R1, E1](
      mergeWith(self.constants, that.constants)(_ merge _),
      mergeWith(self.parsers, that.parsers)(_ merge _),
      self.leaf.map(_.asInstanceOf[Endpoints.HandledEndpoint[R1, E1, Any, Any, Any]]).orElse(that.leaf),
    )
  }

  def lookup(request: Request): Option[HandlerMatch[R, E, _, _]] = {
    val segments = request.path.segments.collect { case Path.Segment.Text(text) => text }
    HandlerTree.lookup(segments, 0, self, Chunk.empty)
  }

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
    HandlerTree(Map.empty, Map.empty, None)

  def single[R, E](handledAPI: Endpoints.HandledEndpoint[R, E, _, _, _]): HandlerTree[R, E] = {
    val inputs = handledAPI.endpointSpec.input.alternatives

    inputs.foldLeft[HandlerTree[R, E]](HandlerTree(Map.empty, Map.empty, Some(handledAPI))) { case (acc, input) =>
      val routeCodecs = Mechanic.flatten(input).routes

      acc.merge(routeCodecs.foldRight(HandlerTree(Map.empty, Map.empty, Some(handledAPI))) { case (codec, acc) =>
        codec match {
          case TextCodec.Constant(string) =>
            HandlerTree(Map(string -> acc), Map.empty, None)
          case codec                      =>
            HandlerTree(Map.empty, Map(codec -> acc), None)
        }
      })
    }
  }

  def fromService[R, E](service: Endpoints[R, E, _]): HandlerTree[R, E] =
    fromIterable(Endpoints.flatten(service))

  def fromIterable[R, E](handledAPIs: Iterable[Endpoints.HandledEndpoint[R, E, _, _, _]]): HandlerTree[R, E] =
    handledAPIs.foldLeft[HandlerTree[R, E]](empty)(_ add _)

  @tailrec
  private def lookup[R, E](
    segments: Vector[String],
    index: Int,
    current: HandlerTree[R, E],
    results: Chunk[Any],
  ): Option[HandlerMatch[R, E, _, _]] =
    if (index == segments.length) {
      // If we've reached the end of the path, we should have a handler
      // otherwise we don't have a match
      current.leaf match {
        case Some(handler) => Some(HandlerMatch(handler, results))
        case None          => None
      }
    } else {
      val segment = segments(index)
      current.constants.get(segment) match {
        // We can quickly check if we have a constant match
        case Some(handler) =>
          lookup(segments, index + 1, handler, results.:+(()))
        case None          =>
          // If we don't have a constant match, we need to check if we have a
          // parser that matches.
          firstSuccessfulCodec(segment, current.parsers) match {
            case Some((result, handler)) =>
              lookup(segments, index + 1, handler, results.:+(result))
            case None                    =>
              None
          }
      }
    }

  private def firstSuccessfulCodec[R, E](
    pathSegment: String,
    parsers: Map[TextCodec[_], HandlerTree[R, E]],
  ): Option[(Any, HandlerTree[R, E])] =
    parsers.collectFirst { case (codec, handler) =>
      codec.decode(pathSegment) match {
        case Some(value) =>
          Some((value, handler))
        case None        =>
          None
      }
    }.flatten

}
