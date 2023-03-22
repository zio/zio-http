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
object HeaderNames {
  val accept: String                        = "accept"
  val acceptEncoding: String                = "accept-encoding"
  val acceptLanguage: String                = "accept-language"
  val acceptRanges: String                  = "accept-ranges"
  val acceptPatch: String                   = "accept-patch"
  val accessControlAllowCredentials: String = "access-control-allow-credentials"
  val accessControlAllowHeaders: String     = "access-control-allow-headers"
  val accessControlAllowMethods: String     = "access-control-allow-methods"
  val accessControlAllowOrigin: String      = "access-control-allow-origin"
  val accessControlExposeHeaders: String    = "access-control-expose-headers"
  val accessControlMaxAge: String           = "access-control-max-age"
  val accessControlRequestHeaders: String   = "access-control-request-headers"
  val accessControlRequestMethod: String    = "access-control-request-method"
  val age: String                           = "age"
  val allow: String                         = "allow"
  val authorization: String                 = "authorization"
  val cacheControl: String                  = "cache-control"
  val connection: String                    = "connection"
  val contentBase: String                   = "content-base"
  val contentEncoding: String               = "content-encoding"
  val contentLanguage: String               = "content-language"
  val contentLength: String                 = "content-length"
  val contentLocation: String               = "content-location"
  val contentTransferEncoding: String       = "content-transfer-encoding"
  val contentDisposition: String            = "content-disposition"
  val contentMd5: String                    = "content-md5"
  val contentRange: String                  = "content-range"
  val contentSecurityPolicy: String         = "content-security-policy"
  val contentType: String                   = "content-type"
  val cookie: String                        = "cookie"
  val date: String                          = "date"
  val dnt: String                           = "dnt"
  val etag: String                          = "etag"
  val expect: String                        = "expect"
  val expires: String                       = "expires"
  val from: String                          = "from"
  val host: String                          = "host"
  val ifMatch: String                       = "if-match"
  val ifModifiedSince: String               = "if-modified-since"
  val ifNoneMatch: String                   = "if-none-match"
  val ifRange: String                       = "if-range"
  val ifUnmodifiedSince: String             = "if-unmodified-since"
  val lastModified: String                  = "last-modified"
  val location: String                      = "location"
  val maxForwards: String                   = "max-forwards"
  val origin: String                        = "origin"
  val pragma: String                        = "pragma"
  val proxyAuthenticate: String             = "proxy-authenticate"
  val proxyAuthorization: String            = "proxy-authorization"
  val range: String                         = "range"
  val referer: String                       = "referer"
  val retryAfter: String                    = "retry-after"
  val secWebSocketLocation: String          = "sec-websocket-location"
  val secWebSocketOrigin: String            = "sec-websocket-origin"
  val secWebSocketProtocol: String          = "sec-websocket-protocol"
  val secWebSocketVersion: String           = "sec-websocket-version"
  val secWebSocketKey: String               = "sec-websocket-key"
  val secWebSocketAccept: String            = "sec-websocket-accept"
  val secWebSocketExtensions: String        = "sec-websocket-extensions"
  val server: String                        = "server"
  val setCookie: String                     = "set-cookie"
  val te: String                            = "te"
  val trailer: String                       = "trailer"
  val transferEncoding: String              = "transfer-encoding"
  val upgrade: String                       = "upgrade"
  val upgradeInsecureRequests: String       = "upgrade-insecure-requests"
  val userAgent: String                     = "user-agent"
  val vary: String                          = "vary"
  val via: String                           = "via"
  val warning: String                       = "warning"
  val webSocketLocation: String             = "websocket-location"
  val webSocketOrigin: String               = "websocket-origin"
  val webSocketProtocol: String             = "websocket-protocol"
  val wwwAuthenticate: String               = "www-authenticate"
  val xFrameOptions: String                 = "x-frame-options"
  val xRequestedWith: String                = "x-requested-with"
}
