package zhttp.http

import io.netty.handler.codec.http.{QueryStringDecoder, QueryStringEncoder}

import java.net.URI
import scala.jdk.CollectionConverters._
import scala.util.Try

sealed trait URL2 { self =>

  def host: Option[String] = self match {
    case a @ URL2.FromString(_)   => a.toCons.get.host
    case URL2.Cons(_, kind, _, _) =>
      kind match {
        case URL2.Location.Relative      => None
        case abs: URL2.Location.Absolute => Option(abs.host)
      }
  }
  def port: Option[Int]    = self match {
    case a @ URL2.FromString(_)   => a.toCons.get.port
    case URL2.Cons(_, kind, _, _) =>
      kind match {
        case URL2.Location.Relative      => None
        case abs: URL2.Location.Absolute => Option(abs.port)
      }
  }

  private[zhttp] def relative: URL2 = self match {
    case a @ URL2.FromString(_)       => a.toCons.get.relative
    case b @ URL2.Cons(_, kind, _, _) =>
      kind match {
        case URL2.Location.Relative => b
        case _                      => b.copy(kind = URL2.Location.Relative)
      }
  }
  def toCons: Option[URL2]          = self match {
    case a @ URL2.FromString(_)    => a.fromString.toOption
    case b @ URL2.Cons(_, _, _, _) => Some(b)
  }

  def asString: String = self match {
    case URL2.FromString(string)   => string
    case u @ URL2.Cons(_, _, _, _) => {
      def path: String = {
        val encoder = new QueryStringEncoder(s"${u.path.encode}${u.fragment.fold("")(f => "#" + f.raw)}")
        u.queryParams.foreach { case (key, values) =>
          if (key != "") values.foreach { value => encoder.addParam(key, value) }
        }
        encoder.toString
      }

      u.kind match {
        case URL2.Location.Relative                     => path
        case URL2.Location.Absolute(scheme, host, port) =>
          if (port == 80 || port == 443) s"${scheme.encode}://$host$path"
          else s"${scheme.encode}://$host:$port$path"
      }
    }
  }

}
object URL2 {
  final case class FromString(string: String) extends URL2 { self =>
    def fromString: Either[HttpError.BadRequest, URL2] = {
      Try(unsafeFromString).toEither match {
        case Left(_)      => Left(HttpError.BadRequest(s"Invalid URL: $string"))
        case Right(value) => Right(value)
      }
    }
    def unsafeFromString: URL2                         = {
      try {
        val url = new URI(string)
        if (url.isAbsolute) fromAbsoluteURI(url).orNull else fromRelativeURI(url).orNull
      } catch { case _: Throwable => null }
    }

    private def unsafeFromAbsoluteURI2(uri: URI): URL2 = {

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
          URL2.Fragment.fromURI(uri),
        )
      else null
    }

    private def unsafeFromRelativeURI2(uri: URI): URL2 =
      URL2.Cons(Path(uri.getRawPath), URL2.Location.Relative, queryParams(uri.getRawQuery), URL2.Fragment.fromURI(uri))

    private def fromAbsoluteURI(uri: URI): Option[URL2] = {
      Try(unsafeFromAbsoluteURI2(uri)).toOption
    }

    private def fromRelativeURI(uri: URI): Option[URL2] = Try(unsafeFromRelativeURI2(uri)).toOption

  }
  final case class Cons(
    path: Path,
    kind: URL2.Location = URL2.Location.Relative,
    queryParams: Map[String, List[String]] = Map.empty,
    fragment: Option[Fragment] = None,
  ) extends URL2

  sealed trait Location
  object Location {
    case object Relative                                               extends Location
    final case class Absolute(scheme: Scheme, host: String, port: Int) extends Location
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
  def unsafeFromString(string: String): URL2                         = URL2.FromString(string)

  case class Fragment private (raw: String, decoded: String)
  object Fragment {
    def fromURI(uri: URI): Option[Fragment] = for {
      raw     <- Option(uri.getRawFragment)
      decoded <- Option(uri.getFragment)
    } yield Fragment(raw, decoded)
  }
  def root: URL2 = URL2.Cons(!!)

}
