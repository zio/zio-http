package zhttp.http

import io.netty.handler.codec.http.{QueryStringDecoder, QueryStringEncoder}
import zhttp.http.URL.Fragment

import java.net.URI
import scala.jdk.CollectionConverters._
import scala.util.Try

final case class URL(
  path: Path,
  kind: URL.Location = URL.Location.Relative,
  queryParams: Map[String, List[String]] = Map.empty,
  fragment: Option[Fragment] = None,
) { self =>
  val host: Option[String] = kind match {
    case URL.Location.Relative      => None
    case abs: URL.Location.Absolute => Option(abs.host)
  }

  val port: Option[Int] = kind match {
    case URL.Location.Relative      => None
    case abs: URL.Location.Absolute => Option(abs.port)
  }

  private[zhttp] def relative: URL = self.kind match {
    case URL.Location.Relative => self
    case _                     => self.copy(kind = URL.Location.Relative)
  }

  def asString: String = URL.asString(self)
}
object URL {
  sealed trait Location
  object Location {
    case object Relative                                               extends Location
    final case class Absolute(scheme: Scheme, host: String, port: Int) extends Location
  }

  private def queryParams(query: String) = {
    if (query == null || query.isEmpty) {
      Map.empty[String, List[String]]
    } else {
      val decoder = new QueryStringDecoder(query, false)
      val params  = decoder.parameters()
      params.asScala.view.map { case (k, v) => (k, v.asScala.toList) }.toMap
    }
  }

  private def fromAbsoluteURI(uri: URI): Option[URL] = {

    def portFromScheme(scheme: Scheme): Int = scheme match {
      case Scheme.HTTP | Scheme.WS => 80
      case Scheme.HTTPS            => 443
    }

    for {
      scheme <- Scheme.fromString(uri.getScheme)
      host   <- Option(uri.getHost)
      path   <- Option(uri.getRawPath)
      port       = Option(uri.getPort).filter(_ != -1).getOrElse(portFromScheme(scheme))
      connection = URL.Location.Absolute(scheme, host, port)
    } yield URL(Path(path), connection, queryParams(uri.getRawQuery), Fragment.fromURI(uri))
  }

  private def fromRelativeURI(uri: URI): Option[URL] = for {
    path <- Option(uri.getRawPath)
  } yield URL(Path(path), Location.Relative, queryParams(uri.getRawQuery), Fragment.fromURI(uri))

  def fromString(string: String): Either[HttpError, URL] = {
    def invalidURL = Left(HttpError.BadRequest(s"Invalid URL: $string"))
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

    def path: String = {
      val encoder = new QueryStringEncoder(s"${url.path.asString}${url.fragment.fold("")(f => "#" + f.raw)}")
      url.queryParams.foreach { case (key, values) =>
        if (key != "") values.foreach { value => encoder.addParam(key, value) }
      }
      encoder.toString
    }

    url.kind match {
      case Location.Relative                     => path
      case Location.Absolute(scheme, host, port) =>
        if (port == 80 || port == 443) s"${scheme.asString}://$host$path"
        else s"${scheme.asString}://$host:$port$path"
    }
  }

  case class Fragment private (raw: String, decoded: String)
  object Fragment {
    def fromURI(uri: URI): Option[Fragment] = for {
      raw     <- Option(uri.getRawFragment)
      decoded <- Option(uri.getFragment)
    } yield Fragment(raw, decoded)
  }

  def root: URL = URL(!!)
}
