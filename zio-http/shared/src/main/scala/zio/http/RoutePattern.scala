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

import scala.language.implicitConversions

import zio.http.codec.PathCodec.Opt
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

  def alternatives: List[RoutePattern[A]] = pathCodec.alternatives.flatMap { path =>
    if (method == Method.ANY) Method.standardMethods.map(RoutePattern(_, path))
    else List(RoutePattern(method, path))
  }

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
  def format(value: A): Either[String, Path] =
    pathCodec.format(value).map(_.addLeadingSlash)

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

  private[http] def structureEquals(that: RoutePattern[_]): Boolean = {
    def map: PartialFunction[PathCodec.Opt, Iterable[Opt]] = {
      case _: Opt.Combine           => Nil
      case Opt.SubSegmentOpts(segs) => segs.toList.flatMap(map)
      case _: Opt.MapOrFail         => Nil
      case other                    => List(other)
    }
    def opts(codec: PathCodec[_]): Array[Opt]              = codec.optimize.flatMap(map)
    (method == that.method || that.method == Method.ANY) && opts(self.pathCodec).sameElements(opts(that.pathCodec))
  }

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
  private[http] final case class Tree[Env](
    connectRoot: SegmentSubtree[Env],
    deleteRoot: SegmentSubtree[Env],
    getRoot: SegmentSubtree[Env],
    headRoot: SegmentSubtree[Env],
    optionsRoot: SegmentSubtree[Env],
    patchRoot: SegmentSubtree[Env],
    postRoot: SegmentSubtree[Env],
    putRoot: SegmentSubtree[Env],
    traceRoot: SegmentSubtree[Env],
    customRoots: Map[Method, SegmentSubtree[Env]],
  ) { self =>
    def ++[Env1 >: Env](that: Tree[Env1]): Tree[Env1] =
      Tree(
        if (self.connectRoot != null) self.connectRoot ++ that.connectRoot else that.connectRoot,
        if (self.deleteRoot != null) self.deleteRoot ++ that.deleteRoot else that.deleteRoot,
        if (self.getRoot != null) self.getRoot ++ that.getRoot else that.getRoot,
        if (self.headRoot != null) self.headRoot ++ that.headRoot else that.headRoot,
        if (self.optionsRoot != null) self.optionsRoot ++ that.optionsRoot else that.optionsRoot,
        if (self.patchRoot != null) self.patchRoot ++ that.patchRoot else that.patchRoot,
        if (self.postRoot != null) self.postRoot ++ that.postRoot else that.postRoot,
        if (self.putRoot != null) self.putRoot ++ that.putRoot else that.putRoot,
        if (self.traceRoot != null) self.traceRoot ++ that.traceRoot else that.traceRoot,
        mergeMaps(self.customRoots.asInstanceOf[Map[Method, SegmentSubtree[Env1]]], that.customRoots),
      )

    def add[Env1 >: Env](routePattern: RoutePattern[_], value: RequestHandler[Env1, Response]): Tree[Env1] =
      self ++ Tree(routePattern, value)

    def addAll[Env1 >: Env](pathPatterns: Iterable[(RoutePattern[_], RequestHandler[Env1, Response])]): Tree[Env1] =
      pathPatterns.foldLeft[Tree[Env1]](self.asInstanceOf[Tree[Env1]]) { case (tree, (p, v)) =>
        tree.add(p, v)
      }

    def get(method: Method, path: Path): RequestHandler[Env, Response] = {
      val forMethod: RequestHandler[Env, Response] = {
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
          case m: Method.CUSTOM if customRoots.contains(m) => customRoots(m).get(path)
          case _                                           => null.asInstanceOf[RequestHandler[Env, Response]]
        }
      }

      forMethod
    }

    def map[Env1](f: RequestHandler[Env, Response] => RequestHandler[Env1, Response]): Tree[Env1] =
      Tree(
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
    def apply[Env](routePattern: RoutePattern[_], value: RequestHandler[Env, Response]): Tree[Env] = {
      val segments = routePattern.pathCodec.segments

      val subtree = SegmentSubtree.single(segments, value)

      val empty = Tree.empty[Env]

      routePattern.method match {
        case Method.GET       => empty.copy(getRoot = subtree)
        case Method.POST      => empty.copy(postRoot = subtree)
        case Method.PUT       => empty.copy(putRoot = subtree)
        case Method.DELETE    => empty.copy(deleteRoot = subtree)
        case Method.CONNECT   => empty.copy(connectRoot = subtree)
        case Method.HEAD      => empty.copy(headRoot = subtree)
        case Method.OPTIONS   => empty.copy(optionsRoot = subtree)
        case Method.PATCH     => empty.copy(patchRoot = subtree)
        case Method.TRACE     => empty.copy(traceRoot = subtree)
        case Method.ANY       =>
          empty.copy(
            getRoot = subtree,
            postRoot = subtree,
            putRoot = subtree,
            deleteRoot = subtree,
            connectRoot = subtree,
            headRoot = subtree,
            optionsRoot = subtree,
            patchRoot = subtree,
            traceRoot = subtree,
          )
        case m: Method.CUSTOM => empty.copy(customRoots = Map(m -> subtree))
      }
    }

    def empty[Env]: Tree[Env] = empty0.asInstanceOf[Tree[Env]]

    private val empty0: Tree[Any] = Tree(
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

  private def mergeMaps[B](
    left: Map[Method, SegmentSubtree[B]],
    right: Map[Method, SegmentSubtree[B]],
  ): Map[Method, SegmentSubtree[B]] =
    right.foldLeft(left) { case (acc, (k, v)) =>
      val old = acc.get(k)

      old match {
        case None     => acc.updated(k, v)
        case Some(v0) => acc.updated(k, v0 ++ v)
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
