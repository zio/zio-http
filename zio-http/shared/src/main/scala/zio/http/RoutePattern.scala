/*
 * Copyright 2023 the ZIO HTTP contributors.
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

import scala.collection.immutable.ListMap
import scala.language.implicitConversions

import zio._

import zio.http.codec._

/**
 * A pattern for matching routes that examines both HTTP method and path. In
 * addition to specifying a method, patterns contain segment patterns, which are
 * sequences of literals, integers, longs, and other segment types.
 *
 * Typically, your entry point constructor for a route pattern would be
 * [[zio.http.Method]]:
 *
 * {{{
 * import zio.http.Method
 * import zio.http.codec.SegmentCodec._
 *
 * val pattern = Method.GET / "users" / int("user-id") / "posts" / string("post-id")
 * }}}
 *
 * However, you can use the convenience constructors in `RoutePattern`, such as
 * `RoutePattern.GET`.
 */
final case class RoutePattern[A](method: Method, pathCodec: PathCodec[A]) { self =>
  type Params = A

  /**
   * Attaches documentation to the route pattern, which may be used when
   * generating developer docs for a route.
   */
  def ??(doc: Doc): RoutePattern[A] = copy(pathCodec = pathCodec ?? doc)

  /**
   * Returns a new pattern that is extended with the specified segment pattern.
   */
  def /[B](that: PathCodec[B])(implicit combiner: Combiner[A, B]): RoutePattern[combiner.Out] =
    if (that == PathCodec.empty) self.asInstanceOf[RoutePattern[combiner.Out]]
    else if (pathCodec == PathCodec.empty) copy(pathCodec = that.asInstanceOf[PathCodec[combiner.Out]])
    else copy(pathCodec = pathCodec ++ that)

  /**
   * Creates a route from this pattern and the specified handler.
   */
  def ->[Env, Err, I](handler: Handler[Env, Err, I, Response])(implicit
    zippable: RequestHandlerInput[A, I],
    trace: zio.Trace,
  ): Route[Env, Err] =
    Route.route(self)(handler)(zippable.zippable, trace)

  /**
   * Creates a route from this pattern and the specified handler, which ignores
   * any parameters produced by this route pattern. This method exists for
   * performance reasons, as it avoids all overhead of propagating parameters or
   * supporting contextual middleware.
   */
  def ->[Env, Err](handler: Handler[Env, Response, Request, Response])(implicit
    trace: zio.Trace,
  ): Route[Env, Err] =
    Route.handledIgnoreParams(self)(handler)

  def alternatives: List[RoutePattern[A]] = pathCodec.alternatives.map(RoutePattern(method, _))

  /**
   * Reinteprets the type parameter, given evidence it is equal to some other
   * type.
   */
  def asType[B](implicit ev: A =:= B): RoutePattern[B] = self.asInstanceOf[RoutePattern[B]]

  /**
   * Decodes a method and path into a value of type `A`.
   */
  def decode(actual: Method, path: Path): Either[String, A] =
    if (!method.matches(actual)) {
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
  def encode(value: A): Either[String, (Method, Path)] = format(value).map((method, _))

  /**
   * Formats a value of type `A` into a path. This is useful for embedding paths
   * into HTML that is rendered by the server.
   */
  def format(value: A): Either[String, Path] = pathCodec.format(value)

  /**
   * Determines if this pattern matches the specified method and path. Rather
   * than use this method, you should just try to decode it directly, for higher
   * performance, otherwise the same information will be decoded twice.
   */
  def matches(method: Method, path: Path): Boolean = decode(method, path).isRight

  def nest(prefix: PathCodec[Unit]): RoutePattern[A] =
    copy(pathCodec = prefix ++ pathCodec)

  /**
   * Renders the route pattern as a string.
   */
  def render: String =
    s"${method.render} ${pathCodec.render}"

  /**
   * Converts the route pattern into an HttpCodec that produces the same value.
   */
  def toHttpCodec: HttpCodec[HttpCodecType.Path with HttpCodecType.Method, A] =
    MethodCodec.method(method) ++ HttpCodec.Path(pathCodec)

  override def toString: String = render

  /**
   * This exists for use with Scala custom extractor syntax, allowing route
   * patterns to match against and deconstruct tuples of methods and paths.
   */
  def unapply(tuple: (Method, Path)): Option[A] =
    decode(tuple._1, tuple._2).toOption
}
object RoutePattern                                                       {
  import PathCodec.SegmentSubtree

  val CONNECT: RoutePattern[Unit] = fromMethod(Method.CONNECT)
  val DELETE: RoutePattern[Unit]  = fromMethod(Method.DELETE)
  val GET: RoutePattern[Unit]     = fromMethod(Method.GET)
  val HEAD: RoutePattern[Unit]    = fromMethod(Method.HEAD)
  val OPTIONS: RoutePattern[Unit] = fromMethod(Method.OPTIONS)
  val PATCH: RoutePattern[Unit]   = fromMethod(Method.PATCH)
  val POST: RoutePattern[Unit]    = fromMethod(Method.POST)
  val PUT: RoutePattern[Unit]     = fromMethod(Method.PUT)
  val TRACE: RoutePattern[Unit]   = fromMethod(Method.TRACE)

  /**
   * Constructs a route pattern from a method.
   */
  def fromMethod(method: Method): RoutePattern[Unit] = RoutePattern(method, PathCodec.empty)

  /**
   * A tree of route patterns, indexed by method and path.
   */
  private[http] final case class Tree[+A](
    anyRoot: SegmentSubtree[A],
    connectRoot: SegmentSubtree[A],
    deleteRoot: SegmentSubtree[A],
    getRoot: SegmentSubtree[A],
    headRoot: SegmentSubtree[A],
    optionsRoot: SegmentSubtree[A],
    patchRoot: SegmentSubtree[A],
    postRoot: SegmentSubtree[A],
    putRoot: SegmentSubtree[A],
    traceRoot: SegmentSubtree[A],
    customRoots: Map[Method, SegmentSubtree[A]],
  ) { self =>
    def ++[A1 >: A](that: Tree[A1]): Tree[A1] =
      Tree(
        if (self.anyRoot != null) self.anyRoot ++ that.anyRoot else that.anyRoot,
        if (self.connectRoot != null) self.connectRoot ++ that.connectRoot else that.connectRoot,
        if (self.deleteRoot != null) self.deleteRoot ++ that.deleteRoot else that.deleteRoot,
        if (self.getRoot != null) self.getRoot ++ that.getRoot else that.getRoot,
        if (self.headRoot != null) self.headRoot ++ that.headRoot else that.headRoot,
        if (self.optionsRoot != null) self.optionsRoot ++ that.optionsRoot else that.optionsRoot,
        if (self.patchRoot != null) self.patchRoot ++ that.patchRoot else that.patchRoot,
        if (self.postRoot != null) self.postRoot ++ that.postRoot else that.postRoot,
        if (self.putRoot != null) self.putRoot ++ that.putRoot else that.putRoot,
        if (self.traceRoot != null) self.traceRoot ++ that.traceRoot else that.traceRoot,
        mergeMaps(self.customRoots, that.customRoots)(_ ++ _),
      )

    def add[A1 >: A](routePattern: RoutePattern[_], value: A1): Tree[A1] =
      self ++ Tree(routePattern, value)

    def addAll[A1 >: A](pathPatterns: Iterable[(RoutePattern[_], A1)]): Tree[A1] =
      pathPatterns.foldLeft[Tree[A1]](self) { case (tree, (p, v)) =>
        tree.add(p, v)
      }

    def get(method: Method, path: Path): Chunk[A] = {
      val forMethod = {
        method match {
          case Method.GET if getRoot != null               => getRoot.get(path)
          case Method.POST if postRoot != null             => postRoot.get(path)
          case Method.PUT if putRoot != null               => putRoot.get(path)
          case Method.DELETE if deleteRoot != null         => deleteRoot.get(path)
          case Method.CONNECT if connectRoot != null       => connectRoot.get(path)
          case Method.HEAD if headRoot != null             => headRoot.get(path)
          case Method.OPTIONS if optionsRoot != null       => optionsRoot.get(path)
          case Method.PATCH if patchRoot != null           => patchRoot.get(path)
          case Method.TRACE if traceRoot != null           => traceRoot.get(path)
          case Method.ANY if anyRoot != null               => anyRoot.get(path)
          case m: Method.CUSTOM if customRoots.contains(m) => customRoots(m).get(path)
          case _                                           => Chunk.empty
        }
      }

      if (anyRoot eq null) forMethod
      else {
        forMethod ++ anyRoot.get(path)
      }
    }

    private[http] def getAllMethods(path: Path): Set[Method] = {
      roots.collect {
        case (method, subtree) if subtree.get(path).nonEmpty => method
      }.toSet
    }

    def map[B](f: A => B): Tree[B] =
      Tree(
        if (anyRoot != null) anyRoot.map(f) else null,
        if (connectRoot != null) connectRoot.map(f) else null,
        if (deleteRoot != null) deleteRoot.map(f) else null,
        if (getRoot != null) getRoot.map(f) else null,
        if (headRoot != null) headRoot.map(f) else null,
        if (optionsRoot != null) optionsRoot.map(f) else null,
        if (patchRoot != null) patchRoot.map(f) else null,
        if (postRoot != null) postRoot.map(f) else null,
        if (putRoot != null) putRoot.map(f) else null,
        if (traceRoot != null) traceRoot.map(f) else null,
        customRoots.map { case (k, v) => k -> v.map(f) },
      )
  }
  private[http] object Tree {
    def apply[A](routePattern: RoutePattern[_], value: A): Tree[A] = {
      val segments = routePattern.pathCodec.segments

      val subtree = SegmentSubtree.single(segments, value)

      routePattern.method match {
        case Method.GET       => Tree.empty.copy(getRoot = subtree)
        case Method.POST      => Tree.empty.copy(postRoot = subtree)
        case Method.PUT       => Tree.empty.copy(putRoot = subtree)
        case Method.DELETE    => Tree.empty.copy(deleteRoot = subtree)
        case Method.CONNECT   => Tree.empty.copy(connectRoot = subtree)
        case Method.HEAD      => Tree.empty.copy(headRoot = subtree)
        case Method.OPTIONS   => Tree.empty.copy(optionsRoot = subtree)
        case Method.PATCH     => Tree.empty.copy(patchRoot = subtree)
        case Method.TRACE     => Tree.empty.copy(traceRoot = subtree)
        case Method.ANY       => Tree.empty.copy(anyRoot = subtree)
        case m: Method.CUSTOM => Tree.empty.copy(customRoots = ListMap(m -> subtree))
      }
    }

    val empty: Tree[Nothing] = Tree(
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      null,
      Map.empty,
    )
  }

  private def mergeMaps[A, B](left: Map[A, B], right: Map[A, B])(f: (B, B) => B): Map[A, B] =
    right.foldLeft(left) { case (acc, (k, v)) =>
      val old = acc.get(k)

      old match {
        case None     => acc.updated(k, v)
        case Some(v0) => acc.updated(k, f(v0, v))
      }
    }

  /**
   * The any pattern matches any method and any path. It is unlikely you need to
   * use this pattern, because it would preclude the use of any other route (at
   * least, unless listed as the final route in a collection of routes).
   */
  val any: RoutePattern[Path] = RoutePattern(Method.ANY, PathCodec.trailing)

  def apply(method: Method, path: Path): RoutePattern[Unit] =
    path.segments.foldLeft[RoutePattern[Unit]](fromMethod(method)) { (pathSpec, segment) =>
      pathSpec./[Unit](PathCodec.Segment(SegmentCodec.literal(segment)))
    }

  /**
   * Constructs a route pattern from a method and a path literal. To match
   * against any method, use [[zio.http.Method.ANY]]. The specified string may
   * contain path segments, which are separated by slashes.
   */
  def apply(method: Method, pathString: String): RoutePattern[Unit] =
    apply(method, Path(pathString))
}
