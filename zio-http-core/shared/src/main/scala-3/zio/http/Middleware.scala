package zio.http

extension (m: Middleware.type) {
  inline def custom[F](inline f: F): Middleware[?, ?] =
    ${ MiddlewareMacro.customImpl[F]('f) }

  inline def intercept[F](inline f: F): Middleware[?, ?]   = custom(f)
  inline def whenContext[F](inline f: F): Middleware[?, ?] = custom(f)
}
