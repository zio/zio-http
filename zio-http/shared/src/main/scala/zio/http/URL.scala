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

import zio.{Config, Unsafe, ZIO, durationInt}

import zio.http.URL.Location.Relative
import zio.http.URL.{Fragment, Location}
import zio.http.internal._

/**
 * A URL made of a path, a location (relative, or absolute with a scheme, host
 * and optional port), query parameters and an optional fragment. A URL is
 * immutable, and its methods return modified copies.
 *
 * {{{
 * val url   = URL.decode("https://shop.example/items?page=2")
 * val path  = url.map(_.path.encode) // Right(/items)
 * val moved = url.map(_.path("/orders").encode) // Right(https://shop.example/orders?page=2)
 * }}}
 *
 * @see
 *   [[https://zio.dev/zio-http/reference/request/ Request]]
 * @param path
 *   the path of the URL
 * @param kind
 *   whether the URL is relative, or absolute with a scheme, host and port
 * @param queryParams
 *   the query parameters
 * @param fragment
 *   the fragment after the `#`, if any
 */
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
   *
   * The paths are combined with `Path#++`, the location is the one of the right
   * URL when it is absolute and the one of the left URL otherwise, the query
   * parameters of both are merged (values of a key present in both are
   * appended), and the fragment is the one of the right URL when it has one.
   */
  def ++(that: URL): URL =
    URL(
      self.path ++ that.path,
      self.kind ++ that.kind,
      self.queryParams ++ that.queryParams,
      that.fragment.orElse(this.fragment),
    )

  /** Returns a copy with `segment` appended to the path, as `Path#/` does. */
  def /(segment: String): URL = self.copy(path = self.path / segment)

  /**
   * Returns a copy with an absolute location that has the `http` scheme, the
   * given host and no port. It replaces the whole location, including a scheme
   * and port the URL had.
   */
  def absolute(host: String): URL =
    self.copy(kind = URL.Location.Absolute(Scheme.HTTP, host, None))

  /**
   * Returns a copy with an absolute location made of the given scheme, host and
   * port. It replaces the whole location.
   */
  def absolute(scheme: Scheme, host: String, port: Int): URL =
    self.copy(kind = URL.Location.Absolute(scheme, host, Some(port)))

  /** Returns a copy whose path has a leading slash. */
  def addLeadingSlash: URL = self.copy(path = path.addLeadingSlash)

  /**
   * Returns a copy with `path` appended to the path of this URL, as `Path#++`
   * does.
   */
  def addPath(path: Path): URL = self.copy(path = self.path ++ path)

  /**
   * Returns a copy with the path decoded by `Path.decode` from the string
   * appended to the path of this URL.
   */
  def addPath(path: String): URL = self.copy(path = self.path ++ Path.decode(path))

  /** Returns a copy whose path has a trailing slash. */
  def addTrailingSlash: URL = self.copy(path = path.addTrailingSlash)

  /**
   * Returns a copy whose query parameters are the current ones merged with
   * `queryParams`. Values of a key that is present in both are appended.
   */
  def addQueryParams(queryParams: QueryParams): URL =
    copy(queryParams = self.queryParams ++ queryParams)

  /** Returns a copy whose path has no leading slash. */
  def dropLeadingSlash: URL = self.copy(path = path.dropLeadingSlash)

  /** Returns a copy whose path has no trailing slash. */
  def dropTrailingSlash: URL = self.copy(path = path.dropTrailingSlash)

  /**
   * Returns the URL as a string. An absolute URL starts with the scheme, `://`,
   * the host and the port when the URL has one, even when it is the default
   * port of the scheme. The path, the query parameters after a `?` and the
   * fragment after a `#` follow. An absolute URL with a non-empty path that
   * does not start with a slash gets one after the host.
   */
  def encode: String = URL.encode(self)

  /**
   * Returns `true` when `that` is a `URL` that has the same location, fragment,
   * path and query parameters, after both are normalized.
   */
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

  /** Returns a hash code that is consistent with `equals`. */
  override def hashCode(): Int = {
    val normalized = self.normalize

    var hash = 17
    hash = hash * 31 + normalized.kind.hashCode
    hash = hash * 31 + normalized.path.hashCode
    hash = hash * 31 + normalized.queryParams.hashCode
    hash = hash * 31 + normalized.fragment.hashCode
    hash
  }

  /** Returns the same string as `encode`. */
  override def toString: String = encode

  /** Returns the host of an absolute URL, and `None` for a relative URL. */
  def host: Option[String] = kind match {
    case URL.Location.Relative      => None
    case abs: URL.Location.Absolute => Option(abs.host)
  }

  /**
   * Returns a copy with the given host. A relative URL becomes an absolute one
   * with the `http` scheme and no port.
   */
  def host(host: String): URL = {
    val location = kind match {
      case URL.Location.Relative      => URL.Location.Absolute(Scheme.HTTP, host, None)
      case abs: URL.Location.Absolute => abs.copy(host = host)
    }
    copy(kind = location)
  }

  /**
   * @return
   *   the host name, followed by `:` and the port when the URL has a port that
   *   is not the default port of its scheme. `None` for a relative URL.
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

  /** Returns `true` when the URL has a scheme and a host. */
  def isAbsolute: Boolean = self.kind match {
    case Location.Absolute(_, _, _) => true
    case Location.Relative          => false
  }

  /** Returns `true` when the URL is not absolute. */
  def isRelative: Boolean = !isAbsolute

  /**
   * Returns a copy whose path is empty when it was empty or the root path, and
   * otherwise has a leading slash, and whose query parameters are normalized
   * with `QueryParams#normalize`. `equals` and `hashCode` compare normalized
   * URLs.
   */
  def normalize: URL = {
    def normalizePath(path: Path): Path =
      if (path.isEmpty || path.isRoot) Path.empty
      else path.addLeadingSlash

    self.copy(path = normalizePath(path), queryParams = queryParams.normalize)
  }

  /** Returns a copy with the given path. */
  def path(path: Path): URL =
    copy(path = path)

  /** Returns a copy with the path decoded from the string by `Path.decode`. */
  def path(path: String): URL =
    copy(path = Path.decode(path))

  /**
   * Returns a copy with the given port. A relative URL becomes an absolute one
   * with the `http` scheme and an empty host.
   */
  def port(port: Int): URL = {
    val location = kind match {
      case URL.Location.Relative      => URL.Location.Absolute(Scheme.HTTP, "", Some(port))
      case abs: URL.Location.Absolute => abs.copy(originalPort = Some(port))
    }

    copy(kind = location)
  }

  /**
   * Returns the port written in the URL, or `None` when there is none or the
   * URL is relative.
   */
  def port: Option[Int] = kind match {
    case URL.Location.Relative      => None
    case abs: URL.Location.Absolute => abs.originalPort
  }

  /**
   * Returns the port written in the URL, or else the default port of the
   * scheme. It is `None` for a relative URL and for a scheme without a default
   * port.
   */
  def portOrDefault: Option[Int] = kind match {
    case URL.Location.Relative      => None
    case abs: URL.Location.Absolute => abs.portOrDefault
  }

  /**
   * Returns the port written in the URL when it differs from the default port
   * of the scheme. It is `None` for a relative URL, when no port is written,
   * and for a scheme without a default port.
   */
  def portIfNotDefault: Option[Int] = kind match {
    case URL.Location.Relative      => None
    case abs: URL.Location.Absolute => abs.portIfNotDefault
  }

  /** Returns the query parameters of the URL. */
  override def queryParameters: QueryParams =
    queryParams

  /**
   * Returns a copy with a relative location, that is without a scheme, host and
   * port.
   */
  def relative: URL = self.kind match {
    case URL.Location.Relative => self
    case _                     => self.copy(kind = URL.Location.Relative)
  }

  /**
   * RFC 3986 § 5.2 Relative Resolution
   * @param reference
   *   the URL to resolve relative to ``this`` base URL
   * @return
   *   a `Right` with the target URL, or a `Left` holding the message `cannot
   *   resolve against relative url` when this URL and the reference are both
   *   relative. An absolute reference is returned with its dot segments
   *   removed.
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

  /** Returns the scheme of an absolute URL, and `None` for a relative URL. */
  def scheme: Option[Scheme] = kind match {
    case Location.Absolute(scheme, _, _) => Some(scheme)
    case Location.Relative               => None
  }

  /**
   * Returns a copy with the given scheme. A relative URL becomes an absolute
   * one with an empty host and no port.
   */
  def scheme(scheme: Scheme): URL = {
    val location = kind match {
      case URL.Location.Relative      => URL.Location.Absolute(scheme, "", None)
      case abs: URL.Location.Absolute => abs.copy(scheme = scheme)
    }

    copy(kind = location)
  }

  /**
   * Returns a new java.net.URI representing this URL. It parses the result of
   * `encode`, so it throws a `java.net.URISyntaxException` when that string is
   * not a valid URI, for example when the host contains a space.
   */
  def toJavaURI: java.net.URI = new URI(encode)

  /**
   * Returns a copy whose query parameters are the result of applying `f` to the
   * current ones.
   */
  override def updateQueryParams(f: QueryParams => QueryParams): URL =
    copy(queryParams = f(queryParams))

}

