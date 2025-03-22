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

import scala.util.control.NonFatal

import zio.{Config, ZIO, durationInt}

import zio.http.URL.{Fragment, Location}
import zio.http.internal._

final case class URL(
  path: Path,
  kind: URL.Location = URL.Location.Relative,
  queryParams: QueryParams = QueryParams.empty,
  fragment: Option[Fragment] = None,
) extends URLPlatformSpecific
    with QueryOps[URL] { self =>

  /**
   * A right-biased way of combining two URLs. Where it makes sense, information
   * will be merged, but in cases where this does not make sense (e.g. two
   * non-empty fragments), the information from the right URL will be used.
   */
  def ++(that: URL): URL =
    URL(
      self.path ++ that.path,
      self.kind ++ that.kind,
      self.queryParams ++ that.queryParams,
      that.fragment.orElse(this.fragment),
    )

  def /(segment: String): URL = self.copy(path = self.path / segment)

  def absolute(host: String): URL =
    self.copy(kind = URL.Location.Absolute(Scheme.HTTP, host, None))

  def absolute(scheme: Scheme, host: String, port: Int): URL =
    self.copy(kind = URL.Location.Absolute(scheme, host, Some(port)))

  def addLeadingSlash: URL = self.copy(path = path.addLeadingSlash)

  def addPath(path: Path): URL = self.copy(path = self.path ++ path)

  def addPath(path: String): URL = self.copy(path = self.path ++ Path.decode(path))

  def addTrailingSlash: URL = self.copy(path = path.addTrailingSlash)

  def addQueryParams(queryParams: QueryParams): URL =
    copy(queryParams = self.queryParams ++ queryParams)

  def dropLeadingSlash: URL = self.copy(path = path.dropLeadingSlash)

  def dropTrailingSlash: URL = self.copy(path = path.dropTrailingSlash)

  def encode: String = URL.encode(self)

  override def equals(that: Any): Boolean = that match {
    case that: URL =>
      val left  = self.normalize
      val right = that.normalize

      left.kind == right.kind &&
      left.path == right.path &&
      left.queryParams == right.queryParams &&
      left.fragment == right.fragment

    case _ => false
  }

  override def hashCode(): Int = {
    val normalized = self.normalize

    var hash = 17
    hash = hash * 31 + normalized.kind.hashCode
    hash = hash * 31 + normalized.path.hashCode
    hash = hash * 31 + normalized.queryParams.hashCode
    hash = hash * 31 + normalized.fragment.hashCode
    hash
  }

  override def toString: String = encode

  def host: Option[String] = kind match {
    case URL.Location.Relative      => None
    case abs: URL.Location.Absolute => Option(abs.host)
  }

  def host(host: String): URL = {
    val location = kind match {
      case URL.Location.Relative      => URL.Location.Absolute(Scheme.HTTP, host, None)
      case abs: URL.Location.Absolute => abs.copy(host = host)
    }
    copy(kind = location)
  }

  /**
   * @return
   *   the location, the host name and the port. The port part is omitted if is
   *   the default port for the protocol.
   */
  def hostPort: Option[String] =
    kind match {
      case URL.Location.Relative      => None
      case abs: URL.Location.Absolute =>
        abs.portIfNotDefault match {
          case None             => Some(abs.host)
          case Some(customPort) => Some(s"${abs.host}:$customPort")
        }
    }

  def isAbsolute: Boolean = self.kind match {
    case Location.Absolute(_, _, _) => true
    case Location.Relative          => false
  }

  def isRelative: Boolean = !isAbsolute

  def normalize: URL = {
    def normalizePath(path: Path): Path =
      if (path.isEmpty || path.isRoot) Path.empty
      else path.addLeadingSlash

    self.copy(path = normalizePath(path), queryParams = queryParams.normalize)
  }

  def path(path: Path): URL =
    copy(path = path)

  def path(path: String): URL =
    copy(path = Path.decode(path))

  def port(port: Int): URL = {
    val location = kind match {
      case URL.Location.Relative      => URL.Location.Absolute(Scheme.HTTP, "", Some(port))
      case abs: URL.Location.Absolute => abs.copy(originalPort = Some(port))
    }

    copy(kind = location)
  }

  def port: Option[Int] = kind match {
    case URL.Location.Relative      => None
    case abs: URL.Location.Absolute => abs.originalPort
  }

  def portOrDefault: Option[Int] = kind match {
    case URL.Location.Relative      => None
    case abs: URL.Location.Absolute => abs.portOrDefault
  }

  def portIfNotDefault: Option[Int] = kind match {
    case URL.Location.Relative      => None
    case abs: URL.Location.Absolute => abs.portIfNotDefault
  }

  override def queryParameters: QueryParams =
    queryParams

  def relative: URL = self.kind match {
    case URL.Location.Relative => self
    case _                     => self.copy(kind = URL.Location.Relative)
  }

  /**
   * RFC 3986 § 5.2 Relative Resolution
   * @param reference
   *   the URL to resolve relative to ``this`` base URL
   * @return
   *   the target URL
   */
  def resolve(reference: URL): Either[String, URL] = {
    // See https://www.rfc-editor.org/rfc/rfc3986#section-5.2
    // § 5.2.1 - `self` is the base and already pre-parsed into components
    // § 5.2.2 - strict parsing does not ignore the reference URL scheme, so we use it directly, instead of un-setting it

    if (reference.kind.isRelative) {
      // § 5.2.2 - reference scheme is undefined, i.e. it is relative
      self.kind match {
        // § 5.2.1 - `self` is the base and is required to have a scheme, therefore it must be absolute
        case Location.Relative => Left("cannot resolve against relative url")

        case location: Location.Absolute =>
          var path: Path         = null
          var query: QueryParams = null

          if (reference.path.isEmpty) {
            // § 5.2.2 - empty reference path keeps base path unmodified
            path = self.path
            // § 5.2.2 - given an empty reference path, use non-empty reference query params,
            //           while empty reference query params keeps base query params
            // NOTE: strictly, if the reference defines a query it should be used, even if that query is empty
            //       but currently no-query is not differentiated from empty-query
            if (reference.queryParams.isEmpty) {
              query = self.queryParams
            } else {
              query = reference.queryParams
            }
          } else {
            // § 5.2.2 - non-empty reference path always keeps reference query params
            query = reference.queryParams

            if (reference.path.hasLeadingSlash) {
              // § 5.2.2 - reference path starts from root, keep reference path without dot segments
              path = reference.path.removeDotSegments
            } else {
              // § 5.2.2 - merge base and reference paths, then collapse dot segments
              // § 5.2.3 - if base has an authority AND an empty path, use the reference path, ensuring a leading slash
              //           the authority is the [user]@host[:port], which is always present on `self`,
              //           so we only need to check for an empty path
              if (self.path.isEmpty) {
                path = reference.path.addLeadingSlash
              } else {
                // § 5.2.3 - otherwise (base has no authority OR a non-empty path), drop the very last portion of the base path,
                //           and append all the reference path components
                path = Path(
                  Path.Flags.concat(self.path.flags, reference.path.flags),
                  self.path.segments.dropRight(1) ++ reference.path.segments,
                )
              }

              path = path.removeDotSegments
            }
          }

          val url = URL(path, location, query, reference.fragment)

          Right(url)

      }
    } else {
      // § 5.2.2 - if the reference scheme is defined, i.e. the reference is absolute,
      //           the target components are the reference components but with dot segments removed

      // § 5.2.2 - if the reference scheme is undefined and authority is defined, keep the base scheme
      //           and take everything else from the reference, removing dot segments from the path
      // NOTE: URL currently does not track authority separate from scheme to implement this
      //       so having an authority is the same as having a scheme and they are treated the same
      Right(reference.copy(path = reference.path.removeDotSegments))
    }
  }

  def scheme: Option[Scheme] = kind match {
    case Location.Absolute(scheme, _, _) => Some(scheme)
    case Location.Relative               => None
  }

  def scheme(scheme: Scheme): URL = {
    val location = kind match {
      case URL.Location.Relative      => URL.Location.Absolute(scheme, "", None)
      case abs: URL.Location.Absolute => abs.copy(scheme = scheme)
    }

    copy(kind = location)
  }

  /**
   * Returns a new java.net.URI representing this URL.
   */
  def toJavaURI: java.net.URI = new URI(encode)

  override def updateQueryParams(f: QueryParams => QueryParams): URL =
    copy(queryParams = f(queryParams))

}

