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
import zio.Config

import zio.http.internal.middlewares.Auth.Credentials
import zio.http.{Header, Headers}

/**
 * Represents the connection to the forward proxy before running the request
 *
 * @param url:
 *   url address of the proxy server
 * @param credentials:
 *   credentials for the proxy server. Encodes credentials with basic auth and
 *   put under the 'proxy-authorization' header
 * @param headers:
 *   headers for the request to the proxy server
 */
final case class Proxy(
  url: URL,
  credentials: Option[Credentials] = None,
  headers: Headers = Headers.empty,
) { self =>

  def url(url: URL): Proxy                         = self.copy(url = url)
  def credentials(credentials: Credentials): Proxy = self.copy(credentials = Some(credentials))
  def headers(headers: Headers): Proxy             = self.copy(headers = headers)
}

object Proxy {
  lazy val config: Config[Proxy] =
    (
      Config
        .string("url")
        .mapOrFail(s => URL.decode(s).left.map(error => Config.Error.InvalidData(message = error.getMessage))) ++
        (Config.string("user") ++ Config
          .string("password")).nested("credentials").map { case (u, p) => Credentials(u, p) }.optional ++
        Config.chunkOf("headers", Config.string("name").zip(Config.string("value"))).optional.map {
          case Some(headers) => Headers(headers.map { case (name, value) => Header.Custom(name, value) }: _*)
          case None          => Headers.empty
        }
    ).map { case (url, creds, headers) =>
      Proxy(url, creds, headers)
    }

  val empty: Proxy = Proxy(URL.empty)
}
