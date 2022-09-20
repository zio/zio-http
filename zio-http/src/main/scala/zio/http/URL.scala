package zio.http

import io.netty.handler.codec.http.QueryStringEncoder
import zio.Chunk
import zio.http.URL.{Fragment, Location}
import zio.http.model.Scheme

import java.net.{MalformedURLException, URI}
import scala.util.Try
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

final case class URL(
  path: Path,
  kind: URL.Location = URL.Location.Relative,
  queryParams: QueryParams = QueryParams.empty,
  fragment: Option[Fragment] = None,
) { self =>

  def ++(that: URL): URL =
    URL(self.path ++ that.path, self.kind, self.queryParams ++ that.queryParams, self.fragment.orElse(that.fragment))

  def addTrailingSlash: URL = self.copy(path = path.addTrailingSlash)

  def dropTrailingSlash: URL = self.copy(path = path.dropTrailingSlash)

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

  private[zio] def normalize: URL = {
    val queryParamsMap =
      self.queryParams.toMap.toList.filter(i => i._1.nonEmpty && i._2.nonEmpty).sortBy(_._1).toMap
    self.copy(queryParams = QueryParams(queryParamsMap))
  }

  def port: Option[Int] = kind match {
    case URL.Location.Relative      => None
    case abs: URL.Location.Absolute => Option(abs.port)
  }

  private[zio] def relative: URL = self.kind match {
    case URL.Location.Relative => self
    case _                     => self.copy(kind = URL.Location.Relative)
  }

  def scheme: Option[Scheme] = kind match {
    case Location.Absolute(scheme, _, _) => Some(scheme)
    case Location.Relative               => None
  }

  def setHost(host: String): URL = {
    val location = kind match {
      case URL.Location.Relative      => URL.Location.Absolute(Scheme.HTTP, host, URL.portFromScheme(Scheme.HTTP))
      case abs: URL.Location.Absolute => abs.copy(host = host)
    }
    copy(kind = location)
  }

  def setPath(path: Path): URL =
    copy(path = path)

  def setPath(path: String): URL = copy(path = Path.decode(path))

  def setPort(port: Int): URL = {
    val location = kind match {
      case URL.Location.Relative      => URL.Location.Absolute(Scheme.HTTP, "", port)
      case abs: URL.Location.Absolute => abs.copy(port = port)
    }

    copy(kind = location)
  }

  def setQueryParams(queryParams: QueryParams): URL =
    copy(queryParams = queryParams)

  def setQueryParams(queryParams: Map[String, Chunk[String]]): URL =
    copy(queryParams = QueryParams(queryParams))

  def setQueryParams(queryParams: (String, Chunk[String])*): URL =
    copy(queryParams = QueryParams(queryParams.toMap))

  def setQueryParams(query: String): URL =
    copy(queryParams = QueryParams.decode(query))

  def setScheme(scheme: Scheme): URL = {
    val location = kind match {
      case URL.Location.Relative      => URL.Location.Absolute(scheme, "", URL.portFromScheme(scheme))
      case abs: URL.Location.Absolute => abs.copy(scheme = scheme)
    }

    copy(kind = location)
  }

  /**
   * Returns a new java.net.URI representing this URL.
   */
  def toJavaURI: java.net.URI = new URI(encode)

  /**
   * Returns a new java.net.URL only if this URL represents an absolute
   * location.
   */
  def toJavaURL: Option[java.net.URL] = if (self.kind == URL.Location.Relative) None else Try(toJavaURI.toURL).toOption

}

object URL {
  private def fromAbsoluteURI(uri: URI): Option[URL] = {
    for {
      scheme <- Scheme.decode(uri.getScheme)
      host   <- Option(uri.getHost)
      path   <- Option(uri.getRawPath)
      port       = Option(uri.getPort).filter(_ != -1).getOrElse(portFromScheme(scheme))
      connection = URL.Location.Absolute(scheme, host, port)
    } yield URL(Path.decode(path), connection, QueryParams.decode(uri.getRawQuery), Fragment.fromURI(uri))
  }

  private def fromRelativeURI(uri: URI): Option[URL] = for {
    path <- Option(uri.getRawPath)
  } yield URL(Path.decode(path), Location.Relative, QueryParams.decode(uri.getRawQuery), Fragment.fromURI(uri))

  private def portFromScheme(scheme: Scheme): Int = scheme match {
    case Scheme.HTTP | Scheme.WS   => 80
    case Scheme.HTTPS | Scheme.WSS => 443
  }

  def empty: URL = URL(!!)

  def encode(url: URL): String = {
    def path: String = {
      val encoder = new QueryStringEncoder(s"${url.path.encode}")
      url.queryParams.toMap.foreach { case (key, values) =>
        if (key != "") values.foreach { value => encoder.addParam(key, value) }
      }

      encoder.toString + url.fragment.fold("")(f => "#" + f.raw)
    }

    url.kind match {
      case Location.Relative                     => path
      case Location.Absolute(scheme, host, port) =>
        if (port == 80 || port == 443) s"${scheme.encode}://$host$path"
        else s"${scheme.encode}://$host:$port$path"
    }
  }

  def fromString(string: String): Either[Exception, URL] = {
    def invalidURL = Left(new MalformedURLException(s"""Invalid URL: "$string""""))
    try {
      val uri = new URI(string)
      val url = if (uri.isAbsolute()) fromAbsoluteURI(uri) else fromRelativeURI(uri)

      url match {
        case None        => invalidURL
        case Some(value) => Right(value)
      }

    } catch {
      case e: Exception => Left(e)
    }
  }

  def root: URL = URL(!!)

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
