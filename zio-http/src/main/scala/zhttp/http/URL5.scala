package zhttp.http

import io.netty.handler.codec.http.{QueryStringDecoder, QueryStringEncoder}
import zhttp.http.Scheme.asString
import zhttp.http.URL5._

import java.net.URI
import scala.jdk.CollectionConverters._
import scala.util.Try

sealed trait URL5 { self =>

  def getHost: Option[String] = self match {
    case a: Decode => a.toCons.get.getHost
    case b: Cons   => b.host
  }
  def getPort: Option[Int]    = self match {
    case a: Decode => a.toCons.get.getPort
    case b: Cons   => b.port
  }

  def toCons: Option[URL5] = self match {
    case a: Decode => a.decode.toOption
    case b: Cons   => Some(b)
  }

  def setPath(path: Path): URL5                                    = URL5.Cons(path = path)
  def setHost(host: String): URL5                                  = URL5.Cons(host = Some(host))
  def setPort(port: Int): URL5                                     = URL5.Cons(port = Some(port))
  def setScheme(scheme: Scheme): URL5                              = URL5.Cons(scheme = Some(scheme))
  def setQueryParams(queryParams: Map[String, List[String]]): URL5 = URL5.Cons(queryParams = queryParams)

  def encode: String = self match {
    case Decode(string) => string
    case u: Cons        => {
      def path: String = {
        val encoder = new QueryStringEncoder(s"${u.path.asString}${u.fragment.fold("")(f => "#" + f.raw)}")
        u.queryParams.foreach { case (key, values) =>
          if (key != "") values.foreach { value => encoder.addParam(key, value) }
        }
        encoder.toString
      }
      val a            = URL5.Decode(s"$path")
      if (u.scheme.isDefined && u.port.isDefined && u.host.isDefined) {
        if (u.port.get == 80 || u.port.get == 443)
          a.copy(s"${asString(u.scheme.get)}://${u.host}$path")
        else a.copy(s"${asString(u.scheme.get)}://${u.host.get}:${u.port.get}$path")
      }
      a.encode
    }
  }

}
object URL5 {

  def apply(path: Path): URL5                                = Cons(path = path)
  def apply(str: String): Either[HttpError.BadRequest, URL5] = Decode(string = str).decode

  final case class Decode(string: String) extends URL5 {
    self =>
    def decode: Either[HttpError.BadRequest, URL5] = {
      Try(unsafeDecode).toEither match {
        case Left(_)      => Left(HttpError.BadRequest(s"Invalid URL: $string"))
        case Right(value) => Right(value)
      }
    }
    def unsafeDecode: URL5                         = {
      try {
        val url = new URI(string)
        if (url.isAbsolute) unsafeFromAbsoluteURI2(url) else unsafeFromRelativeURI2(url)
      } catch { case _: Throwable => null }
    }

    private def unsafeFromAbsoluteURI2(uri: URI): URL5 = {

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
        Cons(
          Path(uri.getRawPath),
          Some(host),
          Some(scheme),
          Some(port),
          queryParams(uri.getRawQuery),
          Fragment.fromURI(uri),
        )
      else null
    }

    private def unsafeFromRelativeURI2(uri: URI): URL5 =
      Cons(Path(uri.getRawPath), queryParams = queryParams(uri.getRawQuery), fragment = Fragment.fromURI(uri))
  }
  final case class Cons(
    path: Path = !!,
    host: Option[String] = None,
    scheme: Option[Scheme] = None,
    port: Option[Int] = None,
    queryParams: Map[String, List[String]] = Map.empty,
    fragment: Option[Fragment] = None,
  ) extends URL5

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

  private def queryParams(query: String) = {
    if (query == null || query.isEmpty) {
      Map.empty[String, List[String]]
    } else {
      val decoder = new QueryStringDecoder(query, false)
      val params  = decoder.parameters()
      params.asScala.view.map { case (k, v) => (k, v.asScala.toList) }.toMap
    }
  }

  def unsafeDecode(string: String): URL5 = Decode(string)

  def root: URL5 = Cons(!!)

  val url1 = URL5(!! / "root")
    .setHost("www.zio-http.com")
    .setQueryParams(Map("A" -> List("B")))
    .setPort(8090)
    .setScheme(Scheme.HTTP)

  val url2: Option[URL5] = URL5("www.zio-http.com/a").toOption

}
