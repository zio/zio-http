package zhttp.endpoint

import zhttp.endpoint.Endpoint.{EPath, EToken}
import zhttp.http._

import scala.annotation.tailrec
import scala.util.Try

final case class Endpoint[A](method: Method, ePath: EPath[A]) { self =>

  /**
   * Create a new Route[A] with existing RoutePath and new string
   */
  def /(name: String): Endpoint[A] =
    Endpoint(self.method, EPath.parse(EToken.Literal(name), self.ePath))

  /**
   * Create a new Route[C] by combining RouteToken[B] and existing RoutePath[A]. C here is path dependent type and will
   * be resolved using combing aux implicit
   */
  def /[B, C](other: EToken[B])(implicit ev: EndpointCombine.Aux[A, B, C]): Endpoint[C] =
    Endpoint(self.method, EPath.parse(other, self.ePath))

  /**
   * Extract route params from request
   */
  def extract(request: Request): Option[A] = Endpoint.extract(request, self)

  /**
   * Extract route params from request path
   */
  def extract(path: Path): Option[A] = Endpoint.extract(path, self)

  /**
   * Execute extract on request path and returns Option[A]
   */
  def parse(path: Path): Option[A] = self.extract(path)

  /**
   * Execute extract on request and returns Option[A]
   */
  def parse(request: Request): Option[A] = self.extract(request)

  /**
   * Creates an HttpApp from a Request to Response function
   */
  def to[B](f: Request.ParameterizedRequest[A] => B)(implicit
    ctor: HttpAppConstructor[A, B],
  ): HttpApp[ctor.ROut, ctor.EOut] =
    ctor.make(self, f)
}

object Endpoint {

  private def unit[A]: Option[A] = Option(().asInstanceOf[A])

  sealed trait EPath[+A] { self =>
    def extract(path: Path): Option[A] = EPath.extract(self, path)
  }
  object EPath           {
    def empty: EPath[Unit] = Empty

    /**
     * Create RoutePath by combining RouteToken[Unit] and RoutePath[A]
     */
    def parse[A](head: EToken[Unit], tail: EPath[A]): EPath[A] =
      EPath.Cons(head, tail)

    /**
     * Create RoutePath[C] by combining RouteToken[B] and RoutePath[A]. Return type C is determined by implicit combine
     */
    def parse[A, B, C](head: EToken[B], tail: EPath[A])(implicit ev: EndpointCombine.Aux[A, B, C]): EPath[C] =
      EPath.Cons(head, tail)

    case object Empty extends EPath[Unit]

    private[zhttp] final case class Cons[A, B, C](head: EToken[B], tail: EPath[A]) extends EPath[C]

    def extract[A](r: EPath[A], p: Path): Option[A] = extract(r, p.toList.reverse)

    /**
     * Recursively iterate through RoutePath and List of String. Returns Option of tuple in case of matching RoutePath
     * and path list
     */
    private def extract[A](r: EPath[A], p: List[String]): Option[A] = {
      @tailrec
      def loop(r: EPath[Any], p: List[String], output: List[Any]): Option[Any] = {
        r match {
          case Empty            => ListToOptionTuple.getOptionTuple(output)
          case Cons(head, tail) =>
            if (p.isEmpty) None
            else {
              head.extract(p.head) match {
                case Some(value) =>
                  if (value.isInstanceOf[Unit]) loop(tail, p.tail, output) else loop(tail, p.tail, value :: output)
                case None        => None
              }
            }
        }
      }
      loop(r.asInstanceOf[EPath[Any]], p, List.empty[Any]).asInstanceOf[Option[A]]
    }
  }

  sealed trait EToken[+A] { self =>
    def extract(string: String): Option[A] = EToken.extract(self, string)
  }
  object EToken           {
    private[zhttp] final case class Literal(s: String)     extends EToken[Unit]
    private[zhttp] final case class Param[A](r: EParam[A]) extends EToken[A]

    def extract[A](rt: EToken[A], string: String): Option[A] = rt match {
      case EToken.Literal(s) => if (s == string) Endpoint.unit else None
      case EToken.Param(r)   => r.extract(string)
    }
  }

  /**
   * Create Route[Unit] from a Method
   */
  def fromMethod(method: Method): Endpoint[Unit] = Endpoint(method, EPath.Empty)

  /**
   * Create RouteToken of Param category
   */
  def apply[A](implicit ev: EParam[A]): EToken[A] = EToken.Param(ev)

  /**
   * Extract route params from Route and Request path
   */
  def extract[A](path: Path, route: Endpoint[A]): Option[A] = route.ePath.extract(path)

  /**
   * Extract route params from Route and Request
   */
  def extract[A](request: Request, self: Endpoint[A]): Option[A] =
    if (self.method == request.method) { self.extract(request.path) }
    else None

  trait EParam[A] {

    /**
     * Take path string and return specialised RouteParam
     */
    def extract(data: String): Option[A]
  }
  object EParam {
    implicit object IntExtract     extends EParam[Int]     {
      override def extract(data: String): Option[Int] = Try(data.toInt).toOption
    }
    implicit object StringExtract  extends EParam[String]  {
      override def extract(data: String): Option[String] = Option(data)
    }
    implicit object BooleanExtract extends EParam[Boolean] {
      override def extract(data: String): Option[Boolean] = Try(data.toBoolean).toOption
    }
  }

}
