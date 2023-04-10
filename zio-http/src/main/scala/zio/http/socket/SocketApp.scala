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

package zio.http.socket

import zio._

import zio.http._

final case class SocketApp[-R](
  decoder: SocketDecoder = SocketDecoder.default,
  protocol: SocketProtocol = SocketProtocol.default,
  message: Option[ChannelEvent[WebSocketFrame, WebSocketFrame] => ZIO[R, Throwable, Any]] = None,
) { self =>

  /**
   * Creates a socket connection on the provided URL. Typically used to connect
   * as a client.
   */
  def connect(
    url: String,
    headers: Headers = Headers.empty,
  )(implicit trace: Trace): ZIO[R with Client with Scope, Throwable, Response] =
    Client.socket(url, self, headers)

  /**
   * Provides the socket app with its required environment, which eliminates its
   * dependency on `R`.
   */
  def provideEnvironment(env: ZEnvironment[R])(implicit trace: Trace): SocketApp[Any] =
    self.copy(message = self.message.map(cb => event => cb(event).provideEnvironment(env)))

  /**
   * Converts the socket app to a HTTP app.
   */
  def toHandler(implicit trace: Trace): Handler[R, Nothing, Any, Response] = Handler.fromZIO(toResponse)
  def toRoute(implicit trace: Trace): Http[R, Nothing, Any, Response]      = toHandler.toHttp

  /**
   * Creates a new response from the socket app.
   */
  def toResponse(implicit trace: Trace): ZIO[R, Nothing, Response] =
    ZIO.environment[R].flatMap { env =>
      Response.fromSocketApp(self.provideEnvironment(env))
    }

  /**
   * Frame decoder configuration
   */
  def withDecoder(decoder: SocketDecoder): SocketApp[R] =
    copy(decoder = decoder, protocol = protocol.withDecoderConfig(decoder))

  /**
   * Server side websocket configuration
   */
  def withProtocol(protocol: SocketProtocol): SocketApp[R] =
    copy(protocol = protocol)
}

object SocketApp {

  def apply[R](socket: ChannelEvent[WebSocketFrame, WebSocketFrame] => ZIO[R, Throwable, Any]): SocketApp[R] =
    SocketApp(message = Option(socket))
}
