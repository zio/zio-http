package zhttp.http

import io.netty.handler.codec.http.{QueryStringDecoder, QueryStringEncoder}
import zhttp.http.Scheme.HTTP
import zhttp.http.URL._

import java.net.URI
import scala.jdk.CollectionConverters._
import scala.util.Try

sealed trait URL { self =>

  def host: Option[String]   = Option(self.toAbsolute.urlHost)
  def port: Int              = self.toAbsolute.urlPort
  def scheme: Option[Scheme] = Option(self.toAbsolute.urlScheme)
  def relative: Relative     = self.toAbsolute.urlRelative
  def path: Path             = relative.urlPath

  def setHost(host: String): URL =
    self.toAbsolute.copy(host, self.scheme.getOrElse(HTTP), self.port)

  def setPort(port: Int): URL =
    self.toAbsolute.copy(self.host.getOrElse("localhost"), self.scheme.getOrElse(HTTP), port)

  def setScheme(scheme: Scheme): URL =
    self.toAbsolute.copy(self.host.getOrElse("localhost"), scheme, self.port)

  def setPath(path: Path): URL = self.toAbsolute.copy(urlRelative = self.relative.copy(urlPath = path))

  def setPath(path: String): URL = self.toAbsolute.copy(urlRelative = self.relative.copy(urlPath = Path(path)))

  def setQueryParams(queryParams: Map[String, List[String]]): URL =
    self.toAbsolute.copy(urlRelative = self.relative.copy(urlQueryParams = queryParams))

  def setQueryParams(query: String): URL =
    self.toAbsolute.copy(urlRelative = self.relative.copy(urlQueryParams = URL.queryParams(query)))

  def toAbsolute: Absolute = self match {
    case Unsafe(x)   => URL.unsafeFromString(x)
    case b: Absolute => b
    case c: Relative => Absolute(null, null, urlRelative = c)
  }

  def encode: String = URL.asString(self)
}
object URL {

  def apply(path: Path): URL     = Relative(path)
  def apply(string: String): URL = Unsafe(string)

  def asString(url: URL): String = {
    val p: String = path(url.relative)
    val absUrl    = url.toAbsolute
    if (absUrl.urlScheme != null && absUrl.urlHost != null) {
      if (absUrl.urlPort == 80 || absUrl.urlPort == 443)
        s"${absUrl.urlScheme.encode}://${absUrl.urlHost}$p"
      else s"${absUrl.urlScheme.encode}://${absUrl.urlHost}:${absUrl.urlPort}$p"
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
    urlHost: String,
    urlScheme: Scheme,
    urlPort: Int = 80,
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
        host,
        scheme,
        port,
        Relative(Path(uri.getRawPath), queryParams(uri.getRawQuery), Fragment.fromURI(uri)),
      )
    else null
  }

  private def unsafeFromRelativeURI(uri: URI): Absolute =
    Absolute(
      null,
      null,
      urlRelative = Relative(
        Path(uri.getRawPath),
        urlQueryParams = queryParams(uri.getRawQuery),
        urlFragment = Fragment.fromURI(uri),
      ),
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

  def root: URL  = Absolute(null, null, urlRelative = Relative())
  def empty: URL = root

}
