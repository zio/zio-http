package zhttp.http

import io.netty.handler.codec.http.HttpScheme
sealed trait Scheme { self =>
  def asString: String = Scheme.asString(self)
}
object Scheme       {
  def asString(self: Scheme): String = self match {
    case HTTP  => "http"
    case HTTPS => "https"
  }

  case object HTTP  extends Scheme
  case object HTTPS extends Scheme

  def fromJScheme(scheme: HttpScheme): Option[Scheme] =
    scheme match {
      case HttpScheme.HTTPS => Option(HTTPS)
      case HttpScheme.HTTP  => Option(HTTP)
      case _                => None
    }

  def fromString(scheme: String): Option[Scheme] =
    scheme.toUpperCase match {
      case "HTTPS" => Option(HTTPS)
      case "HTTP"  => Option(HTTP)
      case _       => None
    }
}
