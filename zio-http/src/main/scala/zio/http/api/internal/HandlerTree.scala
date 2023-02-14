package zio.http.api.internal

import zio._
import zio.http._
import zio.http.api._
import zio.http.model.Method
import zio.stacktracer.TracingImplicits.disableAutoTrace

import scala.annotation.tailrec

case class HandlerTree[-R, +E](
  constants: Map[String, HandlerTree[R, E]],
  parsers: Map[TextCodec[_], HandlerTree[R, E]],
  methodConstants: Map[Method, HandlerTree[R, E]],
  methodParsers: Map[TextCodec[_], HandlerTree[R, E]],
  leaf: Option[Endpoints.HandledEndpoint[R, E, _, _, _]],
) { self =>

  def add[R1 <: R, E1 >: E](handledAPI: Endpoints.HandledEndpoint[R1, E1, _, _, _]): HandlerTree[R1, E1] =
    merge(HandlerTree.single(handledAPI))

  def generateError(request: Request): String = s"The path ${request.path} does not match any route"

  def merge[R1 <: R, E1 >: E](that: HandlerTree[R1, E1]): HandlerTree[R1, E1] =
    (self, that) match {
      case (
            HandlerTree(constants1, parsers1, methodConstants1, methodParsers1, leaf),
            HandlerTree(constants2, parsers2, methodConstants2, methodParsers2, leaf2),
          ) =>
        HandlerTree(
          mergeWith(constants1, constants2)(_ merge _),
          mergeWith(parsers1, parsers2)(_ merge _),
          mergeWith(methodConstants1, methodConstants2)(_ merge _),
          mergeWith(methodParsers1, methodParsers2)(_ merge _),
          leaf orElse leaf2,
        )
    }

  def lookup(request: Request): Option[HandlerMatch[R, E, _, _]] = {
    val segments = request.path.segments.collect { case Path.Segment.Text(text) => text }
    HandlerTree.lookup(segments, request.method, 0, self, Chunk.empty)
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
    HandlerTree(
      constants = Map.empty,
      parsers = Map.empty,
      methodConstants = Map.empty,
      methodParsers = Map.empty,
      leaf = None,
    )

  def single[R, E](handledAPI: Endpoints.HandledEndpoint[R, E, _, _, _]): HandlerTree[R, E] = {
    val inputs = handledAPI.endpointSpec.input.alternatives

    inputs.foldLeft[HandlerTree[R, E]](HandlerTree.empty) { case (acc, input) =>
      val routeCodecs  = Mechanic.flatten(input).routes
      val methodCodecs = Mechanic.flatten(input).methods

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

  def fromService[R, E](service: Endpoints[R, E, _]): HandlerTree[R, E] =
    fromIterable(Endpoints.flatten(service))

  def fromIterable[R, E](handledAPIs: Iterable[Endpoints.HandledEndpoint[R, E, _, _, _]]): HandlerTree[R, E] =
    handledAPIs.foldLeft[HandlerTree[R, E]](empty)(_ add _)

  @tailrec
  private def lookup[R, E](
    segments: Vector[String],
    method: Method,
    index: Int,
    current: HandlerTree[R, E],
    results: Chunk[Any],
  ): Option[HandlerMatch[R, E, _, _]] =
    if (index == segments.length) {
      // If we've reached the end of the path, next we have to match on method
      // finally we match on method
      lookupByMethod(method, current, results)
    } else {
      val segment = segments(index)
      current.constants.get(segment) match {
        // We can quickly check if we have a constant match
        case Some(handler) =>
          lookup(segments, method, index + 1, handler, results.:+(()))
        case None          =>
          // If we don't have a constant match, we need to check if we have a
          // parser that matches.
          firstSuccessfulCodec(segment, current.parsers) match {
            case Some((result, handler)) =>
              lookup(segments, method, index + 1, handler, results.:+(result))
            case None                    =>
              None
          }
      }
    }

  @tailrec
  private def lookupByMethod[R, E](
    method: Method,
    current: HandlerTree[R, E],
    results: Chunk[Any],
  ): Option[HandlerMatch[R, E, _, _]] =
    current.leaf match {
      case Some(handler) =>
        Some(HandlerMatch(handler, results))
      case None          =>
        current.methodConstants.get(method) match {
          case Some(handler) =>
            lookupByMethod(method, handler, results)
          case None          =>
            firstSuccessfulCodec(method.text, current.methodParsers) match {
              case Some((result, handler)) =>
                lookupByMethod(method, handler, results.:+(result))
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
