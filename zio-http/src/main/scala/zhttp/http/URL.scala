package zhttp.http

import java.net.{URI, URISyntaxException}
import scala.util.Try

case class URL(path: Path, kind: URL.Location = URL.Location.Relative) { self =>
  val host: Option[String] = kind match {
    case URL.Location.Relative             => None
    case URL.Location.Absolute(_, host, _) => Option(host)
  }

  val port: Option[Int] = kind match {
    case URL.Location.Relative             => None
    case URL.Location.Absolute(_, _, port) => Option(port)
  }

  def asString: String = URL.asString(self)
}
object URL                                                             {
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
    connection = URL.Location.Absolute(scheme, host, port)
  } yield URL(Path(path), connection)

  private def fromRelativeURI(uri: URI): Option[URL] = for {
    path <- Option(uri.getPath)
  } yield URL(Path(path), Location.Relative)

  def fromString(string: String): Either[Throwable, URL] =
    for {
      uri <- Try(new URI(string)).toEither
      url <- (if (uri.isAbsolute) fromAbsoluteURI(uri) else fromRelativeURI(uri)) match {
        case Some(value) => Right(value)
        case None        => Left(new URISyntaxException(string, "Invalid URL"))
      }

    } yield url

  def asString(url: URL): String = url.kind match {
    case Location.Relative                     => url.path.asString
    case Location.Absolute(scheme, host, port) =>
      port match {
        case 80   => s"${scheme.asString}://${host}${url.path.asString}"
        case port => s"${scheme.asString}://${host}:${port}${url.path.asString}"
      }
  }
}
