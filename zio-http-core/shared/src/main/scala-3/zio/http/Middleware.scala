package zio.http

extension (m: Middleware.type) {
  transparent inline def custom[F](inline f: F): Middleware[?, ?] =
    ${ MiddlewareMacro.customImpl[F]('f) }
}
