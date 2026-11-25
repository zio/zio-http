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

  /**
   * Returns a copy with the given proxy URL. The proxy address is taken from
   * the host and the explicit port of the URL; with no explicit port, or no
   * host, the client does not connect to the proxy at this address.
   */
  def url(url: URL): Proxy = self.copy(url = url)

  /** Returns a copy with the given credentials for the proxy. */
  def credentials(credentials: Credentials): Proxy = self.copy(credentials = Some(credentials))

  /** Returns a copy with the given headers for the proxy. */
  def headers(headers: Headers): Proxy = self.copy(headers = headers)
}

/** Constructors for `Proxy`, and its `Config` reader. */
object Proxy {

  /**
   * Reads a proxy from the key `url`, the optional keys `credentials.user` and
   * `credentials.password`, and the optional list `headers` of `name` and
   * `value` pairs. A `url` that is not a valid URL fails with the message of `URL.decode`: `Invalid URL: ` followed by the input in double quotes.
   */
  def config: Config[Proxy] =
    (
      Config
        .string("url")
        .mapOrFail(s => URL.decode(s).left.map(error => Config.Error.InvalidData(message = error.getMessage))) ++
        (Config.string("user") ++ Config
          .secret("password")).nested("credentials").map { case (u, p) => Credentials(u, p) }.optional ++
        Config.chunkOf("headers", Config.string("name").zip(Config.string("value"))).optional.map {
          case Some(headers) => Headers(headers.map { case (name, value) => Header.Custom(name, value) }: _*)
          case None          => Headers.empty
        }
    ).map { case (url, creds, headers) =>
      Proxy(url, creds, headers)
    }

  /** A proxy with an empty URL, no credentials and no headers. */
  val empty: Proxy = Proxy(URL.empty)
}
