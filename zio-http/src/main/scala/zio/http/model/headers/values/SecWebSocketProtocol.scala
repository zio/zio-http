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

import zio.{Chunk, NonEmptyChunk}

final case class SecWebSocketProtocol(subProtocols: NonEmptyChunk[String])

/**
 * The Sec-WebSocket-Protocol header field is used in the WebSocket opening
 * handshake. It is sent from the client to the server and back from the server
 * to the client to confirm the subprotocol of the connection. This enables
 * scripts to both select a subprotocol and be sure that the server agreed to
 * serve that subprotocol.
 *
 * See:
 * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-WebSocket-Protocol
 */
object SecWebSocketProtocol {
  // https://www.iana.org/assignments/websocket/websocket.xml#subprotocol-name

  def parse(subProtocols: String): Either[String, SecWebSocketProtocol] = {
    NonEmptyChunk.fromChunk(Chunk.fromArray(subProtocols.split(",")).map(_.trim).filter(_.nonEmpty)) match {
      case Some(value) => Right(SecWebSocketProtocol(value))
      case None        => Left("Invalid Sec-WebSocket-Protocol header")
    }
  }

  def render(secWebSocketProtocol: SecWebSocketProtocol): String =
    secWebSocketProtocol.subProtocols.mkString(", ")
}
