/*
 * Copyright 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
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
package zio.http

import scala.annotation.tailrec
import scala.collection.immutable.ListMap
import scala.language.implicitConversions

import zio.{Chunk, NonEmptyChunk}

import zio.http.codec._

/**
 * A pattern for matching paths. Patterns may contain literals or variables,
 * such as integer, long, string, or UUID variables.
 *
 * Typically, your entry point constructor for a route pattern would be Method:
 *
 * {{{
 * import zio.http.Method
 * import zio.http.RoutePattern.Segment._
 *
 * val routePattern = Method.GET / "users" / int("user-id") / "posts" / string("post-id")
 * }}}
 */
final case class RoutePattern[A](method: Method, pathCodec: PathCodec[A]) { self =>

  /**
   * Attaches documentation to the route pattern, which may be used when
   * generating developer docs for a route.
   */
  def ??(doc: Doc): RoutePattern[A] = copy(pathCodec = pathCodec ?? doc)

  /**
   * Returns a new pattern that is extended with the specified segment pattern.
   */
  final def /[B](segment: SegmentCodec[B])(implicit combiner: Combiner[A, B]): RoutePattern[combiner.Out] =
    copy(pathCodec = pathCodec / segment)

  /**
   * Creates a route from this pattern and the specified handler.
   */
  final def ->[Env, Err](handler: Handler[Env, Err, Request, Response])(implicit ev: A =:= Unit): Route[Env, Err] =
    Route.unhandled(self.asType[Unit], (_: Unit) => handler)

  /**
   * Creates a route from this pattern and the specified handler.
   */
  final def ->[Env, Err](handler: A => Handler[Env, Err, Request, Response]): Route[Env, Err] =
    Route.unhandled(self, handler)

  final def asType[B](implicit ev: A =:= B): RoutePattern[B] = self.asInstanceOf[RoutePattern[B]]

  /**
   * Decodes a method and path into a value of type `A`.
   */
  final def decode(actual: Method, path: Path): Either[String, A] =
    if (actual != method) {
      Left(s"Expected HTTP method ${method} but found method ${actual}")
    } else pathCodec.decode(path)

  /**
   * Returns the documentation for the route pattern, if any.
   */
  def doc: Doc = pathCodec.doc

  /**
   * Encodes a value of type `A` into the method and path that this route
   * pattern would successfully match against.
   */
  final def encode(value: A): (Method, Path) = (method, format(value))

  /**
   * Formats a value of type `A` into a path. This is useful for embedding paths
   * into HTML that is rendered by the server.
   */
  final def format(value: A): Path = pathCodec.format(value)

  /**
   * Determines if this pattern matches the specified method and path. Rather
   * than use this method, you should just try to decode it directly, for higher
   * performance, otherwise the same information will be decoded twice.
   */
  final def matches(method: Method, path: Path): Boolean = decode(method, path).isRight

  /**
   * Renders the route pattern as a string.
   */
  def render: String = s"${method.name} ${pathCodec.render}"

  /**
   * Converts the route pattern into an HttpCodec that produces the same value.
   */
  def toHttpCodec: HttpCodec[HttpCodecType.Path with HttpCodecType.Method, A] = ???

  override def toString(): String = render
}
object RoutePattern {
  import PathCodec.SegmentSubtree

  /**
   * A tree of route patterns, indexed by method and path.
   */
  private[http] final case class Tree[+A](roots: ListMap[Method, SegmentSubtree[A]]) { self =>
    def ++[A1 >: A](that: Tree[A1]): Tree[A1] =
      Tree(mergeMaps(self.roots, that.roots)(_ ++ _))

    def add[A1 >: A](routePattern: RoutePattern[_], value: A1): Tree[A1] =
      self ++ Tree(routePattern, value)

    def addAll[A1 >: A](pathPatterns: Iterable[(RoutePattern[_], A1)]): Tree[A1] =
      pathPatterns.foldLeft[Tree[A1]](self) { case (tree, (p, v)) =>
        tree.add(p, v)
      }

    def get(method: Method, path: Path): Chunk[A] =
      roots.get(method) match {
        case None        => Chunk.empty
        case Some(value) => value.get(path)
      }
  }
  private[http] object Tree                                                          {
    def apply[A](routePattern: RoutePattern[_], value: A): Tree[A] = {
      val method   = routePattern.method
      val segments = routePattern.pathCodec.segments

      val subtree = SegmentSubtree.single(segments, value)

      Tree(ListMap(method -> subtree))
    }

    val empty: Tree[Nothing] = Tree(ListMap.empty)
  }

  private def mergeMaps[A, B](left: ListMap[A, B], right: ListMap[A, B])(f: (B, B) => B): ListMap[A, B] =
    right.foldLeft(left) { case (acc, (k, v)) =>
      acc.updatedWith(k) {
        case None     => Some(v)
        case Some(v0) => Some(f(v0, v))
      }
    }

  /**
   * Constructs a route pattern from a method and a path literal.
   */
  def apply(method: Method, value: String): RoutePattern[Unit] = {
    val path = Path(value)

    path.segments.foldLeft[RoutePattern[Unit]](fromMethod(method)) { (pathSpec, segment) =>
      pathSpec./[Unit](SegmentCodec.literal(segment))
    }
  }

  /**
   * Constructs a route pattern from a method.
   */
  def fromMethod(method: Method): RoutePattern[Unit] = RoutePattern(method, PathCodec.root)
}