/**
 * Constructors for `URL` and the types it is made of.
 */
object URL {

  /** The URL with an empty path, no query parameters and no fragment. */
  val empty: URL = URL(path = Path.empty)

  private val EncodedLeftBrace  = "%7B"
  private val EncodedRightBrace = "%7D"

  /**
   * To better understand this implementation, read discussion:
   * https://github.com/zio/zio-http/pull/3017/files#r1716489733
   */
  private final class Err(rawUrl: String, cause: Throwable) extends MalformedURLException {
    override def getMessage: String  = s"""Invalid URL: "$rawUrl""""
    override def getCause: Throwable = cause
  }

  /**
   * Parses a URL string. The empty string gives `URL.empty`. A string that
   * starts with `/` is a relative URL, split at the first `?` and `#` into
   * path, query parameters and fragment. Any other string is parsed with
   * `java.net.URI`: one with a scheme is an absolute URL and needs a host (so
   * `mailto:a@b.com` and `http:///a` fail), and one without a scheme is a
   * relative URL. Dot segments in the path are removed. Returns a `Left` with a
   * `java.net.MalformedURLException` whose message is `Invalid URL: "..."`
   * followed by the input when the string cannot be parsed.
   */
  def decode(rawUrl: String): Either[MalformedURLException, URL] = {
    def invalidURL(e: Throwable = null): Either[MalformedURLException, URL] = Left(new Err(rawUrl = rawUrl, cause = e))

    try {
      if (rawUrl.isEmpty) Right(URL.empty)
      // Fast path for relative URIs (the common case for incoming HTTP requests):
      // avoid allocating a java.net.URI by splitting on '?' and '#' directly.
      else if (rawUrl.charAt(0) == '/') {
        val fragmentIdx = rawUrl.indexOf('#')
        val rawQueryIdx = rawUrl.indexOf('?')
        // A '?' after '#' is part of the fragment, not a query delimiter
        val queryIdx    = if (fragmentIdx >= 0 && rawQueryIdx > fragmentIdx) -1 else rawQueryIdx

        val pathEnd  = if (queryIdx >= 0) queryIdx else if (fragmentIdx >= 0) fragmentIdx else rawUrl.length
        val rawPath  = rawUrl.substring(0, pathEnd)
        val rawQuery = if (queryIdx >= 0) {
          val queryEnd = if (fragmentIdx > queryIdx) fragmentIdx else rawUrl.length
          rawUrl.substring(queryIdx + 1, queryEnd)
        } else null
        val fragment = if (fragmentIdx >= 0) {
          val rawFrag = rawUrl.substring(fragmentIdx + 1)
          Some(Fragment.fromRaw(rawFrag))
        } else None

        Right(URL(Path.decodeRaw(rawPath), Location.Relative, QueryParams.decode(rawQuery), fragment))
      } else {
        val uri = new URI(sanitizeAbsoluteUrlQuery(rawUrl))
        val url = if (uri.isAbsolute) fromAbsoluteURI(uri) else fromRelativeURI(uri)

        url match {
          case Some(value) => Right(value)
          case None        => invalidURL()
        }
      }
    } catch {
      case NonFatal(e) => invalidURL(e)
    }
  }

