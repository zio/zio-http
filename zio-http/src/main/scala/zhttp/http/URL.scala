package zhttp.http

import io.netty.handler.codec.http.QueryStringDecoder

import java.net.URI
import scala.util.Try
import scala.jdk.CollectionConverters._

case class URL(path: Path, kind: URL.Location = URL.Location.Relative, query: String = "") { self =>
  val host: Option[String] = kind match {
    case URL.Location.Relative      => None
    case abs: URL.Location.Absolute => Option(abs.host)
  }

  val port: Option[Int] = kind match {
    case URL.Location.Relative      => None
    case abs: URL.Location.Absolute => Option(abs.port)
  }

  lazy val queryParams: Map[String, List[String]] = {
    val decoder = new QueryStringDecoder(query, false)
    val params = decoder.parameters()
    params.asScala.view.mapValues(_.asScala.toList).toMap
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

  def fromString(string: String): Either[HttpError, URL] = {
    val invalidURL = Left(HttpError.BadRequest(s"Invalid URL: $string"))
    for {
      url <- Try(new URI(string)).toEither match {
        case Left(_)      => invalidURL
        case Right(value) => Right(value)
      }
      url <- (if (url.isAbsolute) fromAbsoluteURI(url) else fromRelativeURI(url)) match {
        case None        => invalidURL
        case Some(value) => Right(value)
      }

    } yield url
  }

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