object URL {
  val empty: URL = URL(path = Path.empty)

  /**
   * To better understand this implementation, read discussion:
   * https://github.com/zio/zio-http/pull/3017/files#r1716489733
   */
  private final class Err(rawUrl: String, cause: Throwable) extends MalformedURLException {
    override def getMessage: String  = s"""Invalid URL: "$rawUrl""""
    override def getCause: Throwable = cause
  }

  def decode(rawUrl: String): Either[MalformedURLException, URL] = {
    def invalidURL(e: Throwable = null): Either[MalformedURLException, URL] = Left(new Err(rawUrl = rawUrl, cause = e))

    try {
      val uri = new URI(rawUrl)
      val url = if (uri.isAbsolute) fromAbsoluteURI(uri) else fromRelativeURI(uri)

      url match {
        case Some(value) => Right(value)
        case None        => invalidURL()
      }
    } catch {
      case NonFatal(e) => invalidURL(e)
    }
  }

  def config: Config[URL] = Config.string.mapAttempt(decode(_).fold(throw _, identity))

  def fromURI(uri: URI): Option[URL] = if (uri.isAbsolute) fromAbsoluteURI(uri) else fromRelativeURI(uri)

  def root: URL = URL(Path.root)

  sealed trait Location { self =>
    def ++(that: Location): Location =
      if (that.isRelative) self
      else that