  private[http] def fromAbsoluteURIOrNull(uri: URI): URL = {
    val scheme     = Scheme.unsafe.decode(uri.getScheme)(Unsafe.unsafe)
    if (scheme eq null) return null
    val host       = uri.getHost
    if (host eq null) return null
    val rawPath    = uri.getRawPath
    if (rawPath eq null) return null
    val port       = uri.getPort
    val portOpt    = if (port == -1) None else Some(port)
    val connection = URL.Location.Absolute(scheme, host, portOpt)
    val path2      = Path.decodeRaw(rawPath)
    val path3      = if (rawPath.nonEmpty) path2.addLeadingSlash else path2
    URL(path3, connection, QueryParams.decode(uri.getRawQuery), Fragment.fromURI(uri))
  }

  private[http] def fromRelativeURIOrNull(uri: URI): URL = {
    val rawPath = uri.getRawPath
    if (rawPath eq null) return null
    URL(Path.decodeRaw(rawPath), Location.Relative, QueryParams.decode(uri.getRawQuery), Fragment.fromURI(uri))
  }

  private[http] def decodeOrNull(rawUrl: String): URL = {
    try {
      if (rawUrl.isEmpty) URL.empty
      else if (rawUrl.charAt(0) == '/') {
        val fragmentIdx = rawUrl.indexOf('#')
        val rawQueryIdx = rawUrl.indexOf('?')
        val queryIdx    = if (fragmentIdx >= 0 && rawQueryIdx > fragmentIdx) -1 else rawQueryIdx

        val pathEnd  = if (queryIdx >= 0) queryIdx else if (fragmentIdx >= 0) fragmentIdx else rawUrl.length
        val rawPath  = rawUrl.substring(0, pathEnd)
        val rawQuery = if (queryIdx >= 0) {
          val queryEnd = if (fragmentIdx > queryIdx) fragmentIdx else rawUrl.length
          rawUrl.substring(queryIdx + 1, queryEnd)
        } else null
        val fragment = if (fragmentIdx >= 0) {
          val rawFrag = rawUrl.substring(fragmentIdx + 1)
          Some(Fragment.fromRaw(rawFrag))
        } else None

        URL(Path.decodeRaw(rawPath), Location.Relative, QueryParams.decode(rawQuery), fragment)
      } else {
        val uri = new URI(sanitizeAbsoluteUrlQuery(rawUrl))
        if (uri.isAbsolute) fromAbsoluteURIOrNull(uri) else fromRelativeURIOrNull(uri)
      }
    } catch {
      case NonFatal(_) => null
    }
  }

