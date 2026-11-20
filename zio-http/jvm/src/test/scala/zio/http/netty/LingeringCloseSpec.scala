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

package zio.http.netty

import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

import scala.collection.mutable

import zio._
import zio.test._

import zio.http.ZIOHttpSpec

import io.netty.buffer.Unpooled
import io.netty.channel._
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http._

/**
 * Exercised on an embedded channel, which makes it possible to have the decoder
 * hold several requests at once and to move time on, neither of which a real
 * socket does reliably. An embedded channel cannot shut its output down, so it
 * is closed as soon as the response is out; the socket level behaviour is in
 * `ServerUnconsumedRequestBodySpec`.
 */
object LingeringCloseSpec extends ZIOHttpSpec {

  /**
   * Stands in for the request handler: records what is dispatched to it, and
   * gives up on the connection on the first part of a body.
   */
  private final class Dispatcher extends SimpleChannelInboundHandler[HttpObject] {
    val requests: mutable.Buffer[String] = mutable.Buffer.empty

    override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit =
      msg match {
        case request: HttpRequest                                 => requests += request.uri()
        case _: HttpContent if !msg.isInstanceOf[LastHttpContent] => LingeringClose(ctx.channel())
        case _                                                    =>
      }
  }

  /** Never completes a write, as with a client that does not read. */
  private final class StuckWrites extends ChannelOutboundHandlerAdapter {
    override def write(ctx: ChannelHandlerContext, msg: Any, promise: ChannelPromise): Unit = ()
  }

  private def ascii(s: String): Array[Byte] = s.getBytes(StandardCharsets.US_ASCII)

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("LingeringClose")(
      test("dispatches no request the decoder had already buffered") {
        val dispatcher = new Dispatcher
        val channel    = new EmbeddedChannel()
        channel.pipeline().addLast(Names.HttpRequestDecoder, new HttpRequestDecoder())
        channel.pipeline().addLast(Names.HttpRequestHandler, dispatcher)

        // A body of several decoder chunks, followed by a pipelined request, arriving in a single read.
        val bodySize = 20 * 1024
        channel.writeInbound(
          Unpooled.wrappedBuffer(
            ascii(s"POST /too-large HTTP/1.1\r\nHost: localhost\r\nContent-Length: $bodySize\r\n\r\n"),
            Array.fill[Byte](bodySize)('x'.toByte),
            ascii("GET /side-effect HTTP/1.1\r\nHost: localhost\r\n\r\n"),
          ),
        )
        channel.runPendingTasks()

        assertTrue(dispatcher.requests.toList == List("/too-large"), !channel.isOpen)
      },
      test("closes a connection whose response is never taken in, once its time is up") {
        val channel = new EmbeddedChannel()
        channel.freezeTime()
        channel.pipeline().addLast(new StuckWrites)

        LingeringClose(channel)
        channel.advanceTimeBy(LingeringClose.MaxLingerMillis - 1, TimeUnit.MILLISECONDS)
        channel.runPendingTasks()
        val openJustBefore = channel.isOpen
        channel.advanceTimeBy(1, TimeUnit.MILLISECONDS)
        channel.runPendingTasks()

        assertTrue(openJustBefore, !channel.isOpen)
      },
    )
}
