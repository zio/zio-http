package zio.http

package object middleware {
  type HttpMiddleware[-R, +E, ReqT <: IT[Request]] = MonoMiddleware[R, E, Request, Response, ReqT]
  type MonoMiddleware[-R, +E, A, B, AT <: IT[A]]   = Middleware[R, E, A, B, A, B, AT]
}
