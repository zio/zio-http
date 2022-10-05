package zio.http.api

import zio.ZIO

// TODO; Allow Middleware to decide whether `handle` should be invoked
sealed trait Middleware[-R, +E, In, Out]

object Middleware {
  final case class HandlerZIO[-R, +E, In, Out](spec: MiddlewareSpec[In, Out], handler: In => ZIO[R, E, Out])
      extends Middleware[R, E, In, Out]

  final case class Handler[In, Out](spec: MiddlewareSpec[In, Out], handler: In => Out)
      extends Middleware[Any, Nothing, In, Out]

  // final case class IfThenElse[In, Out](sp)
}
