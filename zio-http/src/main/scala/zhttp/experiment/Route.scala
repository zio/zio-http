package zhttp.experiment
import zhttp.experiment.Route.{RoutePath, RouteToken}
import zhttp.http.HttpApp.HttpAppConstructor
import zhttp.http._

import scala.annotation.tailrec
import scala.util.Try

final case class Route[A](method: Method, routePath: RoutePath[A]) { self =>

  /**
   * Create a new Route[A] with existing RoutePath and new string
   */
  def /(name: String): Route[A] =
    Route(self.method, RoutePath(RouteToken.Literal(name), self.routePath))

  /**
   * Create a new Route[C] by combining RouteToken[B] and existing RoutePath[A]. C here is path dependent type and will
   * be resolved using combing aux implicit
   */
  def /[B, C](other: RouteToken[B])(implicit ev: Route.Combine.Aux[A, B, C]): Route[C] =
    Route(self.method, RoutePath(other, self.routePath))

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
  def apply(path: Path): Option[A] = self.extract(path)

  /**
   * Execute extract on request and returns Option[A]
   */
  def apply(request: Request): Option[A] = self.extract(request)

  /**
   * Creates an HttpApp from a Request to Response function
   */
  def to[B](f: Request => B)(implicit ctor: HttpAppConstructor[B]): HttpApp[ctor.ROut, ctor.EOut] =
    ctor.make[A](self, f)
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
    def apply[A](head: RouteToken[Unit], tail: RoutePath[A]): RoutePath[A] =
      RoutePath.Cons(head, tail)

    /**
     * Create RoutePath[C] by combining RouteToken[B] and RoutePath[A]. Return type C is determined by implicit combine
     */
    def apply[A, B, C](head: RouteToken[B], tail: RoutePath[A])(implicit ev: Combine.Aux[A, B, C]): RoutePath[C] =
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
                case Some(value) => {
                  if (value.isInstanceOf[Unit]) loop(tail, p.tail, output) else loop(tail, p.tail, value :: output)
                }
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
   * Create a Route[Unit] with Method type GET
   */
  def get: Route[Unit] = fromMethod(Method.GET)

  /**
   * Create a Route[Unit] with Method type POST
   */
  def post: Route[Unit] = fromMethod(Method.POST)

  /**
   * Create a Route[Unit] with Method type PUT
   */
  def put: Route[Unit] = fromMethod(Method.PUT)

  /**
   * Create a Route[Unit] with Method type DELETE
   */
  def delete: Route[Unit] = fromMethod(Method.DELETE)

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

  // Combine Logic for Router
  sealed trait Combine[A, B] {
    type Out
  }

  object Combine {
    type Aux[A, B, C] = Combine[A, B] {
      type Out = C
    }

    // scalafmt: { maxColumn = 1200 }

    /**
     * Path dependent implicit for combination of (A,B)
     */
    implicit def combine0[A, B](implicit ev: A =:= Unit): Combine.Aux[A, B, B]                                                                                                                                                                                                                                                                                                                     = null
    implicit def combine1[A, B](implicit evA: RouteParam[A], evB: RouteParam[B]): Combine.Aux[A, B, (A, B)]                                                                                                                                                                                                                                                                                        = null
    implicit def combine2[A, B, T1, T2](implicit evA: A =:= (T1, T2), evB: RouteParam[B]): Combine.Aux[A, B, (T1, T2, B)]                                                                                                                                                                                                                                                                          = null
    implicit def combine3[A, B, T1, T2, T3](implicit evA: A =:= (T1, T2, T3), evB: RouteParam[B]): Combine.Aux[A, B, (T1, T2, T3, B)]                                                                                                                                                                                                                                                              = null
    implicit def combine4[A, B, T1, T2, T3, T4](implicit evA: A =:= (T1, T2, T3, T4), evB: RouteParam[B]): Combine.Aux[A, B, (T1, T2, T3, T4, B)]                                                                                                                                                                                                                                                  = null
    implicit def combine5[A, B, T1, T2, T3, T4, T5](implicit evA: A =:= (T1, T2, T3, T4, T5), evB: RouteParam[B]): Combine.Aux[A, B, (T1, T2, T3, T4, T5, B)]                                                                                                                                                                                                                                      = null
    implicit def combine6[A, B, T1, T2, T3, T4, T5, T6](implicit evA: A =:= (T1, T2, T3, T4, T5, T6), evB: RouteParam[B]): Combine.Aux[A, B, (T1, T2, T3, T4, T5, T6, B)]                                                                                                                                                                                                                          = null
    implicit def combine7[A, B, T1, T2, T3, T4, T5, T6, T7](implicit evA: A =:= (T1, T2, T3, T4, T5, T6, T7), evB: RouteParam[B]): Combine.Aux[A, B, (T1, T2, T3, T4, T5, T6, T7, B)]                                                                                                                                                                                                              = null
    implicit def combine8[A, B, T1, T2, T3, T4, T5, T6, T7, T8](implicit evA: A =:= (T1, T2, T3, T4, T5, T6, T7, T8), evB: RouteParam[B]): Combine.Aux[A, B, (T1, T2, T3, T4, T5, T6, T7, T8, B)]                                                                                                                                                                                                  = null
    implicit def combine9[A, B, T1, T2, T3, T4, T5, T6, T7, T8, T9](implicit evA: A =:= (T1, T2, T3, T4, T5, T6, T7, T8, T9), evB: RouteParam[B]): Combine.Aux[A, B, (T1, T2, T3, T4, T5, T6, T7, T8, T9, B)]                                                                                                                                                                                      = null
    implicit def combine10[A, B, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10](implicit evA: A =:= (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10), evB: RouteParam[B]): Combine.Aux[A, B, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, B)]                                                                                                                                                                      = null
    implicit def combine11[A, B, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11](implicit evA: A =:= (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11), evB: RouteParam[B]): Combine.Aux[A, B, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, B)]                                                                                                                                                       = null
    implicit def combine12[A, B, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12](implicit evA: A =:= (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12), evB: RouteParam[B]): Combine.Aux[A, B, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, B)]                                                                                                                                        = null
    implicit def combine13[A, B, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13](implicit evA: A =:= (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13), evB: RouteParam[B]): Combine.Aux[A, B, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, B)]                                                                                                                         = null
    implicit def combine14[A, B, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14](implicit evA: A =:= (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14), evB: RouteParam[B]): Combine.Aux[A, B, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, B)]                                                                                                          = null
    implicit def combine15[A, B, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15](implicit evA: A =:= (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15), evB: RouteParam[B]): Combine.Aux[A, B, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, B)]                                                                                           = null
    implicit def combine16[A, B, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16](implicit evA: A =:= (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16), evB: RouteParam[B]): Combine.Aux[A, B, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, B)]                                                                            = null
    implicit def combine17[A, B, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17](implicit evA: A =:= (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17), evB: RouteParam[B]): Combine.Aux[A, B, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, B)]                                                             = null
    implicit def combine18[A, B, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18](implicit evA: A =:= (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18), evB: RouteParam[B]): Combine.Aux[A, B, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, B)]                                              = null
    implicit def combine19[A, B, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19](implicit evA: A =:= (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19), evB: RouteParam[B]): Combine.Aux[A, B, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, B)]                               = null
    implicit def combine20[A, B, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20](implicit evA: A =:= (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20), evB: RouteParam[B]): Combine.Aux[A, B, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, B)]                = null
    implicit def combine21[A, B, T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21](implicit evA: A =:= (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21), evB: RouteParam[B]): Combine.Aux[A, B, (T1, T2, T3, T4, T5, T6, T7, T8, T9, T10, T11, T12, T13, T14, T15, T16, T17, T18, T19, T20, T21, B)] = null
    // scalafmt: { maxColumn = 120 }
  }

}
