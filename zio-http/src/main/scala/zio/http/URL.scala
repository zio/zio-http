/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http

import java.net.{MalformedURLException, URI}

import scala.util.Try

import zio.Chunk

import zio.http.URL.{Fragment, Location, portFromScheme}
import zio.http.internal.QueryParamEncoding
import zio.http.{Charsets, Scheme}

final case class URL(
  path: Path,
  kind: URL.Location = URL.Location.Relative,
  queryParams: QueryParams = QueryParams.empty,
  fragment: Option[Fragment] = None,
) { self =>

  def ++(that: URL): URL =
    URL(
      self.path ++ that.path,
      self.kind ++ that.kind,
      self.queryParams ++ that.queryParams,
      self.fragment.orElse(that.fragment),
    )

  def absolute(host: String): URL =
    self.copy(kind = URL.Location.Absolute(Scheme.HTTP, host, URL.portFromScheme(Scheme.HTTP)))

  def absolute(scheme: Scheme, host: String, port: Int): URL =
    self.copy(kind = URL.Location.Absolute(scheme, host, port))

  def addTrailingSlash: URL = self.copy(path = path.addTrailingSlash)

  def dropTrailingSlash: URL = self.copy(path = path.dropTrailingSlash)

  def encode: String = URL.encode(self)

  def host: Option[String] = kind match {
    case URL.Location.Relative      => None
    case abs: URL.Location.Absolute => Option(abs.host)
  }

  def hostPort: Option[String] =
    kind match {
      case URL.Location.Relative                     => None
      case URL.Location.Absolute(scheme, host, port) =>
        Some(
          if (port == portFromScheme(scheme)) host
          else s"$host:$port",
        )
    }

  def isAbsolute: Boolean = self.kind match {
    case Location.Absolute(_, _, _) => true
    case Location.Relative          => false
  }

  def isRelative: Boolean = !isAbsolute

  def normalize: URL = self.copy(queryParams = queryParams.normalize)

  def port: Option[Int] = kind match {
    case URL.Location.Relative      => None
    case abs: URL.Location.Absolute => Option(abs.port)
  }

  def portIfNotDefault: Option[Int] = kind match {
    case URL.Location.Relative      =>
      None
    case abs: URL.Location.Absolute =>
      if (abs.port == portFromScheme(abs.scheme)) None else Some(abs.port)
  }

  def relative: URL = self.kind match {
    case URL.Location.Relative => self
    case _                     => self.copy(kind = URL.Location.Relative)
  }

  def scheme: Option[Scheme] = kind match {
    case Location.Absolute(scheme, _, _) => Some(scheme)
    case Location.Relative               => None
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

  def withHost(host: String): URL = {
    val location = kind match {
      case URL.Location.Relative      => URL.Location.Absolute(Scheme.HTTP, host, URL.portFromScheme(Scheme.HTTP))
      case abs: URL.Location.Absolute => abs.copy(host = host)
    }
    copy(kind = location)
  }

  def withPath(path: Path): URL =
    copy(path = path)

  def withPath(path: String): URL = copy(path = Path.decode(path))

  def withPort(port: Int): URL = {
    val location = kind match {
      case URL.Location.Relative      => URL.Location.Absolute(Scheme.HTTP, "", port)
      case abs: URL.Location.Absolute => abs.copy(port = port)
    }

    copy(kind = location)
  }

  def withQueryParams(queryParams: QueryParams): URL =
    copy(queryParams = queryParams)

  def withQueryParams(queryParams: Map[String, Chunk[String]]): URL =
    copy(queryParams = QueryParams(queryParams))

  def withQueryParams(queryParams: (String, Chunk[String])*): URL =
    copy(queryParams = QueryParams(queryParams.toMap))

  def withQueryParams(query: String): URL =
    copy(queryParams = QueryParams.decode(query))

  def withScheme(scheme: Scheme): URL = {
    val location = kind match {
      case URL.Location.Relative      => URL.Location.Absolute(scheme, "", URL.portFromScheme(scheme))
      case abs: URL.Location.Absolute => abs.copy(scheme = scheme)
    }

    copy(kind = location)
  }
}

object URL {
  def empty: URL = URL(Path.empty)

  def decode(string: String): Either[Exception, URL] = {
    def invalidURL = Left(new MalformedURLException(s"""Invalid URL: "$string""""))
    try {
      val uri = new URI(string)
      val url = if (uri.isAbsolute) fromAbsoluteURI(uri) else fromRelativeURI(uri)

      url match {
        case None        => invalidURL
        case Some(value) => Right(value)
      }

    } catch {
      case e: Exception => Left(e)
    }
  }

  def fromURI(uri: URI): Option[URL] = if (uri.isAbsolute) fromAbsoluteURI(uri) else fromRelativeURI(uri)

  def root: URL = URL(!!)

  sealed trait Location { self =>
    def ++(that: Location): Location =
      if (that.isRelative) self
      else that

    def isAbsolute: Boolean = !isRelative

    def isRelative: Boolean = self match { case Location.Relative => true; case _ => false }
  }

  object Location {
    final case class Absolute(scheme: Scheme, host: String, port: Int) extends Location

    case object Relative extends Location
  }

  final case class Fragment private (raw: String, decoded: String)

  object Fragment {
    def fromURI(uri: URI): Option[Fragment] = for {
      raw     <- Option(uri.getRawFragment)
      decoded <- Option(uri.getFragment)
    } yield Fragment(raw, decoded)
  }

  private def encode(url: URL): String = {
    def path: String =
      QueryParamEncoding.default.encode(
        url.path.encode,
        url.queryParams.normalize,
        Charsets.Http,
      ) + url.fragment.fold("")(f => "#" + f.raw)

    url.kind match {
      case Location.Relative                     =>
        path
      case Location.Absolute(scheme, host, port) =>
        if (port == portFromScheme(scheme)) s"${scheme.encode}://$host$path"
        else s"${scheme.encode}://$host:$port$path"
    }
  }

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

}
