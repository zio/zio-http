package zhttp.experiment

import zhttp.http._
import zio.ZIO

/**
 * Middlewares for HttpApp.
 */
sealed trait HttpMiddleware[-R, +E] { self =>
  def run[R1 <: R, E1 >: E](app: HttpApp[R1, E1]): HttpApp[R1, E1] = ???

  def apply[R1 <: R, E1 >: E](app: HttpApp[R1, E1]): HttpApp[R1, E1] = run(app)

  def ++[R1 <: R, E1 >: E](other: HttpMiddleware[R1, E1]): HttpMiddleware[R1, E1] =
    self combine other

  def combine[R1 <: R, E1 >: E](other: HttpMiddleware[R1, E1]): HttpMiddleware[R1, E1] =
    HttpMiddleware.Combine(self, other)
}

object HttpMiddleware {

  sealed trait Patch { self =>
    def ++(that: Patch): Patch = Patch.Combine(self, that)
  }

  object Patch {
    case object Empty                                     extends Patch
    final case class AddHeaders(headers: List[Header])    extends Patch
    final case class RemoveHeaders(headers: List[String]) extends Patch
    final case class SetStatus(status: Status)            extends Patch
    final case class Combine(left: Patch, right: Patch)   extends Patch

    val empty: Patch                                = Empty
    def addHeaders(headers: List[Header]): Patch    = AddHeaders(headers)
    def removeHeaders(headers: List[String]): Patch = RemoveHeaders(headers)
    def setStatus(status: Status): Patch            = SetStatus(status)
  }

  sealed trait RequestMiddleware[-R, +E, +S] {
    def process(method: Method, url: URL, headers: List[Header]): ZIO[R, E, S] = ???
  }

  object RequestMiddleware {
    case class ConsM[R, E, S](f: (Method, URL, List[Header]) => ZIO[R, E, S]) extends RequestMiddleware[R, E, S]
    case class Cons[S](f: (Method, URL, List[Header]) => S)                   extends RequestMiddleware[Any, Nothing, S]
    case object Identity extends RequestMiddleware[Any, Nothing, Unit]

    def identity: RequestMiddleware[Any, Nothing, Unit] = new RequestMiddleware[Any, Nothing, Unit] {
      override def process(method: Method, url: URL, headers: List[Header]): ZIO[Any, Nothing, Unit] = ZIO.unit
    }

    def makeM[R, E, S](f: (Method, URL, List[Header]) => ZIO[R, E, S]): RequestMiddleware[R, E, S] =
      ConsM(f)

    def make[R, E, S](f: (Method, URL, List[Header]) => S): RequestMiddleware[R, E, S] =
      Cons(f)

    def apply[R, E, S](f: (Method, URL, List[Header]) => S): RequestMiddleware[R, E, S] =
      make(f)
  }

  sealed trait ResponseMiddleware[-R, +E, -S] {
    def apply[S1 <: S](status: Status, headers: List[Header], state: S1): ZIO[R, E, Patch]
  }

  object ResponseMiddleware {
    case class ConsM[R, E, S](f: (Status, List[Header], S) => ZIO[R, E, Patch]) extends ResponseMiddleware[R, E, S]
    case class Cons[S](f: (Status, List[Header], S) => Patch) extends ResponseMiddleware[Any, Nothing, S]
    case object Identity                                      extends ResponseMiddleware[Any, Nothing, Unit]

    def identity: ResponseMiddleware[Any, Nothing, Unit] = Identity

    def makeM[S]: PartiallyAppliedMakeM[S] = PartiallyAppliedMakeM(())
    def make[S]: PartiallyAppliedMake[S]   = PartiallyAppliedMake(())

    final case class PartiallyAppliedMakeM[S](unit: Unit) extends AnyVal {
      def apply[R, E](f: (Status, List[Header], S) => ZIO[R, E, Patch]): ResponseMiddleware[R, E, S] = ConsM(f)
    }

    final case class PartiallyAppliedMake[S](unit: Unit) extends AnyVal {
      def apply(f: (Status, List[Header], S) => Patch): ResponseMiddleware[Any, Nothing, S] = Cons(f)
    }
  }

  case object Identity extends HttpMiddleware[Any, Nothing]

  case class Transform[R, E, S](req: RequestMiddleware[R, E, S], res: ResponseMiddleware[R, E, S])
      extends HttpMiddleware[R, E]

  case class Combine[R, E](self: HttpMiddleware[R, E], other: HttpMiddleware[R, E]) extends HttpMiddleware[R, E]

  def identity: HttpMiddleware[Any, Nothing] = Identity

  def make[R, E, S](req: RequestMiddleware[R, E, S], res: ResponseMiddleware[R, E, S]): HttpMiddleware[R, E] =
    Transform(req, res)
}
