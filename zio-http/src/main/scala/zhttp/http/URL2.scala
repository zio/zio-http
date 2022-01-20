package zhttp.http

import io.netty.handler.codec.http.{QueryStringDecoder, QueryStringEncoder}

import java.net.URI
import scala.jdk.CollectionConverters._
import scala.util.Try

sealed trait URL2 { self =>
  def asString: String = URL2.asString(self)
}
object URL2       {
  final case class FromString(string: String) extends URL2 { self =>
    def fromString: Either[HttpError.BadRequest, URL2] = {
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
    def fromString2: URL2                              = {
      try {
        val url = new URI(string)
        if (url.isAbsolute) fromAbsoluteURI2(url) else fromRelativeURI2(url)
      } catch { case _: Throwable => null }
    }
    def fromString3: URL2                              = {
      try {
        val url = new URI(string)
        if (url.isAbsolute) fromAbsoluteURI(url).orNull else fromRelativeURI(url).orNull
      } catch { case _: Throwable => null }
    }

    private def fromAbsoluteURI2(uri: URI): URL2 = {

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
        URL2.Cons(
          Path(uri.getRawPath),
          URL2.Location.Absolute(scheme, host, port),
          queryParams(uri.getRawQuery),
          Fragment.fromURI(uri),
        )
      else null
    }

    private def fromRelativeURI2(uri: URI): URL2 =
      URL2.Cons(Path(uri.getRawPath), Location.Relative, queryParams(uri.getRawQuery), Fragment.fromURI(uri))

    private def fromAbsoluteURI(uri: URI): Option[URL2] = {

      def portFromScheme(scheme: Scheme): Int = scheme match {
        case Scheme.HTTP  => 80
        case Scheme.HTTPS => 443
      }

      for {
        scheme <- Scheme.fromString(uri.getScheme)
        host   <- Option(uri.getHost)
        path   <- Option(uri.getRawPath)
        port       = Option(uri.getPort).filter(_ != -1).getOrElse(portFromScheme(scheme))
        connection = URL2.Location.Absolute(scheme, host, port)
      } yield URL2.Cons(Path(path), connection, queryParams(uri.getRawQuery), Fragment.fromURI(uri))
    }

    private def fromRelativeURI(uri: URI): Option[URL2] = for {
      path <- Option(uri.getRawPath)
    } yield URL2.Cons(Path(path), Location.Relative, queryParams(uri.getRawQuery), Fragment.fromURI(uri))

  }
  final case class Cons(
    path: Path,
    kind: URL2.Location = URL2.Location.Relative,
    queryParams: Map[String, List[String]] = Map.empty,
    fragment: Option[Fragment] = None,
  ) extends URL2 {
    self =>

    val host: Option[String] = kind match {
      case URL2.Location.Relative      => None
      case abs: URL2.Location.Absolute => Option(abs.host)
    }
    val port: Option[Int]    = kind match {
      case URL2.Location.Relative      => None
      case abs: URL2.Location.Absolute => Option(abs.port)
    }

    private[zhttp] def relative: URL2 = self.kind match {
      case URL2.Location.Relative => self
      case _                      => self.copy(kind = URL2.Location.Relative)
    }
  }

  sealed trait Location
  object Location {
    case object Relative                                               extends Location
    final case class Absolute(scheme: Scheme, host: String, port: Int) extends Location
  }

  def asString(url: URL2): String = {
    url match {
      case FromString(string)   => string
      case u @ Cons(_, _, _, _) => {
        def path: String = {
          val encoder = new QueryStringEncoder(s"${u.path.encode}${u.fragment.fold("")(f => "#" + f.raw)}")
          u.queryParams.foreach { case (key, values) =>
            if (key != "") values.foreach { value => encoder.addParam(key, value) }
          }
          encoder.toString
        }

        u.kind match {
          case Location.Relative                     => path
          case Location.Absolute(scheme, host, port) =>
            if (port == 80 || port == 443) s"${scheme.encode}://$host$path"
            else s"${scheme.encode}://$host:$port$path"
        }
      }
    }
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

  def fromString(string: String): Either[HttpError.BadRequest, URL2] = URL2.FromString(string).fromString
  def fromString2(string: String): URL2                              = URL2.FromString(string).fromString2
  def fromString3(string: String): URL2                              = URL2.FromString(string).fromString3

  case class Fragment private (raw: String, decoded: String)
  object Fragment {
    def fromURI(uri: URI): Option[Fragment] = for {
      raw     <- Option(uri.getRawFragment)
      decoded <- Option(uri.getFragment)
    } yield Fragment(raw, decoded)
  }
  def root: URL2 = URL2.Cons(!!)

}
