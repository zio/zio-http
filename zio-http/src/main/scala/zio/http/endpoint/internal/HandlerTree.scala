package zio.http.endpoint.internal

import zio._
import zio.http._
import zio.http.endpoint._
import zio.http.model.Method
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.annotation.tailrec

private[zio] case class HandlerTree[-R, E, M <: EndpointMiddleware](
  constants: Map[String, HandlerTree[R, E, M]],
  parsers: Map[TextCodec[_], HandlerTree[R, E, M]],
  methodConstants: Map[Method, HandlerTree[R, E, M]],
  methodParsers: Map[TextCodec[_], HandlerTree[R, E, M]],
  leaf: Option[Routes.Single[R, E, _, _, M]],
) { self =>

  private[zio] def add[R1 <: R](handledAPI: Routes.Single[R1, E, _, _, M]): HandlerTree[R1, E, M] =
    merge(HandlerTree.single(handledAPI))

  private[zio] def generateError(request: Request): String = s"The path ${request.path} does not match any route"

  private[zio] def merge[R1 <: R](that: HandlerTree[R1, E, M]): HandlerTree[R1, E, M] =
    (self, that) match {
      case (
            HandlerTree(constants1, parsers1, methodConstants1, methodParsers1, leaf),
            HandlerTree(constants2, parsers2, methodConstants2, methodParsers2, leaf2),
          ) =>
        HandlerTree[R1, E, M](
          mergeWith(constants1, constants2)(_ merge _),
          mergeWith(parsers1, parsers2)(_ merge _),
          mergeWith(methodConstants1, methodConstants2)(_ merge _),
          mergeWith(methodParsers1, methodParsers2)(_ merge _),
          leaf orElse leaf2,
        )
    }

  private[zio] def lookup(request: Request): Option[HandlerMatch[R, E, _, _, M]] = {
    val segments = request.path.segments.collect { case Path.Segment.Text(text) => text }
    HandlerTree.lookup(segments, request.method, 0, self)
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

  def empty[E, M <: EndpointMiddleware]: HandlerTree[Any, E, M] =
    HandlerTree(
      constants = Map.empty,
      parsers = Map.empty,
      methodConstants = Map.empty,
      methodParsers = Map.empty,
      leaf = None,
    )

  def single[R, E, M <: EndpointMiddleware](handledAPI: Routes.Single[R, E, _, _, M]): HandlerTree[R, E, M] = {
    val inputs = handledAPI.endpoint.input.alternatives

    inputs.foldLeft[HandlerTree[R, E, M]](HandlerTree.empty[E, M]) { case (acc, input) =>
      val routeCodecs  = Mechanic.flatten(input).path
      val methodCodecs = Mechanic.flatten(input).method

      acc.merge(
        methodCodecs.foldRight(
          HandlerTree(
            constants = Map.empty,
            parsers = Map.empty,
            methodConstants = Map.empty,
            methodParsers = Map.empty,
            leaf = Some(handledAPI),
          ),
        ) { case (methodCodec, acc) =>
          val next = methodCodec match {
            case TextCodec.Constant(string) =>
              HandlerTree(
                constants = Map.empty,
                parsers = Map.empty,
                methodConstants = Map(Method.fromString(string) -> acc),
                methodParsers = Map.empty,
                leaf = None,
              )
            case _                          =>
              HandlerTree(
                constants = Map.empty,
                parsers = Map.empty,
                methodConstants = Map.empty,
                methodParsers = Map(methodCodec -> acc),
                leaf = None,
              )
          }
          routeCodecs.foldRight(
            next,
          ) { case (codec, acc) =>
            codec match {
              case TextCodec.Constant(string) =>
                HandlerTree(
                  constants = Map(string -> acc),
                  parsers = Map.empty,
                  methodConstants = Map.empty,
                  methodParsers = Map.empty,
                  leaf = None,
                )
              case codec                      =>
                HandlerTree(
                  constants = Map.empty,
                  parsers = Map(codec -> acc),
                  methodConstants = Map.empty,
                  methodParsers = Map.empty,
                  leaf = None,
                )
            }
          }
        },
      )
    }
  }

  def fromService[R, E, M <: EndpointMiddleware](service: Routes[R, E, M]): HandlerTree[R, E, M] =
    fromIterable(Routes.flatten(service))

  def fromIterable[R, E, M <: EndpointMiddleware](
    handledAPIs: Iterable[Routes.Single[R, E, _, _, M]],
  ): HandlerTree[R, E, M] =
    handledAPIs.foldLeft[HandlerTree[R, E, M]](HandlerTree.empty[E, M])(_ add _)

  @tailrec
  private def lookup[R, E, M <: EndpointMiddleware](
    segments: Vector[String],
    method: Method,
    index: Int,
    current: HandlerTree[R, E, M],
  ): Option[HandlerMatch[R, E, _, _, M]] =
    if (index == segments.length) {
      // If we've reached the end of the path, next we have to match on method
      // finally we match on method
      lookupByMethod(method, current)
    } else {
      val segment = segments(index)
      current.constants.get(segment) match {
        // We can quickly check if we have a constant match
        case Some(handler) =>
          lookup(segments, method, index + 1, handler)
        case None          =>
          // If we don't have a constant match, we need to check if we have a
          // parser that matches.
          firstSuccessfulCodec(segment, current.parsers) match {
            case Some(handler) =>
              lookup(segments, method, index + 1, handler)
            case None          =>
              None
          }
      }
    }

  @tailrec
  private def lookupByMethod[R, E, M <: EndpointMiddleware](
    method: Method,
    current: HandlerTree[R, E, M],
  ): Option[HandlerMatch[R, E, _, _, M]] =
    current.leaf match {
      case Some(handler) =>
        Some(HandlerMatch(handler))
      case None          =>
        current.methodConstants.get(method) match {
          case Some(handler) =>
            lookupByMethod(method, handler)
          case None          =>
            firstSuccessfulCodec(method.text, current.methodParsers) match {
              case Some(handler) =>
                lookupByMethod(method, handler)
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
