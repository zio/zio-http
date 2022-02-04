package zhttp.http

import io.netty.handler.codec.http.{QueryStringDecoder, QueryStringEncoder}
import zhttp.http.URL.{Fragment, Location}

import java.net.URI
import scala.jdk.CollectionConverters._
import scala.util.Try

final case class URL(
  path: Path,
  kind: URL.Location = URL.Location.Relative,
  queryParams: Map[String, List[String]] = Map.empty,
  fragment: Option[Fragment] = None,
) { self =>
  def encode: String = URL.encode(self)

  def host: Option[String] = kind match {
    case URL.Location.Relative      => None
    case abs: URL.Location.Absolute => Option(abs.host)
  }

  def isAbsolute: Boolean = self.kind match {
    case Location.Absolute(_, _, _) => true
    case Location.Relative          => false
  }

  def isRelative: Boolean = !isAbsolute

  def port: Option[Int] = kind match {
    case URL.Location.Relative      => None
    case abs: URL.Location.Absolute => Option(abs.port)
  }

  def setHost(host: String): URL = {
    val location = kind match {
      case URL.Location.Relative      => URL.Location.Absolute(Scheme.HTTP, host, URL.portFromScheme(Scheme.HTTP))
      case abs: URL.Location.Absolute => abs.copy(host = host)
    }
    copy(kind = location)
  }

  def setPath(path: String): URL = copy(path = Path(path))

  def setPath(path: Path): URL =
    copy(path = path)

  def setPort(port: Int): URL = {
    val location = kind match {
      case URL.Location.Relative      => URL.Location.Absolute(Scheme.HTTP, "", port)
      case abs: URL.Location.Absolute => abs.copy(port = port)
    }

    copy(kind = location)
  }

  def setQueryParams(query: String): URL =
    copy(queryParams = URL.queryParams(query))

  def setQueryParams(queryParams: Map[String, List[String]]): URL =
    copy(queryParams = queryParams)

  def setScheme(scheme: Scheme): URL = {
    val location = kind match {
      case URL.Location.Relative      => URL.Location.Absolute(scheme, "", URL.portFromScheme(scheme))
      case abs: URL.Location.Absolute => abs.copy(scheme = scheme)
    }

    copy(kind = location)
  }

  private[zhttp] def relative: URL = self.kind match {
    case URL.Location.Relative => self
    case _                     => self.copy(kind = URL.Location.Relative)
  }
}
object URL {
  def empty: URL = root

  def encode(url: URL): String = {

    def path: String = {
      val encoder = new QueryStringEncoder(s"${url.path.encode}${url.fragment.fold("")(f => "#" + f.raw)}")
      url.queryParams.foreach { case (key, values) =>
        if (key != "") values.foreach { value => encoder.addParam(key, value) }
      }
      encoder.toString
    }

    url.kind match {
      case Location.Relative                     => path
      case Location.Absolute(scheme, host, port) =>
        if (port == 80 || port == 443) s"${scheme.encode}://$host$path"
        else s"${scheme.encode}://$host:$port$path"
    }
  }

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

  def root: URL = URL(!!)

  private def fromAbsoluteURI(uri: URI): Option[URL] = {
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

  private def portFromScheme(scheme: Scheme): Int = scheme match {
    case Scheme.HTTP  => 80
    case Scheme.HTTPS => 443
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

  sealed trait Location

  case class Fragment private (raw: String, decoded: String)

  object Location {
    final case class Absolute(scheme: Scheme, host: String, port: Int) extends Location

    case object Relative extends Location
  }

  object Fragment {
    def fromURI(uri: URI): Option[Fragment] = for {
      raw     <- Option(uri.getRawFragment)
      decoded <- Option(uri.getFragment)
    } yield Fragment(raw, decoded)
  }
}
