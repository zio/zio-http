package zio.web.http.model

sealed trait HttpAnn[+A]
sealed abstract class Method(name: String) extends HttpAnn[Unit] {
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
  def apply(v: String): Route[Unit] = Path(v)

  final case class Path(path: String) extends Route[Unit]
}
