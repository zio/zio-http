package zhttp.experiment
import zhttp.experiment.Route.{RoutePath, RouteToken}
import zhttp.http._

case class Route[A](method: Method, routePath: RoutePath[A]) { self =>
  def /(name: String): Route[A]                                                        =
    Route(self.method, RoutePath(RouteToken.Literal(name), self.routePath))
  def /[B, C](other: RouteToken[B])(implicit ev: Route.Combine.Aux[A, B, C]): Route[C] =
    Route(self.method, RoutePath(other, self.routePath))
  def extract(request: Request): Option[A]                                             = Route.extract(request, self)
  def extract(path: Path): Option[A]                                                   = Route.extract(path, self)
  def apply(path: Path): Option[A]                                                     = self.extract(path)
  def apply(request: Request): Option[A]                                               = self.extract(request)
}

object Route {
  private def unit[A]: Option[A] = Option(().asInstanceOf[A])
  sealed trait RoutePath[+A] { self =>
    def extract(path: Path): Option[A] = RoutePath.extract(self, path)
    def extractCount: Int              = RoutePath.extractCount(self)
  }
  object RoutePath           {
    def empty: RoutePath[Unit] = Empty

    def apply[A](head: RouteToken[Unit], tail: RoutePath[A]): RoutePath[A] =
      RoutePath.Cons(head, tail)

    def apply[A, B, C](head: RouteToken[B], tail: RoutePath[A])(implicit ev: Combine.Aux[A, B, C]): RoutePath[C] =
      RoutePath.Cons(head, tail)

    case object Empty extends RoutePath[Unit]

    case class Cons[A, B, C](head: RouteToken[B], tail: RoutePath[A]) extends RoutePath[C]

    def extractCount[A](r: RoutePath[A]): Int = r match {
      case Empty            => 0
      case Cons(head, tail) =>
        head match {
          case RouteToken.Literal(_) => tail.extractCount
          case RouteToken.Param(_)   => 1 + tail.extractCount
        }
    }

    def extract[A](r: RoutePath[A], p: Path): Option[A] = extract(r, p.toList.reverse)

    private def extract[A](r: RoutePath[A], p: List[String]): Option[A] = {
      def loop(r: RoutePath[Any], p: List[String], output: List[Any]): Option[Any] = {
        r match {
          case Empty            => {
            if (output == List()) { Some(()) }
            else
              output match {
                // scalafmt: { maxColumn = 1200 }
                case List(a0)                                                                                            => Some(a0)
                case List(a0, a1)                                                                                        => Some((a0, a1))
                case List(a0, a1, a2)                                                                                    => Some((a0, a1, a2))
                case List(a0, a1, a2, a3)                                                                                => Some((a0, a1, a2, a3))
                case List(a0, a1, a2, a3, a4)                                                                            => Some((a0, a1, a2, a3, a4))
                case List(a0, a1, a2, a3, a4, a5)                                                                        => Some((a0, a1, a2, a3, a4, a5))
                case List(a0, a1, a2, a3, a4, a5, a6)                                                                    => Some((a0, a1, a2, a3, a4, a5, a6))
                case List(a0, a1, a2, a3, a4, a5, a6, a7)                                                                => Some((a0, a1, a2, a3, a4, a5, a6, a7))
                case List(a0, a1, a2, a3, a4, a5, a6, a7, a8)                                                            => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8))
                case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9)                                                        => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9))
                case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10)                                                   => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10))
                case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11)                                              => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11))
                case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12)                                         => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12))
                case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13)                                    => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13))
                case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14)                               => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14))
                case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15)                          => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15))
                case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16)                     => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16))
                case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17)                => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17))
                case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18)           => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18))
                case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19)      => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19))
                case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20) => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a20))
                case List(a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a21) => Some((a0, a1, a2, a3, a4, a5, a6, a7, a8, a9, a10, a11, a12, a13, a14, a15, a16, a17, a18, a19, a21))
                case _                                                                                                   => None
              }
            // scalafmt: { maxColumn = 120 }
          }
          case Cons(head, tail) =>
            if (p.isEmpty) None
            else {
              head.extract(p.head) match {
                case Some(value) => {
                  if (value == ()) loop(tail, p.tail, output) else loop(tail, p.tail, value :: output)
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
    case class Literal(s: String)         extends RouteToken[Unit]
    case class Param[A](r: RouteParam[A]) extends RouteToken[A]
    def extract[A](rt: RouteToken[A], string: String): Option[A] = rt match {
      case RouteToken.Literal(s) => if (s == string) Route.unit else None
      case RouteToken.Param(r)   => r.extract(string)
    }
  }

  def fromMethod(method: Method): Route[Unit] = Route(method, RoutePath.Empty)
  def get: Route[Unit]                        = fromMethod(Method.GET)
  def post: Route[Unit]                       = fromMethod(Method.POST)
  def put: Route[Unit]                        = fromMethod(Method.PUT)
  def delete: Route[Unit]                     = fromMethod(Method.DELETE)

  def apply[A](implicit ev: RouteParam[A]): RouteToken[A] = RouteToken.Param(ev)

  def extract[A](path: Path, route: Route[A]): Option[A]      = route.routePath.extract(path)
  def extract[A](request: Request, self: Route[A]): Option[A] =
    if (self.method == request.method) { self.extract(request.path) }
    else None

  // Extract
  trait RouteParam[A] {
    def extract(data: String): Option[A]
  }
  object RouteParam   {
    implicit object IntExtract     extends RouteParam[Int]     {
      override def extract(data: String): Option[Int] = data.toIntOption
    }
    implicit object StringExtract  extends RouteParam[String]  {
      override def extract(data: String): Option[String] = Option(data)
    }
    implicit object BooleanExtract extends RouteParam[Boolean] {
      override def extract(data: String): Option[Boolean] = data.toBooleanOption
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
