package zhttp.http

import java.net.URI

import io.netty.handler.codec.http.{QueryStringDecoder, QueryStringEncoder}
import zhttp.http.URL5._

import scala.jdk.CollectionConverters._
import scala.util.Try

sealed trait URL5 { self =>

  def getHost: Option[String] = self match {
    case a: Raw  => a.toCons.getHost
    case b: Cons => b.host
  }
  def getPort: Option[Int]    = self match {
    case a: Raw  => a.toCons.getPort
    case b: Cons => b.port
  }

  def toCons: URL5 = self match {
    case Raw(x)  => URL5.unsafeDecode(x)
    case b: Cons => b
  }

  def setPath(path: Path): URL5                                    = URL5.Cons(path = path)
  def setPath(path: String): URL5 = URL5.Cons(path = Path(path))
  def setHost(host: String): URL5                                  = URL5.Cons(host = Some(host))
  def setPort(port: Int): URL5                                     = URL5.Cons(port = Some(port))
  def setScheme(scheme: Scheme): URL5                              = URL5.Cons(scheme = Some(scheme))
  def setQueryParams(queryParams: Map[String, List[String]]): URL5 = URL5.Cons(queryParams = queryParams)
  def setQueryParams(query: String): URL5 = URL5.Cons(queryParams = URL5.queryParams(query))
  def encode: String                             = URL5.asString(self)

}
object URL5 {

  def apply(path: Path): URL5     = Cons(path)
  def apply(string: String): URL5 = Raw(string)

  def asString(url: URL5): String =  url match {
    case Raw(string) => string
    case u: Cons     => {
      def path: String = {
        val encoder = new QueryStringEncoder(s"${u.path.encode}${u.fragment.fold("")(f => "#" + f.raw)}")
        u.queryParams.foreach { case (key, values) =>
          if (key != "") values.foreach { value => encoder.addParam(key, value) }
        }
        encoder.toString
      }
      val a            = URL5.Raw(s"$path")
      if (u.scheme.isDefined && u.port.isDefined && u.host.isDefined) {
        if (u.port.get == 80 || u.port.get == 443)
          a.copy(s"${u.scheme.get.encode}://${u.host}$path")
        else a.copy(s"${u.scheme.get.encode}://${u.host.get}:${u.port.get}$path")
      }
      a.encode
    }
  }

  def decode(string: String): Either[HttpError.BadRequest, URL5] = Try(unsafeDecode(string)).toEither match {
    case Left(_)      => Left(HttpError.BadRequest(s"Invalid URL: $string"))
    case Right(value) => Right(value)
  }

  final case class Raw(string: String) extends URL5
  final case class Cons(
    path: Path = !!,
    host: Option[String] = None,
    scheme: Option[Scheme] = None,
    port: Option[Int] = None,
    queryParams: Map[String, List[String]] = Map.empty,
    fragment: Option[Fragment] = None,
  ) extends URL5

  def unsafeDecode(string: String): URL5            = {
    try {
      val url = new URI(string)
      if (url.isAbsolute) unsafeFromAbsoluteURI(url) else unsafeFromRelativeURI(url)
    } catch {
      case _: Throwable => null
    }
  }
  private def unsafeFromAbsoluteURI(uri: URI): URL5 = {

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

  private def unsafeFromRelativeURI(uri: URI): URL5 =
    Cons(Path(uri.getRawPath), queryParams = queryParams(uri.getRawQuery), fragment = Fragment.fromURI(uri))

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

  def root: URL5 = Cons(!!)
  def empty: URL5 = root

  val url1 = URL5(!! / "root")
    .setHost("www.zio-http.com")
    .setQueryParams(Map("A" -> List("B")))
    .setPort(8090)
    .setScheme(Scheme.HTTP)

  val url2: URL5                               = URL5("www.zio-http.com/a")
  val url3: Either[HttpError.BadRequest, URL5] = URL5.decode("www.zio-http.com/a")

}
