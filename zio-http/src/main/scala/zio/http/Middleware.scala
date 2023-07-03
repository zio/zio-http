package zio.http

import zio._

sealed trait Middleware[-Env, +Ctx] { self =>
  import Middleware._

  final def @@[Env1 <: Env, Ctx2](that: Middleware[Env1, Ctx2]): Middleware[Env1, Ctx with Ctx2] =
    Sequence[Env1, Ctx, Ctx2](self, that)

  private[http] lazy val flattened: Chunk[Leaf[Env, _]] = flatten(self).materialize
}
object Middleware                   {
  private[http] case object Identity                        extends Middleware[Any, Any]
  private[http] final case class Sequence[-Env, Ctx1, Ctx2](
    first: Middleware[Env, Ctx1],
    second: Middleware[Env, Ctx2],
  ) extends Middleware[Env, Ctx1 with Ctx2]
  private[http] abstract class Leaf[-Env, +Ctx]             extends Middleware[Env, Ctx]
  private[http] sealed trait InterceptContextZIO[-Env, Ctx] extends Leaf[Env, Ctx] {
    type Session

    def incoming(request: Request): ZIO[Env, Response, (Request, Session, ZEnvironment[Ctx])]

    def outgoing(response: Response, session: Session): ZIO[Env, Nothing, Response]
  }
  private[http] sealed trait InterceptContext[Ctx]          extends Leaf[Any, Ctx] {
    type Session

    def incoming(request: Request): Either[Response, (Request, Session, ZEnvironment[Ctx])]

    def outgoing(response: Response, session: Session): Response
  }
  private[http] sealed trait InterceptZIO[-Env, Ctx]        extends Leaf[Env, Any] {
    type Session

    def incoming(request: Request): ZIO[Env, Response, (Request, Session)]

    def outgoing(response: Response, session: Session): ZIO[Env, Nothing, Response]
  }
  private[http] sealed trait Intercept                      extends Leaf[Any, Any] {
    type Session

    def incoming(request: Request): Either[Response, (Request, Session)]

    def outgoing(response: Response, session: Session): Response
  }
  private[http] sealed trait InterceptFast                  extends Leaf[Any, Any] {
    type Session

    def incoming(request: Request): Request

    def outgoing(response: Response, session: Session): Response
  }

  private def flatten[Env, Ctx](middleware: Middleware[Env, Ctx]): Chunk[Leaf[Env, _]] =
    middleware match {
      case Identity                => Chunk.empty
      case Sequence(first, second) => flatten(first) ++ flatten(second)
      case leaf: Leaf[Env, ctx]    => Chunk(leaf)
    }

  private[http] def executeIncoming[Env, Ctx](
    middleware: Middleware[Env, Ctx],
    request: Request,
  ): ZIO[Env, Response, (Request, ZEnvironment[Ctx])] = {
    val flattened = middleware.flattened

    val len = flattened.length
    var i   = 0

    var result: ZIO[Env, Response, (Request, ZEnvironment[Any])] = Exit.succeed((request, ZEnvironment.empty))

    while (i < len) {
      val leaf = flattened(i)

      leaf match {
        case leaf: InterceptContextZIO[_, _] => ???

        case leaf: InterceptContext[_] => ???

        case leaf: InterceptZIO[_, _] => ???

        case leaf: Intercept => ???

        case leaf: InterceptFast => ???
      }

      i = i + 1
    }

    result.asInstanceOf[ZIO[Env, Response, (Request, ZEnvironment[Ctx])]]
  }
}
