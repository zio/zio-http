package zio.web.http.model

import zio.{App => _, _}
import com.github.ghik.silencer.silent

sealed trait Route

object Route {

  @silent("never used")
  def apply(v: String): Route = End

  sealed trait Matcher[A] {
    def isDefined(input: Chunk[String]): Option[(Chunk[String], Chunk[String])]

    def materialize(input: Chunk[String]): A
  }

  sealed case class /: [M <: Matcher[_], R <: Route] private[web] (matcher: M, route: R) extends Route
  sealed trait End extends Route
  private[web] case object End extends End

  implicit class RouteOps[R <: Route](route: R) {
    def /: [M <: Matcher[_]](matcher: M): M /: R = new /: (matcher, route)
  }
}

sealed trait ParamBag
object ParamBag {
  sealed case class <<: [B <: ParamBag, L] private[web] (bag: B, last: L) extends ParamBag
  sealed trait Bag extends ParamBag
  private[web] case object Bag extends Bag

  implicit class ParamBagOps[B <: ParamBag](bag: B) {
    def <<: [L](last: L): B <<: L = new <<: (bag, last)
  }
}



sealed trait Route2

object Route2 {

  sealed trait PathMatcher[A] { self =>

    /**
      * Check if the path matches the pattern.
      */
    def isDefined(input: Chunk[String]): Option[(Chunk[String], Chunk[String])]

    /**
      * Turn a matched chunk into a value.
      */
    def materialize(input: Chunk[String]): A
  }

  object PathMatcher {

    implicit class PathMatcherOps[A](self: PathMatcher[A]) {
      @silent("never used")
      def / [B, P <: PathMatcher[B]](that: P)(implicit ev1: B =:= Unit, ev2: P =!= Root.type): PathMatcher[A] = ???
      
      @silent("never used")
      def / [B](that: PathMatcher[B])(implicit ev: B =!= Unit): PathMatcher[(A, B)] = ???
    }

    case object Root extends PathMatcher[Unit] {
      def isDefined(input: Chunk[String]): Option[(Chunk[String], Chunk[String])] =
        Option(input.splitAt(0)).filter(_._1 == Chunk.single("/"))

      def materialize(input: Chunk[String]): Unit = ()
    }

    final case class Const(value: Chunk[String]) extends PathMatcher[Unit] {
      def isDefined(input: Chunk[String]): Option[(Chunk[String], Chunk[String])] =
        Option(input.splitAt(value.length)).filter(_._1 == value)

      def materialize(input: Chunk[String]): Unit = ()
    }

    private [web] val / : PathMatcher[Unit] = Const(Chunk.single("/"))

    case object StringVal extends PathMatcher[String] {
      def isDefined(input: Chunk[String]): Option[(Chunk[String], Chunk[String])] =
        Option(input.splitWhere(_ == "/")).filter(_._1.nonEmpty)

      def materialize(input: Chunk[String]): String = input.toArray.mkString
    }

    case object $ extends PathMatcher[Unit] {
      def isDefined(input: Chunk[String]): Option[(Chunk[String], Chunk[String])] =
        Option(input).collect { case v @ Chunk.empty => (v, v) }

      def materialize(input: Chunk[String]): Unit = ()
    }
  }
}

// want to be able to represent routes like
//   /users
//   /users/{id}/edit
//   /users/{id}/edit/{section}
// and map the params to handler inputs

