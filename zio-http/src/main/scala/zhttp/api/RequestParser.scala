package zhttp.api

import zhttp.http.Request
import zio.schema.Schema

/**
 * A RequestParser is a description of a Path, Query Parameters, and Headers.:
 *   - Path: /users/:id/posts
 *   - Query Parameters: ?page=1&limit=10
 *   - Headers: X-User-Id: 1 or Accept: application/json
 */
sealed trait RequestParser[A] extends Product with Serializable { self =>
  private[api] def ++[B](that: RequestParser[B])(implicit zipper: Zipper[A, B]): RequestParser[zipper.Out] =
    RequestParser.ZipWith[A, B, zipper.Out](self, that, zipper.zip(_, _), zipper.unzip)

  def map[B](f: A => B)(g: B => A): RequestParser[B] =
    RequestParser.Map(self, f, g)

  private[api] def getPath: Path[_] = {
    def getPathImpl(requestParser: RequestParser[_]): Option[Path[_]] =
      requestParser match {
        case RequestParser.ZipWith(left, right, _, _) =>
          getPathImpl(left) orElse getPathImpl(right)
        case RequestParser.Map(info, _, _)            =>
          getPathImpl(info)
        case _: Header[_]                             =>
          None
        case _: Query[_]                              =>
          None
        case route: Path[_]                           =>
          Some(route)
      }

    getPathImpl(self).get
  }

  private[api] def getQueryParams: Option[Query[_]] =
    self match {
      case zip: RequestParser.ZipWith[_, _, _] =>
        (zip.left.getQueryParams, zip.right.getQueryParams) match {
          case (Some(left), Some(right)) => Some(left ++ right)
          case (Some(left), None)        => Some(left)
          case (None, Some(right))       => Some(right)
          case (None, None)              => None
        }
      case RequestParser.Map(info, _, _)       =>
        info.getQueryParams
      case route: Query[_]                     =>
        Some(route)
      case _: Header[_]                        =>
        None
      case _: Path[_]                          =>
        None
    }

  private[api] def getHeaders: Option[Header[_]] =
    self match {
      case zip: RequestParser.ZipWith[_, _, _] =>
        (zip.left.getHeaders, zip.right.getHeaders) match {
          case (Some(left), Some(right)) => Some(left ++ right)
          case (Some(left), None)        => Some(left)
          case (None, Some(right))       => Some(right)
          case (None, None)              => None
        }
      case RequestParser.Map(info, _, _)       =>
        info.getHeaders
      case route: Header[_]                    =>
        Some(route)
      case _: Query[_]                         =>
        None
      case _: Path[_]                          =>
        None
    }

  private[api] def parseRequest(request: Request): Option[A] = Option(parseRequestImpl(request))

  private[api] def parseRequestImpl(request: Request): A
}

object RequestParser {
  private[api] final case class ZipWith[A, B, C](
    left: RequestParser[A],
    right: RequestParser[B],
    f: (A, B) => C,
    g: C => (A, B),
  ) extends RequestParser[C] {

    override private[api] def parseRequestImpl(request: Request): C = {
      val a = left.parseRequestImpl(request)
      if (a == null) return null.asInstanceOf[C]
      val b = right.parseRequestImpl(request)
      if (b == null) return null.asInstanceOf[C]
      f(a, b)
    }
  }

  private[api] final case class Map[A, B](info: RequestParser[A], f: A => B, g: B => A) extends RequestParser[B] {
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
sealed trait Header[A] extends RequestParser[A] {
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
sealed trait Query[A] extends RequestParser[A] { self =>
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
 * A DSL for describe Paths
 *   - ex: /users
 *   - ex: /users/:id/friends
 *   - ex: /users/:id/friends/:friendId
 *   - ex: /posts/:id/comments/:commentId
 */
sealed trait Path[A] extends RequestParser[A] { self =>
  def ??(doc: Doc): Path[A] = ???

  override def map[B](f: A => B)(g: B => A): Path[B] =
    Path.MapPath(self, f, g)

  def /[B](that: Path[B])(implicit zipper: Zipper[A, B]): Path[zipper.Out] =
    Path.ZipWith[A, B, zipper.Out](this, that, zipper.zip(_, _), zipper.unzip)

  def /(string: String): Path[A] =
    Path.ZipWith(this, Path.path(string), (a: A, _: Unit) => a, a => (a, ()))

  // ZipWith implementation
  // - knowledge of zippable boundary
  // - predict structure of final tuple
  // - push information onto stack (mutable array list)
  //   - track size

  // ParseState.failed is communicating via the heap

  // throw without StackTrace
  // - use a special exception construction, pass null in everywhere
  // - sad path
  // - BUILD A RESPONSE TREE! RequestParser ++ RequestParser
  // Kleisli OrElse — EGAD!
  // Akka  fallback  — EGAD!

  override private[api] def parseRequestImpl(request: Request): A = {
    val state  = PathState(request.url.path.toList)
    val result = parseImpl(state)
    if (result == null || state.input.nonEmpty) null.asInstanceOf[A]
    else result
  }

  private[api] def parseImpl(pathState: PathState): A
}

final case class PathState(var input: List[String])

object Path {
  def path(name: String): Path[Unit] = Path.Literal(name).asInstanceOf[Path[Unit]]

  // Matches a literal string in the path (e.g., "users")
  // input: List("users", "123")
  // Literal("users")
  // input: List("123")
  // /123
  private[api] final case class Literal(string: String) extends Path[Any] {
    override private[api] def parseImpl(pathState: PathState): Any =
      if (pathState.input.nonEmpty && pathState.input.head == string)
        pathState.input = pathState.input.tail
      else
        null
  }

  // "users" / uuid
  private[api] final case class Match[A](name: String, parser: Parser[A], schema: Schema[A]) extends Path[A] {
    override private[api] def parseImpl(pathState: PathState): A =
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

  private[api] final case class ZipWith[A, B, C](left: Path[A], right: Path[B], f: (A, B) => C, g: C => (A, B))
      extends Path[C] {
    override private[api] def parseImpl(pathState: PathState): C =
      if (pathState.input.isEmpty) null.asInstanceOf[C]
      else {
        val a = left.parseImpl(pathState)
        if (a == null) return null.asInstanceOf[C]
        val b = right.parseImpl(pathState)
        if (b == null) return null.asInstanceOf[C]
        f(a, b)
      }
  }

  private[api] final case class MapPath[A, B](path: Path[A], f: A => B, g: B => A) extends Path[B] {
    override private[api] def parseImpl(pathState: PathState): B = {
      val a = path.parseImpl(pathState)
      if (a == null) return null.asInstanceOf[B]
      f(a)
    }
  }
}
