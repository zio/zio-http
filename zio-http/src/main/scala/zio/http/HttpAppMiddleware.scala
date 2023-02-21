package zio.http

trait HttpAppMiddleware[-R, +Err] extends Middleware[Nothing, R, Err, Any, Request, Response, Request, Response] {
  override type OutEnv[Env0] = R
  override type OutErr[Err0] = Err
}
