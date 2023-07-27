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

import zio.ZIO

import zio.http.internal.HeaderOps

final case class Request(
  version: Version = Version.Default,
  method: Method = Method.ANY,
  url: URL = URL.empty,
  headers: Headers = Headers.empty,
  body: Body = Body.empty,
  remoteAddress: Option[InetAddress] = None,
) extends HeaderOps[Request] { self =>

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
  lazy val allHeaders: Headers = {
    body.mediaType match {
      case Some(mediaType) =>
        headers ++ Headers(Header.ContentType(mediaType, body.boundary))
      case None            =>
        headers
    }
  }

  def addLeadingSlash: Request = self.copy(url = url.addLeadingSlash)

  /**
   * Add trailing slash to the path.
   */
  def addTrailingSlash: Request = self.copy(url = self.url.addTrailingSlash)

  /**
   * Collects the potentially streaming body of the request into a single chunk.
   */
  def collect: ZIO[Any, Throwable, Request] =
    if (self.body.isComplete) ZIO.succeed(self)
    else
      self.body.asChunk.map { bytes =>
        self.copy(body = Body.fromChunk(bytes))
      }

  def dropLeadingSlash: Request = updateURL(_.dropLeadingSlash)

  /**
   * Drops trailing slash from the path.
   */
  def dropTrailingSlash: Request = updateURL(_.dropTrailingSlash)

  /** Consumes the streaming body fully and then drops it */
  def ignoreBody: ZIO[Any, Throwable, Request] =
    self.collect.map(_.copy(body = Body.empty))

  def patch(p: Request.Patch): Request =
    self.copy(headers = self.headers ++ p.addHeaders, url = self.url.addQueryParams(p.addQueryParams))

  def path: Path = url.path

  def path(path: Path): Request = updateURL(_.path(path))

  /**
   * Updates the headers using the provided function
   */
  override def updateHeaders(update: Headers => Headers): Request =
    self.copy(headers = update(self.headers))

  def updateURL(f: URL => URL): Request = copy(url = f(url))

  /**
   * Unnests the request by the specified prefix. If the request URL is not
   * nested at the specified path, then this has no effect on the URL.
   */
  def unnest(prefix: Path): Request =
    copy(url = self.url.copy(path = self.url.path.unnest(prefix)))
}

object Request {
  final case class Patch(addHeaders: Headers, addQueryParams: QueryParams) { self =>
    def ++(that: Patch): Patch =
      Patch(self.addHeaders ++ that.addHeaders, self.addQueryParams ++ that.addQueryParams)
  }

  def delete(path: String): Request = Request(method = Method.DELETE, url = URL(Path(path)))

  def delete(url: URL): Request = Request(method = Method.DELETE, url = url)

  def get(path: String): Request = Request(method = Method.GET, url = URL(Path(path)))

  def get(url: URL): Request = Request(method = Method.GET, url = url)

  def head(path: String): Request = Request(method = Method.HEAD, url = URL(Path(path)))

  def head(url: URL): Request = Request(method = Method.HEAD, url = url)

  def options(path: String): Request = Request(method = Method.OPTIONS, url = URL(Path(path)))

  def options(url: URL): Request = Request(method = Method.OPTIONS, url = url)

  def patch(path: String, body: Body): Request = Request(method = Method.PATCH, url = URL(Path(path)), body = body)

  def patch(url: URL, body: Body): Request = Request(method = Method.PATCH, url = url, body = body)

  def post(path: String, body: Body): Request = Request(method = Method.POST, url = URL(Path(path)), body = body)

  def post(url: URL, body: Body): Request = Request(method = Method.POST, url = url, body = body)

  def put(path: String, body: Body): Request = Request(method = Method.PUT, url = URL(Path(path)), body = body)

  def put(url: URL, body: Body): Request = Request(method = Method.PUT, url = url, body = body)
}
