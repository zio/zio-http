package zio.http.api

import zio.ZIO

// TODO; Allow Middleware to decide whether `handle` should be invoked
sealed trait Middleware[-R, +E, I, O]

object Middleware {
  val none: Middleware[Any, Nothing, Unit, Unit] = Handler(MiddlewareSpec.none, _ => ())

  final case class HandlerZIO[-R, +E, I, O](spec: MiddlewareSpec[I, O], handler: I => ZIO[R, E, O])
      extends Middleware[R, E, I, O]

  final case class Handler[I, O](spec: MiddlewareSpec[I, O], handler: I => O) extends Middleware[Any, Nothing, I, O]

  // final case class IfThenElse[In, Out](sp)
}
