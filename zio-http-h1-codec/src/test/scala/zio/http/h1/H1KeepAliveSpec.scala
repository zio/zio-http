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

package zio.http.h1

import zio.blocks.chunk.Chunk
import zio.test._

object H1KeepAliveSpec extends ZIOSpecDefault {
  override def spec =
    suite("H1KeepAliveSpec")(
      test("parses 5000 sequential keep-alive requests with bounded retention") {
        val decoder     = new H1Decoder()
        var parsed      = 0
        var maxBuffered = 0
        var index       = 0
        var failed      = Option.empty[H1Error]
        while (index < 5000 && failed.isEmpty) {
          val raw = s"GET /r$index HTTP/1.1\r\nHost: x\r\n\r\n"
          decoder.feed(Chunk.fromArray(raw.getBytes("ISO-8859-1"))) match {
            case Right(msgs) =>
              parsed += msgs.length
              if (decoder.bufferedBytes > maxBuffered) maxBuffered = decoder.bufferedBytes
            case Left(error) => failed = Some(error)
          }
          index += 1
        }

        // Allocation observation (no optimization claimed): retention after the
        // sequence must be zero, peak buffered bytes must stay far below one
        // request burst, the backing array must not grow, and consumed prefixes
        // must have been compacted at least once.
        assertTrue(
          failed.isEmpty &&
            parsed == 5000 &&
            decoder.bufferedBytes == 0 &&
            maxBuffered < 4096 &&
            decoder.bufferCapacity <= 8192 &&
            decoder.compactions > 0,
        )
      },
      test("parses a pipelined burst of 100 requests from one feed") {
        val decoder = new H1Decoder()
        val burst   = List.fill(100)("GET /b HTTP/1.1\r\nHost: x\r\n\r\n").mkString
        val result  = decoder.feed(Chunk.fromArray(burst.getBytes("ISO-8859-1")))

        assertTrue(
          result.exists(_.length == 100) &&
            decoder.bufferedBytes == 0 &&
            decoder.compactions > 0,
        )
      },
      test("keeps parsing after a partial message split across keep-alive feeds") {
        val decoder = new H1Decoder()
        val first   = decoder.feed(wire("GET /1 HTTP/1.1\r\nHost: x\r\n\r\nGET /2 HTTP/1.1\r\nHost: x\r\n\r\nGET /3"))
        val second  = decoder.feed(wire(" HTTP/1.1\r\nHost: x\r\n\r\n"))

        assertTrue(
          first.exists(_.map(_.target) == List("/1", "/2")) &&
            second.exists(_.map(_.target) == List("/3")) &&
            decoder.bufferedBytes == 0,
        )
      },
    )

  private def wire(s: String): Chunk[Byte] =
    Chunk.fromArray(s.getBytes("ISO-8859-1"))
}
