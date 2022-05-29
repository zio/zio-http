package zhttp.api

import zhttp.http.Request
import zio.schema.Schema

/**
 * A RequestCodec is a description of a Route, Query Parameters, and Headers.:
 *   - Route: /users/:id/posts
 *   - Query Parameters: ?page=1&limit=10
 *   - Headers: X-User-Id: 1 or Accept: application/json
 */
sealed trait RequestCodec[A] extends Product with Serializable { self =>
  private[api] def ++[B](that: RequestCodec[B])(implicit zipper: Zipper[A, B]): RequestCodec[zipper.Out] =
    RequestCodec.ZipWith[A, B, zipper.Out](self, that, zipper.zip(_, _), zipper.unzip)

  def map[B](f: A => B)(g: B => A): RequestCodec[B] =
    RequestCodec.Map(self, f, g)

  private[api] def getRoute: Route[_] = {
    def getRouteImpl(requestCodec: RequestCodec[_]): Option[Route[_]] =
      requestCodec match {
        case RequestCodec.ZipWith(left, right, _, _) =>
          getRouteImpl(left) orElse getRouteImpl(right)
        case RequestCodec.Map(info, _, _)            =>
          getRouteImpl(info)
        case _: Header[_]                            =>
          None
        case _: Query[_]                             =>
          None
        case route: Route[_]                         =>
          Some(route)
      }

    getRouteImpl(self).get
  }

  private[api] def getQueryParams: Option[Query[_]] =
    self match {
      case zip: RequestCodec.ZipWith[_, _, _] =>
        (zip.left.getQueryParams, zip.right.getQueryParams) match {
          case (Some(left), Some(right)) => Some(left ++ right)
          case (Some(left), None)        => Some(left)
          case (None, Some(right))       => Some(right)
          case (None, None)              => None
        }
      case RequestCodec.Map(info, _, _)       =>
        info.getQueryParams
      case route: Query[_]                    =>
        Some(route)
      case _: Header[_]                       =>
        None
      case _: Route[_]                        =>
        None
    }

  private[api] def getHeaders: Option[Header[_]] =
    self match {
      case zip: RequestCodec.ZipWith[_, _, _] =>
        (zip.left.getHeaders, zip.right.getHeaders) match {
          case (Some(left), Some(right)) => Some(left ++ right)
          case (Some(left), None)        => Some(left)
          case (None, Some(right))       => Some(right)
          case (None, None)              => None
        }
      case RequestCodec.Map(info, _, _)       =>
        info.getHeaders
      case route: Header[_]                   =>
        Some(route)
      case _: Query[_]                        =>
        None
      case _: Route[_]                        =>
        None
    }

  private[api] def parseRequest(request: Request): Option[A] = Option(parseRequestImpl(request))

  private[api] def parseRequestImpl(request: Request): A

  private[api] def unapply(request: Request): Option[A] = parseRequest(request)
}

object RequestCodec {
  private[api] final case class ZipWith[A, B, C](
    left: RequestCodec[A],
    right: RequestCodec[B],
    f: (A, B) => C,
    g: C => (A, B),
  ) extends RequestCodec[C] {

    override private[api] def parseRequestImpl(request: Request): C = {
      val a = left.parseRequestImpl(request)
      if (a == null) return null.asInstanceOf[C]
      val b = right.parseRequestImpl(request)
      if (b == null) return null.asInstanceOf[C]
      f(a, b)
    }
  }

  private[api] final case class Map[A, B](info: RequestCodec[A], f: A => B, g: B => A) extends RequestCodec[B] {
    override private[api] def parseRequestImpl(request: Request): B = {
      val a = info.parseRequestImpl(request)
      if (a == null) return null.asInstanceOf[B]
      f(a)
    }
  }
}

/**
 * =HEADERS=
 */
sealed trait Header[A] extends RequestCodec[A] {
  self =>

  def ? : Header[Option[A]] =
    Header.Optional(self)

  override def map[B](f: A => B)(g: B => A): Header[B] =
    Header.Map(self, f, g)

