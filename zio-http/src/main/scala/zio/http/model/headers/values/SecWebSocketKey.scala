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

final case class SecWebSocketKey(base64EncodedKey: String)

/**
 * The Sec-WebSocket-Key header is used in the WebSocket handshake. It is sent
 * from the client to the server to provide part of the information used by the
 * server to prove that it received a valid WebSocket handshake. This helps
 * ensure that the server does not accept connections from non-WebSocket clients
 * (e.g. HTTP clients) that are being abused to send data to unsuspecting
 * WebSocket servers.
 *
 * See:
 * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-WebSocket-Key
 */
object SecWebSocketKey {
  def toSecWebSocketKey(key: String): Either[String, SecWebSocketKey] = {
    try {
      val decodedKey = java.util.Base64.getDecoder.decode(key)
      if (decodedKey.length == 16) Right(SecWebSocketKey(key))
      else Left("Invalid Sec-WebSocket-Key header")
    } catch {
      case _: Throwable => Left("Invalid Sec-WebSocket-Key header")
    }

  }

  def fromSecWebSocketKey(secWebSocketKey: SecWebSocketKey): String =
    secWebSocketKey.base64EncodedKey
}
