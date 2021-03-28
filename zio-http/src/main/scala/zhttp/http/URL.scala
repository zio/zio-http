package zhttp.http

import java.net.{URI, URISyntaxException}
import scala.util.Try

case class URL(path: Path, kind: URL.Location = URL.Location.Relative, query: String = "") { self =>
  val host: Option[String] = kind match {
    case URL.Location.Relative      => None
    case abs: URL.Location.Absolute => Option(abs.host)
  }

  val port: Option[Int] = kind match {
    case URL.Location.Relative      => None
    case abs: URL.Location.Absolute => Option(abs.port)
  }

  def asString: String = URL.asString(self)
}
object URL                                                                                 {
  sealed trait Location
  object Location {
    case object Relative                                         extends Location
    case class Absolute(scheme: Scheme, host: String, port: Int) extends Location
  }

  private def fromAbsoluteURI(uri: URI): Option[URL] = for {
    scheme <- Scheme.fromString(uri.getScheme)
    host   <- Option(uri.getHost)
    port   <- Option(uri.getPort)
    path   <- Option(uri.getPath)
    query      = Option(uri.getRawQuery).getOrElse("")
    connection = URL.Location.Absolute(scheme, host, port)
  } yield URL(Path(path), connection, query)

  private def fromRelativeURI(uri: URI): Option[URL] = for {
    path <- Option(uri.getPath)
    query = Option(uri.getRawQuery).getOrElse("")
  } yield URL(Path(path), Location.Relative, query)

  def fromString(string: String): Either[Throwable, URL] =
    for {
      uri <- Try(new URI(string)).toEither
      url <- (if (uri.isAbsolute) fromAbsoluteURI(uri) else fromRelativeURI(uri)) match {
        case Some(value) => Right(value)
        case None        => Left(new URISyntaxException(string, "Invalid URL"))
      }

    } yield url

  def asString(url: URL): String = {
    def withQuery(u: String): String =
      if (url.query.nonEmpty) {
        s"$u?${url.query}"
      } else {
        u
      }

    url.kind match {
      case Location.Relative                     => withQuery(url.path.asString)
      case Location.Absolute(scheme, host, port) =>
        (scheme, port) match {
          case (Scheme.HTTP, 80)   => withQuery(s"${scheme.asString}://${host}${url.path.asString}")
          case (Scheme.HTTPS, 443) => withQuery(s"${scheme.asString}://${host}${url.path.asString}")
          case (_, port)           => withQuery(s"${scheme.asString}://${host}:${port}${url.path.asString}")
        }
    }
  }
}
