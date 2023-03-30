/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.endpoint.internal

import zio._

import zio.http._
import zio.http.codec.internal.AtomizedCodecs
import zio.http.codec.{SimpleCodec, TextCodec}
import zio.http.endpoint._
import zio.http.model.Method

private[http] sealed trait RoutingTree[-R, E, M <: EndpointMiddleware] { self =>
  final def add[R1 <: R](that: Routes.Single[R1, E, _, _, M]): RoutingTree[R1, E, M] =
    self.merge(RoutingTree.single(that))

  final def lookup(request: Request): Chunk[Routes.Single[R, E, _, _, M]] = {
    val segments = request.path.segments.collect { case Path.Segment.Text(text) => text }

    lookup(segments, 0, request.method)
  }

  protected def lookup(segments: Vector[String], index: Int, method: Method): Chunk[Routes.Single[R, E, _, _, M]]

  def merge[R1 <: R](that: RoutingTree[R1, E, M]): RoutingTree[R1, E, M]

  protected def toPaths[R1 <: R]: RoutingTree.Paths[R1, E, M]
}
private[http] object RoutingTree                                       {
  def single[R, E, I, O, M <: EndpointMiddleware](
    single0: Routes.Single[R, E, I, O, M],
  ): RoutingTree[R, E, M] = {
    // Here we perform a complete rewrite of the endpoint to generate a set of
    // endpoints that is equivalent to the endpoint, but each of which does
    // not use the fallback operator. This allows us to "compile away" fallback
    // and handle it using the relatively dumb process of trying each handler
    // at a given point in the tree until we find one that works. Ultimately,
    // this can be made more efficient by finding common subsequences between
    // the different terms of the alternative and factoring them out to avoid
    // duplicate decoding. But for now, it gets the job done.
    val alternatives = single0.endpoint.input.alternatives.map { input =>
      Routes.Single(endpoint = single0.endpoint.copy(input = input), single0.handler)
    }

    alternatives.foldLeft[RoutingTree[R, E, M]](RoutingTree.empty[E, M]) { case (tree, alternative) =>
      val input    = alternative.endpoint.input
      val atomized = AtomizedCodecs.flatten(input)

      def make(segments: List[TextCodec[_]]): RoutingTree[R, E, M] =
        segments match {
          case Nil =>
            atomized.method.headOption match {
              case Some(SimpleCodec.Specified(method)) =>
                Leaf(
                  Map(method -> Chunk(alternative)),
                  Chunk.empty,
                )

              case Some(SimpleCodec.Unspecified()) =>
                Leaf(
                  Map(),
                  Chunk(((_: Method) => true) -> Chunk(alternative)),
                )
              case None                            =>
                Leaf(
                  Map(Method.GET -> Chunk(alternative)),
                  Chunk.empty,
                )
            }

          case head :: tail =>
            head match {
              case TextCodec.Constant(string) =>
                Paths[R, E, M](
                  Map(string -> make(tail)),
                  Chunk.empty,
                  Leaf(Map.empty, Chunk.empty),
                )

              case codec =>
                Paths[R, E, M](
                  Map.empty,
                  Chunk.single((s => codec.isDefinedAt(s), make(tail))),
                  Leaf(Map.empty, Chunk.empty),
                )
            }
        }

      tree.merge(make(atomized.path.toList))
    }
  }

  def empty[E, M <: EndpointMiddleware]: RoutingTree[Any, E, M] =
    Paths(Map.empty, Chunk.empty, Leaf(Map.empty, Chunk.empty))

  def fromRoutes[R, E, M <: EndpointMiddleware](routes: Routes[R, E, M]): RoutingTree[R, E, M] =
    fromIterable(Routes.flatten(routes))

  def fromIterable[R, E, M <: EndpointMiddleware](
    routes: Iterable[Routes.Single[R, E, _, _, M]],
  ): RoutingTree[R, E, M] =
    routes.foldLeft[RoutingTree[R, E, M]](RoutingTree.empty[E, M])((a, b) => a.add(b))

  final case class Paths[-R, E, M <: EndpointMiddleware](
    literals: Map[String, RoutingTree[R, E, M]],
    variables: Chunk[(String => Boolean, RoutingTree[R, E, M])],
    here: Leaf[R, E, M],
  ) extends RoutingTree[R, E, M] { self =>
    def lookup(segments: Vector[String], index: Int, method: Method): Chunk[Routes.Single[R, E, _, _, M]] =
      if (index > segments.length) {
        Chunk.empty
      } else if (index == segments.length) {
        here.lookup(segments, index, method)
      } else {
        val segment = segments(index)

        literals.get(segment) match {
          case Some(handler) => handler.lookup(segments, index + 1, method)
          case None          =>
            variables.flatMap { case (predicate, handler) =>
              if (predicate(segment)) handler.lookup(segments, index + 1, method) else Chunk.empty
            }
        }
      }

    def merge[R1 <: R](that: RoutingTree[R1, E, M]): RoutingTree[R1, E, M] =
      that match {
        case Paths(literals2, variables2, here2) =>
          Paths(
            mergeMaps(literals, literals2)(_.merge(_)),
            variables ++ variables2,
            here.mergeLeaf(here2),
          )

        case leaf2 @ Leaf(_, _) =>
          Paths(
            literals,
            variables,
            here.mergeLeaf(leaf2),
          )
      }

    def toPaths[R1 <: R]: RoutingTree.Paths[R1, E, M] = self
  }
  final case class Leaf[-R, E, M <: EndpointMiddleware](
    literals: Map[Method, Chunk[Routes.Single[R, E, _, _, M]]],
    custom: Chunk[(Method => Boolean, Chunk[Routes.Single[R, E, _, _, M]])],
  ) extends RoutingTree[R, E, M] { self =>
    def lookup(segments: Vector[String], index: Int, method: Method): Chunk[Routes.Single[R, E, _, _, M]] =
      if (index < segments.length) Chunk.empty
      else {
        val part1 =
          literals.get(method) match {
            case Some(chunk) => chunk
            case None        => Chunk.empty
          }

        val part2 =
          if (custom.nonEmpty) custom.flatMap { case (predicate, route) =>
            if (predicate(method)) route else Chunk.empty
          }
          else Chunk.empty

        part1 ++ part2
      }

    def merge[R1 <: R](that: RoutingTree[R1, E, M]): RoutingTree[R1, E, M] =
      that match {
        case leaf2 @ Leaf(_, _) => mergeLeaf(leaf2)
        case _                  => that.merge(self)
      }

    def mergeLeaf[R1 <: R](that: Leaf[R1, E, M]): Leaf[R1, E, M] =
      Leaf(mergeMaps(literals, that.literals)(_ ++ _), custom ++ that.custom)

    def toPaths[R1 <: R]: RoutingTree.Paths[R1, E, M] =
      Paths(Map.empty, Chunk.empty, self)
  }

  private def mergeMaps[K, V](m1: Map[K, V], m2: Map[K, V])(f: (V, V) => V): Map[K, V] =
    m1.foldLeft(m2) { case (acc, (k, v)) =>
      acc.get(k) match {
        case Some(v2) => acc.updated(k, f(v, v2))
        case None     => acc.updated(k, v)
      }
    }
}
