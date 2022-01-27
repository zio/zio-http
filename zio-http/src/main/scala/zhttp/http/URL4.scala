package zhttp.http

import io.netty.handler.codec.http.{QueryStringDecoder, QueryStringEncoder}

import java.net.URI
import scala.jdk.CollectionConverters._
import scala.util.Try

sealed trait URL4 { self =>

  def getHost: Option[String] = self match {
    case a: URL4.Decode => a.toCons.get.getHost
    case b: URL4.Cons   => b.host
  }
  def getPort: Option[Int]    = self match {
    case a: URL4.Decode => a.toCons.get.getPort
    case b: URL4.Cons   => b.port
  }

  def toCons: Option[URL4] = self match {
    case a: URL4.Decode => a.apply.toOption
    case b: URL4.Cons   => Some(b)
  }

  def setPath(path: Path): URL4                                    = URL4.Cons(path = path)
  def setHost(host: String): URL4                                  = URL4.Cons(host = Some(host))
  def setPort(port: Int): URL4                                     = URL4.Cons(port = Some(port))
  def setScheme(scheme: Scheme): URL4                              = URL4.Cons(scheme = Some(scheme))
  def setQueryParams(queryParams: Map[String, List[String]]): URL4 = URL4.Cons(queryParams = queryParams)

  def encode: String = self match {
    case URL4.Decode(string) => string
    case u: URL4.Cons        => {
      def path: String = {
        val encoder = new QueryStringEncoder(s"${u.path.asString}${u.fragment.fold("")(f => "#" + f.raw)}")
        u.queryParams.foreach { case (key, values) =>
          if (key != "") values.foreach { value => encoder.addParam(key, value) }
        }
        encoder.toString
      }
      val a            = URL4.Decode(s"$path")
      if (u.scheme.isDefined && u.port.isDefined && u.host.isDefined) {
        if (u.port.get == 80 || u.port.get == 443)
          a.copy(s"${u.scheme.get.encode}://${u.host}$path")
        else a.copy(s"${u.scheme.get.encode}://${u.host.get}:${u.port.get}$path")
      }
      a.encode
    }
  }

}
object URL4 {

  def apply(path: Path): URL4 = URL4.Cons(path = path)
  final case class Decode(string: String) extends URL4 {
    self =>
    def apply: Either[HttpError.BadRequest, URL4] = {
      Try(unsafeDecode).toEither match {
        case Left(_)      => Left(HttpError.BadRequest(s"Invalid URL: $string"))
        case Right(value) => Right(value)
      }
    }
    def unsafeDecode: URL4                        = {
      try {
        val url = new URI(string)
        if (url.isAbsolute) unsafeFromAbsoluteURI2(url) else unsafeFromRelativeURI2(url)
      } catch { case _: Throwable => null }
    }

    private def unsafeFromAbsoluteURI2(uri: URI): URL4 = {

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
        URL4.Cons(
          Path(uri.getRawPath),
          Some(host),
          Some(scheme),
          Some(port),
          queryParams(uri.getRawQuery),
          URL4.Fragment.fromURI(uri),
        )
      else null
    }

    private def unsafeFromRelativeURI2(uri: URI): URL4 =
      URL4.Cons(Path(uri.getRawPath), queryParams = queryParams(uri.getRawQuery), fragment = URL4.Fragment.fromURI(uri))
  }
  final case class Cons(
    path: Path = !!,
    host: Option[String] = None,
    scheme: Option[Scheme] = None,
    port: Option[Int] = None,
    queryParams: Map[String, List[String]] = Map.empty,
    fragment: Option[Fragment] = None,
  ) extends URL4

  sealed trait Location
  object Location {
    case object Relative                                               extends Location
    final case class Absolute(scheme: Scheme, host: String, port: Int) extends Location
  }

  case class Fragment private (raw: String, decoded: String)
  object Fragment {
    def fromURI(uri: URI): Option[Fragment] = for {
      raw     <- Option(uri.getRawFragment)
      decoded <- Option(uri.getFragment)
    } yield Fragment(raw, decoded)
  }

  private def queryParams(query: String)                         = {
    if (query == null || query.isEmpty) {
      Map.empty[String, List[String]]
    } else {
      val decoder = new QueryStringDecoder(query, false)
      val params  = decoder.parameters()
      params.asScala.view.map { case (k, v) => (k, v.asScala.toList) }.toMap
    }
  }
  def decode(string: String): Either[HttpError.BadRequest, URL4] = URL4.Decode(string).apply
  def unsafeDecode(string: String): URL4                         = URL4.Decode(string)

  def root: URL4 = URL4.Cons(!!)

  val url = URL4(!! / "root")
    .setHost("www.zio-http.com")
    .setQueryParams(Map("A" -> List("B")))
    .setPort(8090)
    .setScheme(Scheme.HTTP)
}
