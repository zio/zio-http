package zio.http

package object middleware {
  type HttpMiddleware[-R, +E]               = MonoMiddleware[R, E, Request, Response]
  type HttpMiddlewareForTotal[-R, +E]       = MonoMiddlewareForTotal[R, E, Request, Response]
  type MonoMiddleware[-R, +E, A, B]         = Middleware[R, E, A, B, A, B]
  type MonoMiddlewareForTotal[-R, +E, A, B] = Middleware.ForTotal[R, E, A, B, A, B]
}
