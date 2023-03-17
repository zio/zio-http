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

final case class SecWebSocketAccept(hashedKey: String)

/**
 * The Sec-WebSocket-Accept header is used in the websocket opening handshake.
 * It would appear in the response headers. That is, this is header is sent from
 * server to client to inform that server is willing to initiate a websocket
 * connection.
 *
 * See:
 * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-WebSocket-Accept
 */
object SecWebSocketAccept {

  def parse(value: String): Either[String, SecWebSocketAccept] =
    if (value.trim.isEmpty) Left("Invalid Sec-WebSocket-Accept header")
    else Right(SecWebSocketAccept(value))

  def render(secWebSocketAccept: SecWebSocketAccept): String =
    secWebSocketAccept.hashedKey
}