  private def sanitizeAbsoluteUrlQuery(rawUrl: String): String = {
    if (rawUrl.isEmpty || rawUrl.charAt(0) == '/') rawUrl
    else {
      val queryIdx = rawUrl.indexOf('?')
      if (queryIdx < 0) rawUrl
      else {
        val fragmentIdx = rawUrl.indexOf('#', queryIdx + 1)
        val queryEnd    = if (fragmentIdx >= 0) fragmentIdx else rawUrl.length
        val rawQuery    = rawUrl.substring(queryIdx + 1, queryEnd)
        if (rawQuery.indexOf('{') < 0 && rawQuery.indexOf('}') < 0) rawUrl
        else {
          val sanitizedQuery = rawQuery
            .replace("{", EncodedLeftBrace)
            .replace("}", EncodedRightBrace)
          rawUrl.substring(0, queryIdx + 1) + sanitizedQuery + rawUrl.substring(queryEnd)
        }
      }
    }
  }

  /**
   * A `Config` that reads a string and decodes it with `URL.decode`. A string
   * that cannot be decoded fails with the message of the
   * `MalformedURLException`, for example `Invalid URL: "http://exa mple"`.
   */
  def config: Config[URL] = Config.string.mapAttempt(decode(_).fold(throw _, identity))

  /**
   * Converts a `java.net.URI` to a URL. Returns `None` when an absolute URI has
   * no host, as in `mailto:a@b.c`.
   */
  def fromURI(uri: URI): Option[URL] = if (uri.isAbsolute) fromAbsoluteURI(uri) else fromRelativeURI(uri)

  /** The URL with the root path `/`, no query parameters and no fragment. */
  def root: URL = URL(Path.root)

  /**
   * Where a URL points: `Location.Relative`, or `Location.Absolute` with a
   * scheme, host and port.
   */
  sealed trait Location { self =>

    /** Returns `that` when it is absolute, and this location otherwise. */
    def ++(that: Location): Location =
      if (that.isRelative) self
      else that

    /** Returns `true` for `Location.Absolute`. */
    def isAbsolute: Boolean = !isRelative

    /** Returns `true` for `Location.Relative`. */
    def isRelative: Boolean = self match { case Location.Relative => true; case _ => false }
  }

  /** The locations a URL can have. */
  object Location {

