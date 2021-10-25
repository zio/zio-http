package zhttp.experiment
import zhttp.experiment.Route.{RoutePath, RouteToken}
import zhttp.http._

import scala.annotation.tailrec
import scala.util.Try

final case class Route[A](method: Method, routePath: RoutePath[A]) { self =>

  /**
   * Create a new Route[A] with existing RoutePath and new string
   */
  def /(name: String): Route[A] =
    Route(self.method, RoutePath.parse(RouteToken.Literal(name), self.routePath))

  /**
   * Create a new Route[C] by combining RouteToken[B] and existing RoutePath[A]. C here is path dependent type and will
   * be resolved using combing aux implicit
   */
  def /[B, C](other: RouteToken[B])(implicit ev: RouteCombine.Aux[A, B, C]): Route[C] =
    Route(self.method, RoutePath.parse(other, self.routePath))

  /**
   * Extract route params from request
   */
  def extract(request: Request): Option[A] = Route.extract(request, self)

  /**
   * Extract route params from request path
   */
  def extract(path: Path): Option[A] = Route.extract(path, self)

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

object Route {

  private def unit[A]: Option[A] = Option(().asInstanceOf[A])

  sealed trait RoutePath[+A] { self =>
    def extract(path: Path): Option[A] = RoutePath.extract(self, path)
  }
  object RoutePath           {
    def empty: RoutePath[Unit] = Empty

    /**
     * Create RoutePath by combining RouteToken[Unit] and RoutePath[A]
     */
    def parse[A](head: RouteToken[Unit], tail: RoutePath[A]): RoutePath[A] =
      RoutePath.Cons(head, tail)

    /**
     * Create RoutePath[C] by combining RouteToken[B] and RoutePath[A]. Return type C is determined by implicit combine
     */
    def parse[A, B, C](head: RouteToken[B], tail: RoutePath[A])(implicit ev: RouteCombine.Aux[A, B, C]): RoutePath[C] =
      RoutePath.Cons(head, tail)

    case object Empty extends RoutePath[Unit]

    private[zhttp] final case class Cons[A, B, C](head: RouteToken[B], tail: RoutePath[A]) extends RoutePath[C]

    def extract[A](r: RoutePath[A], p: Path): Option[A] = extract(r, p.toList.reverse)

    /**
     * Recursively iterate through RoutePath and List of String. Returns Option of tuple in case of matching RoutePath
     * and path list
     */
    private def extract[A](r: RoutePath[A], p: List[String]): Option[A] = {
      @tailrec
      def loop(r: RoutePath[Any], p: List[String], output: List[Any]): Option[Any] = {
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
      loop(r.asInstanceOf[RoutePath[Any]], p, List.empty[Any]).asInstanceOf[Option[A]]
    }
  }

  sealed trait RouteToken[+A] { self =>
    def extract(string: String): Option[A] = RouteToken.extract(self, string)
  }
  object RouteToken           {
    private[zhttp] final case class Literal(s: String)         extends RouteToken[Unit]
    private[zhttp] final case class Param[A](r: RouteParam[A]) extends RouteToken[A]

    def extract[A](rt: RouteToken[A], string: String): Option[A] = rt match {
      case RouteToken.Literal(s) => if (s == string) Route.unit else None
      case RouteToken.Param(r)   => r.extract(string)
    }
  }

  /**
   * Create Route[Unit] from a Method
   */
  def fromMethod(method: Method): Route[Unit] = Route(method, RoutePath.Empty)

  /**
   * Create RouteToken of Param category
   */
  def apply[A](implicit ev: RouteParam[A]): RouteToken[A] = RouteToken.Param(ev)

  /**
   * Extract route params from Route and Request path
   */
  def extract[A](path: Path, route: Route[A]): Option[A] = route.routePath.extract(path)

  /**
   * Extract route params from Route and Request
   */
  def extract[A](request: Request, self: Route[A]): Option[A] =
    if (self.method == request.method) { self.extract(request.path) }
    else None

  trait RouteParam[A] {

    /**
     * Take path string and return specialised RouteParam
     */
    def extract(data: String): Option[A]
  }
  object RouteParam {
    implicit object IntExtract     extends RouteParam[Int]     {
      override def extract(data: String): Option[Int] = Try(data.toInt).toOption
    }
    implicit object StringExtract  extends RouteParam[String]  {
      override def extract(data: String): Option[String] = Option(data)
    }
    implicit object BooleanExtract extends RouteParam[Boolean] {
      override def extract(data: String): Option[Boolean] = Try(data.toBoolean).toOption
    }
  }

  /**
   * Operator to create RouteToken with param of type A
   */
  final def *[A](implicit ev: RouteParam[A]): RouteToken[A] = Route[A]
}
