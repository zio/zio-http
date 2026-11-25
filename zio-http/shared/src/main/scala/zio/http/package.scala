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

package zio

import java.util.UUID

import zio.http.codec.{PathCodec, SegmentCodec}

package object http extends UrlInterpolator with MdInterpolator {

  /**
   * A smart constructor that attempts to construct a handler from the specified
   * value. If you have difficulty using this function, then please use the
   * constructors on [[zio.http.Handler]] directly.
   */
  def handler[H](handler: => H)(implicit h: ToHandler[H]): Handler[h.Env, h.Err, h.In, h.Out] =
    h.toHandler(handler)

  /**
   * Creates a handler that dies with a `RuntimeException` whose message is `message`. Use it as a placeholder for a handler that is not written yet.
   */
  def handlerTODO(message: String): Handler[Any, Nothing, Any, Nothing] =
    handler(ZIO.dieMessage(message))

  /**
   * Creates an effect that gets the services named by the parameter types of `fn` from the environment, such as the context that a [[HandlerAspect]] provides, and calls `fn` with them. The parameter types must be written explicitly: `withContext((user: String) => Response.text(user))`.
   */
  def withContext[C](fn: => C)(implicit c: WithContext[C]): ZIO[c.Env, c.Err, c.Out] =
    c.toZIO(fn)

  /**
   * A path segment named `name` that decodes a `Boolean`, for route patterns such as `Method.GET / "flags" / boolean("on")`.
   */
  def boolean(name: String): SegmentCodec[Boolean]         = SegmentCodec.bool(name)
  /**
   * A path segment named `name` that decodes an `Int`, for route patterns such as `Method.GET / "users" / int("id")`.
   */
  def int(name: String): SegmentCodec[Int]                 = SegmentCodec.int(name)
  /**
   * A path segment named `name` that decodes a `Long`.
   */
  def long(name: String): SegmentCodec[Long]               = SegmentCodec.long(name)
  /**
   * A path segment named `name` that decodes a `String`.
   */
  def string(name: String): SegmentCodec[String]           = SegmentCodec.string(name)
  /**
   * A path segment that matches the rest of the path, including no segments, and decodes it as a [[Path]].
   */
  val trailing: SegmentCodec[Path]                         = SegmentCodec.trailing
  /**
   * A path segment named `name` that decodes a `java.util.UUID`.
   */
  def uuid(name: String): SegmentCodec[UUID]               = SegmentCodec.uuid(name)
  /**
   * A path codec that matches one literal segment, `name` or one of `names`.
   */
  def anyOf(name: String, names: String*): PathCodec[Unit] =
    if (names.isEmpty) PathCodec.literal(name)
    else names.foldLeft(PathCodec.literal(name))((acc, n) => acc.orElse(PathCodec.literal(n)))

  /**
   * The empty path codec. `Method.GET / Root / "users"` is the same pattern as `Method.GET / "users"`.
   */
  val Root: PathCodec[Unit] = PathCodec.empty

  /**
   * A handler that takes a [[Request]] and returns a [[Response]].
   */
  type RequestHandler[-R, +Err] = Handler[R, Err, Request, Response]

  /**
   * The usual HTTP client type: a [[ZClient]] that takes a [[Body]], fails with a `Throwable`, and returns a [[Response]] whose body is streamed while the `Scope` of the request is open. Get one from a layer such as `Client.default`, and use `batched` to read the whole response before the effect completes.
   *
   * {{{
   * import zio._
   * import zio.http._
   *
   * val status: ZIO[Client, Throwable, Status] =
   *   Client.batched(Request.get(url"https://example.com/health")).map(_.status)
   *
   * val withClient: Task[Status] = status.provide(Client.default)
   * }}}
   *
   * @see [[https://zio.dev/zio-http/reference/client/ Client]]
   */
  type Client = ZClient[Any, Scope, Body, Throwable, Response]
  /**
   * The [[ZClient]] companion object, so that `Client.default` and `Client.batched(request)` can be written.
   */
  def Client: ZClient.type = ZClient

  /**
   * A channel that reads and writes [[WebSocketChannelEvent]] values on a WebSocket connection.
   */
  type WebSocketChannel = Channel[WebSocketChannelEvent, WebSocketChannelEvent]

  /**
   * An event of a WebSocket channel: a received [[WebSocketFrame]], or a change of the channel such as the handshake completing or the channel closing.
   */
  type WebSocketChannelEvent = ChannelEvent[WebSocketFrame]
}
