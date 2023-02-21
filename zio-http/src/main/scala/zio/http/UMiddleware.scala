package zio.http

trait UMiddleware[+AIn, -AOut, -BIn, +BOut] extends Middleware[Nothing, Any, Nothing, Any, AIn, AOut, BIn, BOut] {
  override type OutEnv[Env0] = Any
  override type OutErr[Err0] = Nothing
}
