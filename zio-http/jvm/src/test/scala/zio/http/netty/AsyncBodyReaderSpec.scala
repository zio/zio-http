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

import java.util.concurrent.atomic.AtomicInteger

import zio._
import zio.test._

import zio.http.ZIOHttpSpec

import io.netty.buffer.Unpooled
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.channel.{ChannelHandlerContext, ChannelOutboundHandlerAdapter}
import io.netty.handler.codec.http.DefaultHttpContent

/**
 * Regression test for the pre-connect Buffering window in `AsyncBodyReader`.
 *
 * Before any consumer calls `connect`, the reader buffers incoming body chunks
 * on the heap and asks Netty for more (`ctx.read()`). Without a cap, a fast
 * producer (e.g. a localhost upload) can fill the heap during the few
 * milliseconds it takes the route handler to call `request.body.asStream` and
 * start pulling. See issue #3173.
 *
 * `EmbeddedChannel.writeInbound` ignores `autoRead`, so we cannot verify the
 * fix by checking that buffered bytes stay below the cap. Instead we count the
 * outbound `read()` requests AsyncBodyReader makes — the cap should make those
 * calls stop, which is what enforces TCP back-pressure on a real socket.
 */
object AsyncBodyReaderSpec extends ZIOHttpSpec {

  private final class TestReader(maxPreConnectBufferSize: Int)
      extends AsyncBodyReader(timeoutMillis = None, maxPreConnectBufferSize = maxPreConnectBufferSize)

  /** Outbound handler placed before AsyncBodyReader to count `ctx.read()`. */
  private final class ReadCounter extends ChannelOutboundHandlerAdapter {
    val count = new AtomicInteger(0)
    override def read(ctx: ChannelHandlerContext): Unit = {
      count.incrementAndGet()
      ctx.read(): Unit
    }
  }

  private def setup(cap: Int): (ReadCounter, EmbeddedChannel) = {
    val counter = new ReadCounter
    val ch      = new EmbeddedChannel(new TestReader(maxPreConnectBufferSize = cap))
    // addFirst places counter at the head of the pipeline, so outbound `read`
    // events from AsyncBodyReader pass through it on their way to the channel.
    ch.pipeline().addFirst(counter): Unit
    (counter, ch)
  }

  private def feed(ch: EmbeddedChannel, chunkBytes: Int, count: Int): Unit = {
    val payload = new Array[Byte](chunkBytes)
    var i       = 0
    while (i < count) {
      ch.writeInbound(new DefaultHttpContent(Unpooled.wrappedBuffer(payload))): Unit
      i += 1
    }
  }

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("AsyncBodyReader")(
      test("Buffering state stops requesting reads once pre-connect cap is reached") {
        ZIO.attempt {
          val cap            = 64 * 1024
          val chunkSz        = 8 * 1024
          val (counter, ch)  = setup(cap)
          // Feed 100 chunks of 8 KB = 800 KB, far above the 64 KB cap.
          feed(ch, chunkBytes = chunkSz, count = 100)

          // Without the fix, AsyncBodyReader would request a read after every
          // chunk (~100 reads). With the cap, it stops once bufferedBytes
          // reaches the cap — that's after ceil(cap / chunkSz) = 8 chunks.
          val reads = counter.count.get()
          assertTrue(reads <= cap / chunkSz, reads < 100)
        }
      },
      test("Buffering state keeps requesting reads while under cap") {
        ZIO.attempt {
          val cap            = 64 * 1024
          val chunkSz        = 1024
          val (counter, ch)  = setup(cap)
          feed(ch, chunkBytes = chunkSz, count = 4)
          // 4 KB buffered, well below 64 KB cap → reader still asking for more.
          val reads = counter.count.get()
          assertTrue(reads == 4)
        }
      },
    )
}
