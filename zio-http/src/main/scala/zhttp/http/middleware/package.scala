package zhttp.http

package object middleware {
  type HttpMiddleware[-R, +E]       = MonoMiddleware[R, E, Request, Response]
  type MonoMiddleware[-R, +E, A, B] = Middleware[R, E, A, B, A, B]
}
