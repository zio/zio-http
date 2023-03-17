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

package zio.http.model.headers.values

import zio.http.URL

final case class SecWebSocketOrigin(url: URL)

/**
 * The Sec-WebSocket-Origin header is used to protect against unauthorized
 * cross-origin use of a WebSocket server by scripts using the |WebSocket| API
 * in a Web browser. The server is informed of the script origin generating the
 * WebSocket connection request.
 */
object SecWebSocketOrigin {

  def fromSecWebSocketOrigin(secWebSocketOrigin: SecWebSocketOrigin): String = {
    secWebSocketOrigin.url.encode

  }

  def toSecWebSocketOrigin(value: String): Either[String, SecWebSocketOrigin] = {
    if (value.trim == "") Left("Invalid Sec-WebSocket-Origin header: empty value")
    else
      URL
        .fromString(value)
        .left
        .map(_ => "Invalid Sec-WebSocket-Origin header: invalid URL")
        .map(url => SecWebSocketOrigin(url))
  }
}
