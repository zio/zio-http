package zhttp.http

import io.netty.handler.codec.http.{QueryStringDecoder, QueryStringEncoder}
import zhttp.http.Scheme.HTTP
import zhttp.http.URL._

import java.net.URI
import scala.jdk.CollectionConverters._
import scala.util.Try

sealed trait URL { self =>

  def getHost: Option[String]   = self match {
    case b: Absolute => b.host
    case r           => r.toAbsolute.getHost
  }
  def getPort: Option[Int]      = self match {
    case b: Absolute => b.port
    case r           => r.toAbsolute.getPort
  }
  def getScheme: Option[Scheme] = self match {
    case b: Absolute => b.scheme
    case r           => r.toAbsolute.getScheme
  }

  def toAbsolute: Absolute = self match {
    case Unsafe(x)   => URL.unsafeFromString(x)
    case b: Absolute => b
    case c: Relative => Absolute(relative = c)
  }

  def setHost(host: String): URL     =
    self.toAbsolute.copy(Some(host), Some(self.getScheme.getOrElse(HTTP)), Some(self.getPort.getOrElse(80)))
  def setPort(port: Int): URL        =
    self.toAbsolute.copy(Some(self.getHost.getOrElse("localhost")), Some(self.getScheme.getOrElse(HTTP)), Some(port))
  def setScheme(scheme: Scheme): URL =
    self.toAbsolute.copy(Some(self.getHost.getOrElse("localhost")), Some(scheme), Some(self.getPort.getOrElse(80)))
  def setPath(path: Path): URL       = self.toAbsolute.copy(relative = self.toAbsolute.relative.copy(path = path))
  def setPath(path: String): URL     = self.toAbsolute.copy(relative = self.toAbsolute.relative.copy(path = Path(path)))
  def setQueryParams(queryParams: Map[String, List[String]]): URL =
    self.toAbsolute.copy(relative = self.toAbsolute.relative.copy(queryParams = queryParams))
  def setQueryParams(query: String): URL                          =
    self.toAbsolute.copy(relative = self.toAbsolute.relative.copy(queryParams = URL.queryParams(query)))
  def encode: String                                              = URL.asString(self)
}
object URL {

  def apply(path: Path): URL     = Relative(path)
  def apply(string: String): URL = Unsafe(string)

  def asString(url: URL): String        = {
    val p: String = path(url.toAbsolute.relative)
    url match {
      case u: Absolute =>
        if (u.scheme.isDefined && u.port.isDefined && u.host.isDefined) {
          if (u.port.get == 80 || u.port.get == 443)
            s"${u.scheme.get.encode}://${u.host.get}$p"
          else s"${u.scheme.get.encode}://${u.host.get}:${u.port.get}$p"
        } else p
      case _           => {
        p
      }
    }
  }
  private def path(r: Relative): String = {
    val encoder = new QueryStringEncoder(s"${r.path.encode}${r.fragment.fold("")(f => "#" + f.raw)}")
    r.queryParams.foreach { case (key, values) =>
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
    host: Option[String] = None,
    scheme: Option[Scheme] = None,
    port: Option[Int] = None,
    relative: Relative = Relative(),
  ) extends URL

  final case class Relative(
    path: Path = !!,
    queryParams: Map[String, List[String]] = Map.empty,
    fragment: Option[Fragment] = None,
  ) extends URL

  def unsafeFromString(string: String): Absolute =
    try {
      val url = new URI(string)
      if (url.isAbsolute) unsafeFromAbsoluteURI(url) else unsafeFromRelativeURI(url)
    } catch {
      case _: Throwable => throw new Error("Invalid URL")
    }

  private def unsafeFromAbsoluteURI(uri: URI): Absolute = {

    def portFromScheme(scheme: Scheme): Int = scheme match {
      case Scheme.HTTP  => 80
      case Scheme.HTTPS => 443
      case null         => -1
    }

    val scheme  = Scheme.fromString2(uri.getScheme)
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
    else throw new Error()
  }

  private def unsafeFromRelativeURI(uri: URI): Absolute =
    Absolute(relative = Relative(Path(uri.getRawPath), queryParams(uri.getRawQuery), Fragment.fromURI(uri)))

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

  def root: URL  = Absolute(relative = Relative())
  def empty: URL = root

  val url1 = URL.empty
    .setHost("www.zio-http.com")
    .setQueryParams(Map("A" -> List("B")))
    .setPort(8090)
    .setScheme(Scheme.HTTP)

  val url2: URL                               = URL("www.zio-http.com/a")
  val url3: Either[HttpError.BadRequest, URL] = URL.fromString("www.zio-http.com/a")

}
