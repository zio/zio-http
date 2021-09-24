package zhttp.http

private[zhttp] trait HttpAppSyntax {
  import scala.language.implicitConversions

  implicit final def httpAppSyntax[R, E](self: HttpApp[R, E]): HttpAppOps[R, E] = new HttpAppOps(self)
}

private[zhttp] final class HttpAppOps[R, E](val self: HttpApp[R, E]) extends AnyVal {
  @deprecated("you no longer need to unwrap an HttpApp", "zio-http 1.0.0.0-RC18")
  def asHttp: Http[R, E, Request, Response[R, E]] = self.http

  /**
   * Converts a failing Http into a non-failing one by handling the failure and converting it to a result if possible.
   */
  def silent[R1 <: R, E1 >: E](implicit s: CanBeSilenced[E1, Response[R1, E1]]) =
    self.http.catchAll(e => Http.succeed(s.silent(e)))
}
