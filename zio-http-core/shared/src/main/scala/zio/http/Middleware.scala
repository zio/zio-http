package zio.http

trait Middleware[UpperCtx, Ctx] { self =>
  def apply(routes: Routes[Ctx]): Routes[UpperCtx]
  def andThen[UpperCtx2](that: Middleware[UpperCtx2, UpperCtx]): Middleware[UpperCtx2, Ctx] =
    new Middleware[UpperCtx2, Ctx] {
      def apply(routes: Routes[Ctx]): Routes[UpperCtx2] = that(self(routes))
    }
}

object Middleware {
  def identity[Ctx]: Middleware[Ctx, Ctx] = new Middleware[Ctx, Ctx] {
    def apply(routes: Routes[Ctx]): Routes[Ctx] = routes
  }

  inline def custom[F](inline f: F): Middleware[?, ?] =
    ${ MiddlewareMacro.customImpl[F]('f) }

  inline def intercept[F](inline f: F): Middleware[?, ?] = custom(f)
  inline def whenContext[F](inline f: F): Middleware[?, ?] = custom(f)
}
