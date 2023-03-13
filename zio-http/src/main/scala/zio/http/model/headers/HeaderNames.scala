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

package zio.http.model.headers

/**
 * List of commonly use HeaderNames. They are provided to reduce bugs caused by
 * typos and also to improve performance. `HeaderNames` aren't encoded everytime
 * one needs to send them over the wire.
 */
trait HeaderNames {
  val accept: CharSequence
  val acceptEncoding: CharSequence
  val acceptLanguage: CharSequence
  val acceptRanges: CharSequence
  val acceptPatch: CharSequence
  val accessControlAllowCredentials: CharSequence
  val accessControlAllowHeaders: CharSequence
  val accessControlAllowMethods: CharSequence
  val accessControlAllowOrigin: CharSequence
  val accessControlExposeHeaders: CharSequence
  val accessControlMaxAge: CharSequence
  val accessControlRequestHeaders: CharSequence
  val accessControlRequestMethod: CharSequence
  val age: CharSequence
  val allow: CharSequence
  val authorization: CharSequence
  val cacheControl: CharSequence
  val connection: CharSequence
  val contentBase: CharSequence
  val contentEncoding: CharSequence
  val contentLanguage: CharSequence
  val contentLength: CharSequence
  val contentLocation: CharSequence
  val contentTransferEncoding: CharSequence
  val contentDisposition: CharSequence
  val contentMd5: CharSequence
  val contentRange: CharSequence
  val contentSecurityPolicy: CharSequence
  val contentType: CharSequence
  val cookie: CharSequence
  val date: CharSequence
  val dnt: CharSequence
  val etag: CharSequence
  val expect: CharSequence
  val expires: CharSequence
  val from: CharSequence
  val host: CharSequence
  val ifMatch: CharSequence
  val ifModifiedSince: CharSequence
  val ifNoneMatch: CharSequence
  val ifRange: CharSequence
  val ifUnmodifiedSince: CharSequence
  val lastModified: CharSequence
  val location: CharSequence
  val maxForwards: CharSequence
  val origin: CharSequence
  val pragma: CharSequence
  val proxyAuthenticate: CharSequence
  val proxyAuthorization: CharSequence
  val range: CharSequence
  val referer: CharSequence
  val retryAfter: CharSequence
  val secWebSocketLocation: CharSequence
  val secWebSocketOrigin: CharSequence
  val secWebSocketProtocol: CharSequence
  val secWebSocketVersion: CharSequence
  val secWebSocketKey: CharSequence
  val secWebSocketAccept: CharSequence
  val secWebSocketExtensions: CharSequence
  val server: CharSequence
  val setCookie: CharSequence
  val te: CharSequence
  val trailer: CharSequence
  val transferEncoding: CharSequence
  val upgrade: CharSequence
  val upgradeInsecureRequests: CharSequence
  val userAgent: CharSequence
  val vary: CharSequence
  val via: CharSequence
  val warning: CharSequence
  val webSocketLocation: CharSequence
  val webSocketOrigin: CharSequence
  val webSocketProtocol: CharSequence
  val wwwAuthenticate: CharSequence
  val xFrameOptions: CharSequence
  val xRequestedWith: CharSequence
}
