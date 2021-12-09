package zhttp.endpoint

import zhttp.http._

/**
 * Description of an Http endpoint containing a Method and a ParameterList
 */
final case class Endpoint[A](method: Method, params: ParameterList[A]) { self =>

  /**
   * Appends a string literal to the endpoint
   */
  def /(name: String): Endpoint[A] = Endpoint(self.method, name :: self.params)

  /**
   * Appends the parameter to the endpoint
   */
  def /[B, C](other: Parameter[B])(implicit ev: CanCombine.Aux[A, B, C]): Endpoint[C] =
    Endpoint(self.method, other :: self.params)

  /**
   * Creates an HttpApp from a Request to Response function
   */
  def to[B](f: Request.ParameterizedRequest[A] => B)(implicit ctor: CanConstruct[A, B]): HttpApp[ctor.ROut, ctor.EOut] =
    ctor.make(self, f)

  private[zhttp] def extract(path: Path): Option[A]       = Endpoint.extract(path, self)
  private[zhttp] def extract(request: Request): Option[A] = Endpoint.extract(request, self)
}

private[zhttp] object Endpoint {

  def unit[A]: Option[A] = Option(().asInstanceOf[A])

  /**
   * Create Route[Unit] from a Method
   */
  def fromMethod(method: Method): Endpoint[Unit] = Endpoint(method, ParameterList.Empty)

  /**
   * Extracts the parameter list from the given path
   */
  private[zhttp] def extract[A](path: Path, route: Endpoint[A]): Option[A] = route.params.extract(path)

  /**
   * Extracts the parameter list from the given request
   */
  private[zhttp] def extract[A](request: Request, self: Endpoint[A]): Option[A] =
    if (self.method == request.method) { self.extract(request.path) }
    else None

}
