package zhttp.http

import io.netty.handler.codec.http.{QueryStringDecoder, QueryStringEncoder}
import zhttp.http.Scheme.HTTP
import zhttp.http.URL._

import java.net.URI
import scala.jdk.CollectionConverters._
import scala.util.Try

sealed trait URL { self =>

  def host: Option[String]   = self.toAbsolute.urlHost
  def port: Option[Int]      = self.toAbsolute.urlPort
  def scheme: Option[Scheme] = self.toAbsolute.urlScheme
  def relative: Relative     = self.toAbsolute.urlRelative
  def path: Path             = relative.urlPath

  def setHost(host: String): URL =
    self.toAbsolute.copy(Some(host), Some(self.scheme.getOrElse(HTTP)), Some(self.port.getOrElse(80)))

  def setPort(port: Int): URL =
    self.toAbsolute.copy(Some(self.host.getOrElse("localhost")), Some(self.scheme.getOrElse(HTTP)), Some(port))

  def setScheme(scheme: Scheme): URL =
    self.toAbsolute.copy(Some(self.host.getOrElse("localhost")), Some(scheme), Some(self.port.getOrElse(80)))

  def setPath(path: Path): URL = self.toAbsolute.copy(urlRelative = self.relative.copy(urlPath = path))

  def setPath(path: String): URL = self.toAbsolute.copy(urlRelative = self.relative.copy(urlPath = Path(path)))

  def setQueryParams(queryParams: Map[String, List[String]]): URL =
    self.toAbsolute.copy(urlRelative = self.relative.copy(urlQueryParams = queryParams))

  def setQueryParams(query: String): URL =
    self.toAbsolute.copy(urlRelative = self.relative.copy(urlQueryParams = URL.queryParams(query)))

  def toAbsolute: Absolute = self match {
    case Unsafe(x)   => URL.unsafeFromString(x)
    case b: Absolute => b
    case c: Relative => Absolute(urlRelative = c)
  }

  def encode: String = URL.asString(self)
}
object URL {

  def apply(path: Path): URL     = Relative(path)
  def apply(string: String): URL = Unsafe(string)

  def asString(url: URL): String = {
    val p: String = path(url.relative)
    val absUrl    = url.toAbsolute
    if (absUrl.urlScheme.isDefined && absUrl.urlPort.isDefined && absUrl.urlHost.isDefined) {
      if (absUrl.urlPort.get == 80 || absUrl.urlPort.get == 443)
        s"${absUrl.urlScheme.get.encode}://${absUrl.urlHost.get}$p"
      else s"${absUrl.urlScheme.get.encode}://${absUrl.urlHost.get}:${absUrl.urlPort.get}$p"
    } else p

  }
  private def path(r: Relative): String = {
    val encoder = new QueryStringEncoder(s"${r.urlPath.encode}${r.urlFragment.fold("")(f => "#" + f.raw)}")
    r.urlQueryParams.foreach { case (key, values) =>
      if (key != "") values.foreach { value => encoder.addParam(key, value) }
    }
    encoder.toString
  }

  def fromString(string: String): Either[HttpError.BadRequest, URL] = Try(unsafeFromString(string)).toEither match {
    case Right(value) if value != null => Right(value)
    case _                             => Left(HttpError.BadRequest(s"Invalid URL: $string"))
  }

  final case class Unsafe(string: String) extends URL
  final case class Absolute(
    urlHost: Option[String] = None,
    urlScheme: Option[Scheme] = None,
    urlPort: Option[Int] = None,
    urlRelative: Relative = Relative(),
  ) extends URL

  final case class Relative(
    urlPath: Path = !!,
    urlQueryParams: Map[String, List[String]] = Map.empty,
    urlFragment: Option[Fragment] = None,
  ) extends URL

  private def portFromScheme(scheme: Scheme): Int = scheme match {
    case Scheme.HTTP | Scheme.WS   => 80
    case Scheme.HTTPS | Scheme.WSS => 443
  }

  def unsafeFromString(string: String): Absolute        = {
    try {
      val url = new URI(string)
      if (url.isAbsolute) unsafeFromAbsoluteURI(url) else unsafeFromRelativeURI(url)
    } catch {
      case _: Throwable => null
    }
  }
  private def unsafeFromAbsoluteURI(uri: URI): Absolute = {
    val scheme  = Scheme.unsafeDecode(uri.getScheme)
    val uriPort = uri.getPort
    val port    = if (uriPort != -1) uriPort else portFromScheme(scheme)
    val host    = uri.getHost

    if (port != -1 && scheme != null && host != null)
      Absolute(
        Some(host),
        Some(scheme),
        Some(port),
        Relative(Path(uri.getRawPath), queryParams(uri.getRawQuery), Fragment.fromURI(uri)),
      )
    else null
  }

  private def unsafeFromRelativeURI(uri: URI): Absolute =
    Absolute(urlRelative =
      Relative(Path(uri.getRawPath), urlQueryParams = queryParams(uri.getRawQuery), urlFragment = Fragment.fromURI(uri)),
    )

  case class Fragment private (raw: String, decoded: String)
  object Fragment {
    def fromURI(uri: URI): Option[Fragment] = for {
      raw     <- Option(uri.getRawFragment)
      decoded <- Option(uri.getFragment)
    } yield Fragment(raw, decoded)
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

  def root: URL  = Absolute(urlRelative = Relative())
  def empty: URL = root

}