  def ++[B](that: Header[B])(implicit zipper: Zipper[A, B]): Header[zipper.Out] =
    Header.ZipWith[A, B, zipper.Out](self, that, zipper.zip(_, _), zipper.unzip)

  override private[api] def parseRequestImpl(request: Request) = {
    val map: Map[String, String] = request.headers.toChunk.toMap.map { case (k, v) => k.toString -> v.toString }
    parseHeaders(map)
  }

  private[api] def parseHeaders(requestHeaders: Map[String, String]): A

}

object Header {
  def AcceptEncoding: Header[String] = string("Accept-Encoding")
  def UserAgent: Header[String]      = string("User-Agent")
  def Host: Header[String]           = string("Host")
  def Accept: Header[String]         = string("Accept")

  def string(name: String): Header[String] = SingleHeader(name, Parser.stringParser)

  private[api] final case class SingleHeader[A](name: String, parser: Parser[A]) extends Header[A] {
    override private[api] def parseHeaders(requestHeaders: Predef.Map[String, String]): A = {
      val a      = requestHeaders.getOrElse(name, null)
      if (a == null) return null.asInstanceOf[A]
      val parsed = parser.parse(a)
      if (parsed.isEmpty) null.asInstanceOf[A]
      else parsed.get
    }
  }

  private[api] final case class ZipWith[A, B, C](left: Header[A], right: Header[B], f: (A, B) => C, g: C => (A, B))
      extends Header[C] {
    override private[api] def parseHeaders(requestHeaders: Predef.Map[String, String]): C = {
      val a = left.parseHeaders(requestHeaders)
      if (a == null) return null.asInstanceOf[C]
      val b = right.parseHeaders(requestHeaders)
      if (b == null) return null.asInstanceOf[C]
      f(a, b)
    }
  }

  private[api] final case class Map[A, B](headers: Header[A], f: A => B, g: B => A) extends Header[B] {
    override private[api] def parseHeaders(requestHeaders: Predef.Map[String, String]): B = {
      val a = headers.parseHeaders(requestHeaders)
      if (a == null) return null.asInstanceOf[B]
      f(a)
    }

  }

  private[api] case class Optional[A](headers: Header[A]) extends Header[Option[A]] {
    override private[api] def parseHeaders(requestHeaders: Predef.Map[String, String]): Option[A] =
      Option(headers.parseHeaders(requestHeaders))

  }

}

/**
 * QUERY PARAMS \============
 */
sealed trait Query[A] extends RequestCodec[A] { self =>
  def ? : Query[Option[A]] = Query.Optional(self)

  def ++[B](that: Query[B])(implicit zipper: Zipper[A, B]): Query[zipper.Out] =
    Query.ZipWith[A, B, zipper.Out](self, that, zipper.zip(_, _), zipper.unzip)

  override def map[B](f: A => B)(g: B => A): Query[B] =
    Query.MapParams(self, f, g)

  override private[api] def parseRequestImpl(request: Request) =
    parseQueryImpl(request.url.queryParams)

  def parseQueryImpl(params: Map[String, List[String]]): A
}

object Query {

  private[api] final case class SingleParam[A](name: String, parser: Parser[A], schema: Schema[A]) extends Query[A] {
    override def parseQueryImpl(params: Map[String, List[String]]): A = {
      val a      = params.getOrElse(name, null)
      if (a == null) return null.asInstanceOf[A]
      val parsed = parser.parse(a.head)
      if (parsed.isEmpty) null.asInstanceOf[A]
      else parsed.get
    }

  }

  private[api] final case class ZipWith[A, B, C](left: Query[A], right: Query[B], f: (A, B) => C, g: C => (A, B))
      extends Query[C] {
    override def parseQueryImpl(params: Map[String, List[String]]): C = {
      val a = left.parseQueryImpl(params)
      if (a == null) return null.asInstanceOf[C]
      val b = right.parseQueryImpl(params)
      if (b == null) return null.asInstanceOf[C]
      f(a, b)
    }

  }

