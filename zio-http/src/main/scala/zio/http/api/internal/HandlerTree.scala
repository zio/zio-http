package zio.http.api.internal

import zio._
import zio.http._
import zio.http.api._
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;
import scala.annotation.tailrec

final case class HandlerTree[-R, +E, M <: EndpointMiddleware](
  constants: Map[String, HandlerTree[R, E, M]],
  parsers: Map[TextCodec[_], HandlerTree[R, E, M]],
  leaf: Option[Routes.Single[R, _ <: E, _, _, M]],
) { self =>

  def add[R1 <: R, E1 >: E](handledAPI: Routes.Single[R1, E1, _, _, M]): HandlerTree[R1, E1, M] =
    merge(HandlerTree.single(handledAPI))

  def generateError(request: Request): String = s"The path ${request.path} does not match any route"

  def merge[R1 <: R, E1 >: E](that: HandlerTree[R1, E1, M]): HandlerTree[R1, E1, M] = {
    HandlerTree[R1, E1, M](
      mergeWith(self.constants, that.constants)(_ merge _),
      mergeWith(self.parsers, that.parsers)(_ merge _),
      self.leaf.map(_.asInstanceOf[Routes.Single[R1, E1, Any, Any, M]]).orElse(that.leaf),
    )
  }

  def lookup(request: Request): Option[HandlerMatch[R, E, _, _, M]] = {
    val segments = request.path.segments.collect { case Path.Segment.Text(text) => text }
    HandlerTree.lookup(segments, 0, self)
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

  def empty[M <: EndpointMiddleware]: HandlerTree[Any, Nothing, M] =
    HandlerTree(Map.empty, Map.empty, None)

  def single[R, E, M <: EndpointMiddleware](handledAPI: Routes.Single[R, E, _, _, M]): HandlerTree[R, E, M] = {
    val inputs = handledAPI.endpoint.input.alternatives

    inputs.foldLeft[HandlerTree[R, E, M]](HandlerTree(Map.empty, Map.empty, Some(handledAPI))) { case (acc, input) =>
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

  def fromService[R, E, M <: EndpointMiddleware](service: Routes[R, E, M]): HandlerTree[R, E, M] =
    fromIterable(Routes.flatten(service))

  def fromIterable[R, E, M <: EndpointMiddleware](
    handledAPIs: Iterable[Routes.Single[R, E, _, _, M]],
  ): HandlerTree[R, E, M] =
    handledAPIs.foldLeft[HandlerTree[R, E, M]](empty)(_ add _)

  @tailrec
  private def lookup[R, E, M <: EndpointMiddleware](
    segments: Vector[String],
    index: Int,
    current: HandlerTree[R, E, M],
  ): Option[HandlerMatch[R, E, _, _, M]] =
    if (index == segments.length) {
      // If we've reached the end of the path, we should have a handler
      // otherwise we don't have a match
      current.leaf match {
        case Some(handler) => Some(HandlerMatch(handler))
        case None          => None
      }
    } else {
      val segment = segments(index)
      current.constants.get(segment) match {
        // We can quickly check if we have a constant match
        case Some(handler) =>
          lookup(segments, index + 1, handler)
        case None          =>
          // If we don't have a constant match, we need to check if we have a
          // parser that matches.
          firstSuccessfulCodec(segment, current.parsers) match {
            case Some(handler) =>
              lookup(segments, index + 1, handler)
            case None          =>
              None
          }
      }
    }

  private def firstSuccessfulCodec[R, E, M <: EndpointMiddleware](
    pathSegment: String,
    parsers: Map[TextCodec[_], HandlerTree[R, E, M]],
  ): Option[HandlerTree[R, E, M]] =
    parsers.collectFirst { case (codec, handler) =>
      if (codec.isDefinedAt(pathSegment)) Some(handler) else None
    }.flatten

}
