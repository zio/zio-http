package zhttp.api

import zhttp.http.Response
import zio._

import java.util.UUID

/**
 * A DSL for describe Routes
 *   - ex: /users
 *   - ex: /users/:id/friends
 *   - ex: /users/:id/friends/:friendId
 *   - ex: /posts/:id/comments/:commentId
 */

sealed trait RouteCodec[A] extends Product with Serializable { self =>
  // returns Option of leftmost path segment and the tail
  // or, if a single segment, returns None
  def peel: Option[(RouteCodec[_], RouteCodec[_])] = {
    flatten match {
      case _ :: Nil   => None
      case head :: xs => Some((head, xs.reduce(RouteCodec.Zip(_, _, (a: Any, b: Any) => (a, b)))))
      case Nil        => throw new IllegalStateException("Empty route")
    }
  }

  def toOptimized: OptimizedRouteCodec =
    self.asInstanceOf[RouteCodec[_]] match {
      case RouteCodec.StringLiteral(value) => OptimizedRouteCodec.StringLiteral(value)
      case RouteCodec.ParseRoute(parser)   => OptimizedRouteCodec.ParseRoute(parser)
      case RouteCodec.Zip(lhs, rhs, _)     => OptimizedRouteCodec.Zip(lhs.toOptimized, rhs.toOptimized)
    }

  def flatten: List[RouteCodec[_]] = {
    self match {
      case RouteCodec.Zip(lhs, rhs, _) => lhs.flatten ++ rhs.flatten
      case other                       => List(other)
    }
  }

  def zip[B](that: RouteCodec[B])(implicit zippable: Zippable[A, B]): RouteCodec[zippable.Out] =
    RouteCodec.Zip[A, B, zippable.Out](this, that, zippable.zip(_, _))

//  def longestCommonPrefix(that: RouteCodec[_]): Option[RouteCodec[_]] = {}
}

object RouteCodec {
  final case class StringLiteral(value: String)                                         extends RouteCodec[Unit]
  final case class ParseRoute[A](parser: Parser[A])                                     extends RouteCodec[A]
  final case class Zip[A, B, C](lhs: RouteCodec[A], rhs: RouteCodec[B], f: (A, B) => C) extends RouteCodec[C]

  val uuid: ParseRoute[UUID]               = ParseRoute(Parser.uuidParser)
  val users: RouteCodec[UUID]              = StringLiteral("users") zip uuid
  val usersPosts: RouteCodec[(UUID, UUID)] = StringLiteral("users") zip uuid zip StringLiteral("posts") zip uuid

  // parsedRouteResult: (UUID, UUID)
  // Future optimization: look into zio-json's prefix technique for matching Json Object keys

  // /users
  //       /:id ID
  //           /friends
  //           /posts -> (UUID)
  //                 /:id -> (UUID)
  //           /comments -> (UUID)
  // /posts
}

final case class API2[RouteType](
  routeCodec: RouteCodec[RouteType],
)

final case class Handler2[A](
  api: API2[A],
  handler: A => ZIO[Any, Throwable, Response],
)

object ServerInterpreter2 {
  def run(handlers: List[Handler2[_]]): Unit = {
    val optimized = ???
  }
}

// TODO: Add [-R, +E]
sealed trait OptimizedRouteCodec extends Product with Serializable { self =>
  def isHandle: Boolean = self.isInstanceOf[OptimizedRouteCodec.Handle]
}

object OptimizedRouteCodec {
  final case class StringLiteral(value: String)                            extends OptimizedRouteCodec
  // users/123
  final case class ParseRoute(parser: Parser[_])                           extends OptimizedRouteCodec
//  final case class ParseRoute(parser: Parser[_])                           extends OptimizedRouteCodec
  final case class Zip(lhs: OptimizedRouteCodec, rhs: OptimizedRouteCodec) extends OptimizedRouteCodec

  //
  // - loop
  // - looper
  // loo(p)e(r)
  // loo(p)e(r)
  // users / (id, handler: Id => Response) / (posts, handler: Id => Response)

  // /users
  // /users
  // TODO: Move handle into alternatives (less orthogonal)
  final case class Handle(
    codec: OptimizedRouteCodec,
    handler: Any => ZIO[Any, Throwable, Response],
  ) extends OptimizedRouteCodec

  final case class Alternatives(
    prefix: OptimizedRouteCodec,
    // TODO: Use Chunk! (call materialize)
    alternatives: List[OptimizedRouteCodec],
  ) extends OptimizedRouteCodec

  case object Empty extends OptimizedRouteCodec

  def peel(handler: Handler2[_]): Either[Handle, (OptimizedRouteCodec, Handler2[_])] = {
    handler.api.routeCodec.peel match {
      case None                      =>
        Left(
          Handle(
            handler.api.routeCodec.toOptimized,
            handler.handler.asInstanceOf[Any => ZIO[Any, Throwable, Response]],
          ),
        )
      case Some((prefix, remainder)) =>
        val value = handler.asInstanceOf[Handler2[Any]].copy(api = handler.api.copy(routeCodec = remainder))
        Right((prefix.toOptimized, value))
    }
    // if handler has singleton route (no zips!) then return Left of Handle

    // else return a tuple of (the leftmost route segment, handler with the first route segment removed)

  }

  def optimize(handlers: List[Handler2[_]]): List[OptimizedRouteCodec] = {
    // TODO: handle multiple "handles" with same codec (there should only be one handle per route path)
    val (handles, remainder): (List[Handle], List[(OptimizedRouteCodec, Handler2[_])]) =
      handlers.partitionMap(peel)

    val groupedByPrefix: Map[OptimizedRouteCodec, List[Handler2[_]]] =
      remainder.groupMap(_._1)(_._2)

    val alternatives = groupedByPrefix.map { case (prefix, alternatives) =>
      Alternatives(prefix, optimize(alternatives))
    }

    handles ++ alternatives
  }

  def optimizeTopLevel(handlers: List[Handler2[_]]): OptimizedRouteCodec = {
    val codecs = optimize(handlers)
    codecs match {
      case head :: Nil => head
      case codecs      => Alternatives(Empty, codecs)
    }
  }
}