  private[api] final case class MapParams[A, B](params: Query[A], f: A => B, g: B => A) extends Query[B] {
    override def parseQueryImpl(paramsMap: Map[String, List[String]]): B = {
      val a = params.parseQueryImpl(paramsMap)
      if (a == null) return null.asInstanceOf[B]
      f(a)
    }

  }

  private[api] case class Optional[A](params: Query[A]) extends Query[Option[A]] {
    override def parseQueryImpl(paramsMap: Map[String, List[String]]): Option[A] =
      Option(params.parseQueryImpl(paramsMap))
  }

}

/**
 * A DSL for describe Routes
 *   - ex: /users
 *   - ex: /users/:id/friends
 *   - ex: /users/:id/friends/:friendId
 *   - ex: /posts/:id/comments/:commentId
 */
sealed trait Route[A] extends RequestCodec[A] { self =>
  def ??(doc: Doc): Route[A] = ???

  override def map[B](f: A => B)(g: B => A): Route[B] =
    Route.MapRoute(self, f, g)

  def /[B](that: Route[B])(implicit zipper: Zipper[A, B]): Route[zipper.Out] =
    Route.ZipWith[A, B, zipper.Out](this, that, zipper.zip(_, _), zipper.unzip)

  def /(string: String): Route[A] =
    Route.ZipWith(this, Route.path(string), (a: A, _: Unit) => a, a => (a, ()))

  // ZipWith implementation
  // - knowledge of zippable boundary
  // - predict structure of final tuple
  // - push information onto stack (mutable array list)
  //   - track size

  // ParseState.failed is communicating via the heap

  // throw without StackTrace
  // - use a special exception construction, pass null in everywhere
  // - sad path
  // - BUILD A RESPONSE TREE! RequestCodec ++ RequestCodec
  // Kleisli OrElse — EGAD!
  // Akka  fallback  — EGAD!

  override private[api] def parseRequestImpl(request: Request): A = {
    val state  = RouteState(request.url.path.toList)
    val result = parseImpl(state)
    if (result == null || state.input.nonEmpty) null.asInstanceOf[A]
    else result
  }

  private[api] def parseImpl(pathState: RouteState): A
}

final case class RouteState(var input: List[String])

object Route {
  def path(name: String): Route[Unit] = Route.Literal(name).asInstanceOf[Route[Unit]]

  // Matches a literal string in the path (e.g., "users")
  // input: List("users", "123")
  // Literal("users")
  // input: List("123")
  // /123
  private[api] final case class Literal(string: String) extends Route[Any] {
    override private[api] def parseImpl(pathState: RouteState): Any =
      if (pathState.input.nonEmpty && pathState.input.head == string)
        pathState.input = pathState.input.tail
      else
        null
  }

  // "users" / uuid
  private[api] final case class Match[A](name: String, parser: Parser[A], schema: Schema[A]) extends Route[A] {
    override private[api] def parseImpl(pathState: RouteState): A =
      if (pathState.input.isEmpty) {
        null.asInstanceOf[A]
      } else {
        val a = parser.parse(pathState.input.head)
        if (a.isEmpty) {
          null.asInstanceOf[A]
        } else {
          pathState.input = pathState.input.tail
          a.get
        }
      }
  }

  private[api] final case class ZipWith[A, B, C](left: Route[A], right: Route[B], f: (A, B) => C, g: C => (A, B))
      extends Route[C] {
    override private[api] def parseImpl(pathState: RouteState): C =
      if (pathState.input.isEmpty) null.asInstanceOf[C]
      else {
        val a = left.parseImpl(pathState)
        if (a == null) return null.asInstanceOf[C]
        val b = right.parseImpl(pathState)
        if (b == null) return null.asInstanceOf[C]
        f(a, b)
      }
  }

  private[api] final case class MapRoute[A, B](path: Route[A], f: A => B, g: B => A) extends Route[B] {
    override private[api] def parseImpl(pathState: RouteState): B = {
      val a = path.parseImpl(pathState)
      if (a == null) return null.asInstanceOf[B]
      f(a)
    }
  }
}