    /**
     * An absolute location.
     *
     * @param scheme
     *   the scheme, such as `Scheme.HTTPS`
     * @param host
     *   the host name
     * @param originalPort
     *   the port written in the URL, if any
     */
    final case class Absolute(scheme: Scheme, host: String, originalPort: Option[Int]) extends Location {

      /**
       * Returns the port written in the URL, or else the default port of the
       * scheme.
       */
      def portOrDefault: Option[Int] = originalPort.orElse(scheme.defaultPort)

      /**
       * Returns the written port when it differs from the default port of the
       * scheme.
       */
      def portIfNotDefault: Option[Int] = originalPort.filter(p => scheme.defaultPort.exists(_ != p))

      /**
       * Returns the port written in the URL, or else the default port of the
       * scheme, or else 80 when the scheme has no default port.
       */
      def port: Int = originalPort.orElse(scheme.defaultPort).getOrElse(Scheme.defaultPortForHTTP)
    }

    /** A location without a scheme, host and port. */
    case object Relative extends Location
  }

  /**
   * The fragment of a URL, the part after the `#`.
   *
   * @param raw
   *   the fragment as written in the URL
   * @param decoded
   *   the decoded text of the fragment
   */
  final case class Fragment private (raw: String, decoded: String)

  /** Constructors for `Fragment`. */
  object Fragment {

    /**
     * Returns the fragment of the URI, or `None` when it has none. An empty
     * fragment (a URI ending in `#`) gives a fragment with empty text.
     */
    def fromURI(uri: URI): Option[Fragment] = for {
      raw     <- Option(uri.getRawFragment)
      decoded <- Option(uri.getFragment)
    } yield Fragment(raw, decoded)

    private[http] def fromRaw(raw: String): Fragment =
      Fragment(raw, java.net.URLDecoder.decode(raw, "UTF-8"))
  }

  private def encode(url: URL): String = {
    val path =
      QueryParamEncoding.encode(
        url.path.encodeBuilder,
        url.queryParams,
        Charsets.Http,
      )

    def fromAbsURL(abs: Location.Absolute, path: String) = {
      // Do not use `abs.portIfNotDefault` here. Setting a port in the URL that is the default
      // is an edge case. But checking it allocates an `Option` that is not needed in most cases.
      abs.originalPort match {
        case None =>
          val sb = ThreadLocals.stringBuilder.append(abs.scheme.encode).append("://").append(abs.host)
          if (path.nonEmpty && path.head != '/') sb.append('/')
          sb
        case port =>
          val sb = ThreadLocals.stringBuilder
            .append(abs.scheme.encode)
            .append("://")
            .append(abs.host)
            .append(':')
            .append(port.get)
          if (path.nonEmpty && path.head != '/') sb.append('/')
          sb
      }
    }

    url.kind match {
      case Location.Relative if url.fragment.isEmpty      =>
        path
      case Relative                                       =>
        ThreadLocals.stringBuilder.append(path).append('#').append(url.fragment.get.raw).toString
      case abs: Location.Absolute if url.fragment.isEmpty =>
        fromAbsURL(abs, path).append(path).toString
      case abs: Location.Absolute                         =>
        fromAbsURL(abs, path).append(path).append('#').append(url.fragment.get.raw).toString

    }
  }

  private[http] def encodeHttpPath(url: URL): String = {
    // As per the spec, the path should contain only the relative part and start with a slash.
    // Host and port information should be in the headers.
    // Query params are included while fragments are excluded.

    val prefix = if (!url.path.hasLeadingSlash) "/" else ""

    prefix + (if (url.queryParams.isEmpty) {
                url.path.encodeBuilder.toString
              } else {
                // this branch could be more efficient with something like QueryParamEncoding.appendNonEmpty(pathBuf, qparams, Charsets.Http)
                // that directly filtered the keys/values and appended to the buffer
                // but for now the underlying Netty encoder requires the base url as a String anyway
                QueryParamEncoding.encode(url.path.encodeBuilder, url.queryParams, Charsets.Http)
              })
  }

  private[http] def fromAbsoluteURI(uri: URI): Option[URL] = {
    for {
      scheme <- Scheme.decode(uri.getScheme)
      host   <- Option(uri.getHost)
      path   <- Option(uri.getRawPath)
      port       = Option(uri.getPort).filter(_ != -1)
      connection = URL.Location.Absolute(scheme, host, port)
      path2      = Path.decodeRaw(path)
      path3      = if (path.nonEmpty) path2.addLeadingSlash else path2
    } yield URL(path3, connection, QueryParams.decode(uri.getRawQuery), Fragment.fromURI(uri))
  }

  private[http] def fromRelativeURI(uri: URI): Option[URL] = for {
    path <- Option(uri.getRawPath)
  } yield URL(Path.decodeRaw(path), Location.Relative, QueryParams.decode(uri.getRawQuery), Fragment.fromURI(uri))

}
