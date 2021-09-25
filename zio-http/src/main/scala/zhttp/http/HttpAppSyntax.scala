package zhttp.http

private[zhttp] trait HttpAppSyntax {
  import scala.language.implicitConversions

  implicit final def httpAppSyntax[R, E](self: HttpApp[R, E]): HttpAppOps[R, E] = new HttpAppOps(self)
}

private[zhttp] final class HttpAppOps[R, E](val self: HttpApp[R, E]) extends AnyVal {

  /**
   * Converts a failing Http app into a non-failing one by handling the failure and converting it to a result if
   * possible.
   */
  def silent[R1 <: R, E1 >: E](implicit s: CanBeSilenced[E1, Response[R1, E1]]): HttpApp[R1, E1] =
    self.catchAll(e => Http.succeed(s.silent(e)).toApp)
}
