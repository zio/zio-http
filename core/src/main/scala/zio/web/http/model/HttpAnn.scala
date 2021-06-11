package zio.web.http.model

import java.util.UUID
import zio.web.internal.Combine

sealed trait HttpAnn[+A]
sealed abstract class Method(val name: String) extends HttpAnn[Unit] {
  override def toString(): String = s"Method.$name"
}

object Method {

  // expose widened cases (simulating Scala 3 enums behavior) to help with type inference
  private object singleton {
    object OPTIONS extends Method("OPTIONS")
    object GET     extends Method("GET")
    object HEAD    extends Method("HEAD")
    object POST    extends Method("POST")
    object PUT     extends Method("PUT")
    object PATCH   extends Method("PATCH")
    object DELETE  extends Method("DELETE")
    object TRACE   extends Method("TRACE")
    object CONNECT extends Method("CONNECT")
  }

  val OPTIONS: Method = singleton.OPTIONS
  val GET: Method     = singleton.GET
  val HEAD: Method    = singleton.HEAD
  val POST: Method    = singleton.POST
  val PUT: Method     = singleton.PUT
  val PATCH: Method   = singleton.PATCH
  val DELETE: Method  = singleton.DELETE
  val TRACE: Method   = singleton.TRACE
  val CONNECT: Method = singleton.CONNECT

  def fromString(method: String): Method =
    method match {
      case "OPTIONS" => Method.OPTIONS
      case "GET"     => Method.GET
      case "HEAD"    => Method.HEAD
      case "POST"    => Method.POST
      case "PUT"     => Method.PUT
      case "PATCH"   => Method.PATCH
      case "DELETE"  => Method.DELETE
      case "TRACE"   => Method.TRACE
      case "CONNECT" => Method.CONNECT
      case _         => throw new IllegalArgumentException(s"Unable to handle method: $method")
    }
}

sealed trait Route[+A] extends HttpAnn[A]

object Route {
  case class Cons[P0, H <: Segment[P0], P, T <: Route[P], O] private (
    head: H,
    tail: T,
    combine: Combine.Aux[P0, P, O]
  ) extends Route[O]

  sealed trait Root extends Route[Unit]
  case object Root  extends Root

  sealed trait Segment[A]

  object Segment {
    final private[http] case class Static(value: String) extends Segment[Unit]

    final case class Param[A](from: String => A, to: A => String) extends Segment[A] {

      def derive[B](map: A => B, contramap: B => A): Param[B] =
        Param[B](v => map(from(v)), v => to(contramap(v)))
    }
  }

  val IntVal    = Segment.Param[Int](_.toInt, _.toString)
  val LongVal   = Segment.Param[Long](_.toLong, _.toString)
  val StringVal = Segment.Param[String](identity, identity)
  val UUIDVal   = Segment.Param[UUID](UUID.fromString, _.toString)

  def apply[A](f: Root => Route[A]): Route[A] = f(Root)

  implicit class RouteOps[P](tail: Route[P]) {

    final def /(segment: String)(implicit c: Combine[Unit, P]): Route[c.Out] =
      Cons[Unit, Segment.Static, P, Route[P], c.Out](Segment.Static(segment), tail, c)

    final def /[P0](param: Segment.Param[P0])(implicit c: Combine[P0, P]): Route[c.Out] =
      Cons[P0, Segment.Param[P0], P, Route[P], c.Out](param, tail, c)
  }
}
