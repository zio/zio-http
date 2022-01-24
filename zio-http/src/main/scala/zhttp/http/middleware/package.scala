package zhttp.http

package object middleware {
  type HttpMiddleware[-R, +E] = Middleware[R, E, Request, Response, Request, Response]
}
