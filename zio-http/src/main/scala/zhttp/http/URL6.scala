package zhttp.http

import io.netty.handler.codec.http.{QueryStringDecoder, QueryStringEncoder}
import zhttp.http.Scheme.HTTP
import zhttp.http.URL6._

import java.net.URI
import scala.jdk.CollectionConverters._
import scala.util.Try

sealed trait URL6 { self =>

  def getHost: Option[String] = self match {
    case a: Unsafe   => a.toAbsolute.getHost
    case b: Absolute => b.host
    case r: Relative => r.toAbsolute.getHost
  }
  def getPort: Option[Int]    = self match {
    case a: Unsafe   => a.toAbsolute.getPort
    case b: Absolute => b.port
    case r: Relative => r.toAbsolute.getPort
  }

  def toAbsolute: URL6 = self match {
    case Unsafe(x)   => URL6.unsafeDecode(x)
    case b: Absolute => b
    case c: Relative => Absolute(relative = c)
  }

  def setHost(host: String): URL6     = URL6.Absolute(Some(host), Some(HTTP), Some(80))
  def setPort(port: Int): URL6        = URL6.Absolute(Some("localhost"), Some(HTTP), Some(port))
  def setScheme(scheme: Scheme): URL6 = URL6.Absolute(Some("localhost"), Some(scheme), Some(80))
  def setPath(path: Path): URL6       = URL6.Relative(path)
  def setPath(path: String): URL6     = URL6.Relative(Path(path))
  def setQueryParams(queryParams: Map[String, List[String]]): URL6 = URL6.Relative(queryParams = queryParams)
  def setQueryParams(query: String): URL6 = URL6.Relative(queryParams = URL6.queryParams(query))
  def encode: String                      = URL6.asString(self)

}
object URL6 {

  def apply(path: Path): URL6     = Relative(path)
  def apply(string: String): URL6 = Unsafe(string)

  def asString(url: URL6): String = url match {
    case Unsafe(string) => string
    case u: Absolute    => {
      def path: String = {
        val encoder = new QueryStringEncoder(
          s"${u.relative.path.encode}${u.relative.fragment.fold("")(f => "#" + f.raw)}",
        )
        u.relative.queryParams.foreach { case (key, values) =>
          if (key != "") values.foreach { value => encoder.addParam(key, value) }
        }
        encoder.toString
      }
      val a            = URL6.Unsafe(s"$path")
      if (u.scheme.isDefined && u.port.isDefined && u.host.isDefined) {
        if (u.port.get == 80 || u.port.get == 443)
          a.copy(s"${u.scheme.get.encode}://${u.host}$path")
        else a.copy(s"${u.scheme.get.encode}://${u.host.get}:${u.port.get}$path")
      }
      a.encode
    }
    case r: Relative    => {
      def path: String = {
        val encoder = new QueryStringEncoder(s"${r.path.encode}${r.fragment.fold("")(f => "#" + f.raw)}")
        r.queryParams.foreach { case (key, values) =>
          if (key != "") values.foreach { value => encoder.addParam(key, value) }
        }
        encoder.toString
      }
      s"$path"
    }
  }

  def decode(string: String): Either[HttpError.BadRequest, URL6] = Try(unsafeDecode(string)).toEither match {
    case Left(_)      => Left(HttpError.BadRequest(s"Invalid URL: $string"))
    case Right(value) => Right(value)
  }

  final case class Unsafe(string: String) extends URL6
  final case class Absolute(
    host: Option[String] = None,
    scheme: Option[Scheme] = None,
    port: Option[Int] = None,
    relative: Relative = Relative(),
  ) extends URL6

  final case class Relative(
    path: Path = !!,
    queryParams: Map[String, List[String]] = Map.empty,
    fragment: Option[Fragment] = None,
  ) extends URL6

  def unsafeDecode(string: String): URL6            = {
    try {
      val url = new URI(string)
      if (url.isAbsolute) unsafeFromAbsoluteURI(url) else unsafeFromRelativeURI(url)
    } catch {
      case _: Throwable => null
    }
  }
  private def unsafeFromAbsoluteURI(uri: URI): URL6 = {

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
    else null
  }

  private def unsafeFromRelativeURI(uri: URI): URL6 =
    Relative(Path(uri.getRawPath), queryParams = queryParams(uri.getRawQuery), fragment = Fragment.fromURI(uri))

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

  def root: URL6  = Relative()
  def empty: URL6 = root

  val url1 = URL6(!! / "root")
    .setHost("www.zio-http.com")
    .setQueryParams(Map("A" -> List("B")))
    .setPort(8090)
    .setScheme(Scheme.HTTP)

  val url2: URL6                               = URL6("www.zio-http.com/a")
  val url3: Either[HttpError.BadRequest, URL6] = URL6.decode("www.zio-http.com/a")

}
