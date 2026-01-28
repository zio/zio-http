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

import java.net.InetAddress
import java.security.cert.Certificate

import zio._

import zio.http.internal.{HeaderOps, QueryOps}

final case class Request(
  version: Version = Version.Default,
  method: Method = Method.ANY,
  url: URL = URL.empty,
  headers: Headers = Headers.empty,
  body: Body = Body.empty,
  remoteAddress: Option[InetAddress] = None,
  remoteCertificate: Option[Certificate] = None,
) extends HeaderOps[Request]
    with QueryOps[Request] { self =>

  /**
   * A right-biased way of combining two requests. Most information will be
   * merged, but in cases where this does not make sense (e.g. two non-empty
   * bodies), the information from the right request will be used.
   */
  def ++(that: Request): Request =
    Request(
      self.version ++ that.version,
      self.method ++ that.method,
      self.url ++ that.url,
      self.headers ++ that.headers,
      self.body ++ that.body,
      that.remoteAddress.orElse(self.remoteAddress),
    )

  /** Custom headers and headers required by the used Body */
  val allHeaders: Headers = {
    body.mediaType match {
      case Some(mediaType) =>
        headers ++ Headers(Header.ContentType(mediaType, body.boundary))
      case None            =>
        headers
    }
  }

  def addLeadingSlash: Request = self.copy(url = url.addLeadingSlash)

  def addCookie(cookie: Cookie.Request): Request =
    updateHeaders { (headers: Headers) =>
      if (headers.contains(Header.Cookie.name))
        headers
          .removeHeader(Header.Cookie.name)
          .addHeader(Header.Cookie(headers.get(Header.Cookie).get.value :+ cookie))
      else
        headers.addHeader(Header.Cookie(NonEmptyChunk(cookie)))
    }

  def addCookies(cookie: Cookie.Request, cookies: Cookie.Request*): Request =
    updateHeaders { (headers: Headers) =>
      if (headers.contains(Header.Cookie.name))
        headers
          .removeHeader(Header.Cookie.name)
          .addHeader(Header.Cookie(headers.get(Header.Cookie).get.value ++ NonEmptyChunk(cookie, cookies: _*)))
      else
        headers.addHeader(Header.Cookie(NonEmptyChunk(cookie, cookies: _*)))
    }

  /**
   * Add trailing slash to the path.
   */
  def addTrailingSlash: Request = self.copy(url = self.url.addTrailingSlash)

  /**
   * Collects the potentially streaming body of the response into a single
   * chunk.
   *
   * Any errors that occur from the collection of the body will be caught and
   * propagated to the Body
   */
  def collect(implicit trace: Trace): ZIO[Any, Nothing, Request] =
    self.body.materialize.map { b =>
      if (b eq self.body) self
      else self.copy(body = b)
    }

  def dropLeadingSlash: Request = updateURL(_.dropLeadingSlash)

  /**
   * Drops trailing slash from the path.
   */
  def dropTrailingSlash: Request = updateURL(_.dropTrailingSlash)

  /**
   * Consumes the streaming body fully and then discards it while also ignoring
   * any failures
   */
  def ignoreBody(implicit trace: Trace): ZIO[Any, Nothing, Request] = {
    val out   = self.copy(body = Body.empty)
    val body0 = self.body
    if (body0.isComplete) Exit.succeed(out)
    else body0.asStream.runDrain.ignore.as(out)
  }

  def patch(p: Request.Patch): Request =
    self.copy(headers = self.headers ++ p.addHeaders, url = self.url.addQueryParams(p.addQueryParams))

  def path: Path = url.path

  def path(path: Path): Request = updateURL(_.path(path))

  override def queryParameters: QueryParams =
    url.queryParams

  /**
   * Updates the headers using the provided function
   */
  override def updateHeaders(update: Headers => Headers)(implicit trace: Trace): Request =
    self.copy(headers = update(self.headers))

  override def updateQueryParams(f: QueryParams => QueryParams): Request =
    copy(url = url.updateQueryParams(f))

  def updateURL(f: URL => URL): Request = copy(url = f(url))

  def updatePath(f: Path => Path): Request = copy(url = url.copy(path = f(path)))

  /**
   * Unnests the request by the specified prefix. If the request URL is not
   * nested at the specified path, then this has no effect on the URL.
   */
  def unnest(prefix: Path): Request =
    copy(url = self.url.copy(path = self.url.path.unnest(prefix)))

  /**
   * Returns a request with a body derived from the current body.
   */
  def updateBody(f: Body => Body): Request = self.copy(body = f(body))

  /**
   * Returns a request with a body derived from the current body in an effectful
   * way.
   */
  def updateBodyZIO[R, E](f: Body => ZIO[R, E, Body]): ZIO[R, E, Request] = f(body).map(withBody)

  /**
   * Returns a request with the specified body.
   */
  def withBody(body: Body): Request = self.copy(body = body)

  /**
   * Returns the cookie with the given name if it exists.
   */
  def cookie(name: String): Option[Cookie] =
    cookies.find(_.name == name)

  /**
   * Uses the cookie with the given name if it exists and runs `f` with the
   * cookie afterwards.
   */
  def cookieWithZIO[R, A](name: String)(f: Cookie => ZIO[R, Throwable, A])(implicit
    trace: Trace,
  ): ZIO[R, Throwable, A] =
    cookieWithOrFailImpl[R, Throwable, A](name)(new java.util.NoSuchElementException(s"cookie doesn't exist: $name"))(f)

  /**
   * Uses the cookie with the given name if it exists and runs `f` with the
   * cookie afterwards.
   *
   * Also, you can set a custom failure value from a missing cookie with `E`.
   */
  def cookieWithOrFail[R, E, A](name: String)(missingCookieError: E)(f: Cookie => ZIO[R, E, A])(implicit
    trace: Trace,
  ): ZIO[R, E, A] =
    cookieWithOrFailImpl(name)(missingCookieError)(f)

  private def cookieWithOrFailImpl[R, E, A](name: String)(e: E)(f: Cookie => ZIO[R, E, A])(implicit
    trace: Trace,
  ): ZIO[R, E, A] = {
    cookie(name) match {
      case Some(value) => f(value)
      case None        => ZIO.fail(e)
    }
  }

  /**
   * Returns all cookies from the request.
   */
  def cookies: Chunk[Cookie] =
    header(Header.Cookie).fold(Chunk.empty[Cookie])(_.value.toChunk)

  /**
   * Returns an `A` if it exists from the cookie-based flash-scope.
   */
  def flash[A](flash: Flash[A]): Option[A] =
    Flash.run(flash, self).toOption

}

