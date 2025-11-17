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

package zio.http.stomp

import zio._

import zio.http._

/**
 * Extension methods for working with STOMP frames in zio-http WebSocket
 * connections
 */
trait StompSyntax {

  /**
   * Extension methods for WebSocketFrame to encode/decode STOMP frames
   */
  implicit class WebSocketFrameStompOps(val frame: WebSocketFrame) {

    /**
     * Decodes a WebSocket binary frame as a STOMP frame
     */
    def asStompFrame(implicit trace: Trace): IO[Throwable, StompFrame] = frame match {
      case WebSocketFrame.Binary(bytes) =>
        ZIO.fromEither(
          StompCodec.binaryCodec.decode(bytes),
        )
      case _                            =>
        ZIO.fail(new IllegalArgumentException("STOMP frames must be transmitted as WebSocket binary frames"))
    }
  }

  /**
   * Extension methods for StompFrame to convert to WebSocketFrame
   */
  implicit class StompFrameWebSocketOps(val stompFrame: StompFrame) {

    /**
     * Encodes a STOMP frame as a WebSocket binary frame
     */
    def toWebSocketFrame: WebSocketFrame =
      WebSocketFrame.Binary(stompFrame.encode)
  }
}

object StompSyntax extends StompSyntax