    def isAbsolute: Boolean = !isRelative

    def isRelative: Boolean = self match { case Location.Relative => true; case _ => false }
  }

  object Location {
    final case class Absolute(scheme: Scheme, host: String, originalPort: Option[Int]) extends Location {
      def portOrDefault: Option[Int]    = originalPort.orElse(scheme.defaultPort)
      def portIfNotDefault: Option[Int] = originalPort.filter(p => scheme.defaultPort.exists(_ != p))
      def port: Int                     = originalPort.orElse(scheme.defaultPort).getOrElse(Scheme.defaultPortForHTTP)
    }

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
    def path(relative: Boolean) =
      QueryParamEncoding.default.encode(
        if (relative || url.path.isEmpty) url.path.encode else url.path.addLeadingSlash.encode,
        url.queryParams.normalize,
        Charsets.Http,
      ) + url.fragment.fold("")(f => "#" + f.raw)

    url.kind match {
      case Location.Relative      => path(true)
      case abs: Location.Absolute =>
        val path2 = path(false)
        abs.portIfNotDefault match {
          case None             => s"${abs.scheme.encode}://${abs.host}$path2"
          case Some(customPort) => s"${abs.scheme.encode}://${abs.host}:$customPort$path2"
        }
    }
  }

  private[http] def encodeHttpPath(url: URL): String = {
    // As per the spec, the path should contain only the relative part and start with a slash.
    // Host and port information should be in the headers.
    // Query params are included while fragments are excluded.

    val pathBuf = new StringBuilder(256)

    val path = url.path

    path.segments.foreach { segment =>
      pathBuf.append('/')
      pathBuf.append(segment)
    }

    if (pathBuf.isEmpty | path.hasTrailingSlash) {
      pathBuf.append('/')
    }

    val qparams = url.queryParams

    if (qparams.isEmpty) {
      pathBuf.result()
    } else {
      // this branch could be more efficient with something like QueryParamEncoding.appendNonEmpty(pathBuf, qparams, Charsets.Http)
      // that directly filtered the keys/values and appended to the buffer
      // but for now the underlying Netty encoder requires the base url as a String anyway
      QueryParamEncoding.default.encode(pathBuf.result(), qparams.normalize, Charsets.Http)
    }
  }

  private[http] def fromAbsoluteURI(uri: URI): Option[URL] = {
    for {
      scheme <- Scheme.decode(uri.getScheme)
      host   <- Option(uri.getHost)
      path   <- Option(uri.getRawPath)
      port       = Option(uri.getPort).filter(_ != -1).orElse(scheme.defaultPort) // FIXME REMOVE defaultPort
      connection = URL.Location.Absolute(scheme, host, port)
      path2      = Path.decode(path)
      path3      = if (path.nonEmpty) path2.addLeadingSlash else path2
    } yield URL(path3, connection, QueryParams.decode(uri.getRawQuery), Fragment.fromURI(uri))
  }

  private[http] def fromRelativeURI(uri: URI): Option[URL] = for {
    path <- Option(uri.getRawPath)
  } yield URL(Path.decode(path), Location.Relative, QueryParams.decode(uri.getRawQuery), Fragment.fromURI(uri))

}