object Request {
  final case class Patch(addHeaders: Headers, addQueryParams: QueryParams) { self =>
    def ++(that: Patch): Patch =
      Patch(self.addHeaders ++ that.addHeaders, self.addQueryParams ++ that.addQueryParams)
  }

  def delete(path: String): Request = Request(method = Method.DELETE, url = pathOrUrl(path))

  def delete(url: URL): Request = Request(method = Method.DELETE, url = url)

  def get(path: String): Request = Request(method = Method.GET, url = pathOrUrl(path))

  def get(url: URL): Request = Request(method = Method.GET, url = url)

  def head(path: String): Request = Request(method = Method.HEAD, url = pathOrUrl(path))

  def head(url: URL): Request = Request(method = Method.HEAD, url = url)

  def options(path: String): Request = Request(method = Method.OPTIONS, url = pathOrUrl(path))

  def options(url: URL): Request = Request(method = Method.OPTIONS, url = url)

  def patch(path: String, body: Body): Request = Request(method = Method.PATCH, url = pathOrUrl(path), body = body)

  def patch(url: URL, body: Body): Request = Request(method = Method.PATCH, url = url, body = body)

  def post(path: String, body: Body): Request = Request(method = Method.POST, url = pathOrUrl(path), body = body)

  def post(url: URL, body: Body): Request = Request(method = Method.POST, url = url, body = body)

  def put(path: String, body: Body): Request = Request(method = Method.PUT, url = pathOrUrl(path), body = body)

  def put(url: URL, body: Body): Request = Request(method = Method.PUT, url = url, body = body)

  /**
   * Convenience function that detects and handles cases when an absolute URL
   * was passed in the path variant of the request constructors
   */
  private def pathOrUrl(path: String): URL =
    if (path.startsWith("http://") || path.startsWith("https://")) {
      URL.decode(path).getOrElse(URL(Path(path)))
    } else {
      URL(Path(path))
    }

  object Patch {
    val empty: Patch = Patch(Headers.empty, QueryParams.empty)
  }
}
