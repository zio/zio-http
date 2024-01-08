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

package zio.http.internal

import zio.Chunk

import zio.http.Cookie
import zio.http.netty.NettyCookieEncoding

private[http] trait CookieEncoding {
  def encodeRequestCookie(cookie: Cookie.Request, validate: Boolean): String
  def decodeRequestCookie(header: String, validate: Boolean): Chunk[Cookie.Request]

  def encodeResponseCookie(cookie: Cookie.Response, validate: Boolean): String
  def decodeResponseCookie(header: String, validate: Boolean): Cookie.Response
}

private[http] object CookieEncoding {
  val default: CookieEncoding = NettyCookieEncoding
}
