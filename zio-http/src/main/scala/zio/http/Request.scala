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
  body: Body,
  headers: Headers,
  method: Method,
  url: URL,
  version: Version,
  remoteAddress: Option[InetAddress],
) extends HeaderOps[Request] { self =>

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

  /**
   * Drops trailing slash from the path.
   */
  def dropTrailingSlash: Request = self.copy(url = self.url.dropTrailingSlash)

  /** Consumes the streaming body fully and then drops it */
  def ignoreBody: ZIO[Any, Throwable, Request] =
    self.collect.map(_.copy(body = Body.empty))

  def patch(p: Request.Patch): Request =
    Request(
      body,
      headers ++ p.addHeaders,
      method,
      url.copy(queryParams = url.queryParams ++ p.addQueryParams),
      version,
      remoteAddress,
    )

  val path = url.path

  def updatePath(path: Path): Request = self.copy(url = self.url.copy(path = path))

  /**
   * Updates the headers using the provided function
   */
  override def updateHeaders(update: Headers => Headers): Request =
    self.copy(headers = update(self.headers))
}

object Request {
  final case class Patch(addHeaders: Headers, addQueryParams: QueryParams) { self =>
    def ++(that: Patch): Patch =
      Patch(self.addHeaders ++ that.addHeaders, self.addQueryParams ++ that.addQueryParams)
  }

  def default(method: Method, url: URL, body: Body = Body.empty) =
    Request(body, headersForBody(body), method, url, Version.`HTTP/1.1`, Option.empty)

  def delete(url: URL): Request = default(Method.DELETE, url)

  def get(url: URL): Request = default(Method.GET, url)

  def options(url: URL): Request = default(Method.OPTIONS, url)

  def patch(body: Body, url: URL): Request = default(Method.PATCH, url, body)

  def post(body: Body, url: URL): Request = default(Method.POST, url, body)

  def put(body: Body, url: URL): Request = default(Method.PUT, url, body)

  private def headersForBody(body: Body): Headers =
    body.mediaType match {
      case Some(mediaType) =>
        Headers(Header.ContentType(mediaType, body.boundary))
      case None            =>
        Headers.empty
    }
}
