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

object H1LimitsSpec extends ZIOSpecDefault {
  private val tiny = H1Limits(
    maxRequestLineLength = 16,
    maxStatusLineLength = 16,
    maxHeaderSectionBytes = 32,
    maxHeaderCount = 2,
    maxHeaderNameLength = 4,
    maxHeaderValueLength = 8,
    maxChunkLineLength = 4,
    maxTrailerSectionBytes = 16,
    maxTrailerCount = 1,
    maxUndecodedBytes = 64,
  )

  private val roomyHeaders = tiny.copy(
    maxHeaderSectionBytes = 256,
    maxHeaderCount = 8,
    maxHeaderNameLength = 32,
    maxHeaderValueLength = 32,
  )
  override def spec        =
    suite("H1LimitsSpec")(
      test("rejects an overlong request line") {
        val decoder = new H1Decoder(tiny)

        assertTrue(is[H1Error.LineTooLong](decoder.feed(wire("GET /way-too-long-target HTTP/1.1\r\nHost: x\r\n\r\n"))))
      },
      test("rejects an overlong status line") {
        val decoder = new H1Decoder(tiny)

        assertTrue(
          is[H1Error.LineTooLong](decoder.feedResponse(wire("HTTP/1.1 200 OK-with-a-long-reason\r\n\r\n"), "GET")),
        )
      },
      test("rejects an overlarge header section") {
        val decoder = new H1Decoder(tiny.copy(maxHeaderSectionBytes = 20))

        assertTrue(
          is[H1Error.HeaderSectionTooLarge](
            decoder.feed(wire("GET / HTTP/1.1\r\nHost: x\r\nA: 12345678\r\n\r\n")),
          ),
        )
      },
      test("rejects too many headers") {
        val decoder = new H1Decoder(tiny)

        assertTrue(
          is[H1Error.TooManyHeaders](
            decoder.feed(wire("GET / HTTP/1.1\r\nHost: x\r\nA: 1\r\nB: 2\r\nC: 3\r\n\r\n")),
          ),
        )
      },
      test("rejects an overlong header name") {
        val decoder = new H1Decoder(tiny)

        assertTrue(
          is[H1Error.HeaderNameTooLong](
            decoder.feed(wire("GET / HTTP/1.1\r\nLong-Name: v\r\n\r\n")),
          ),
        )
      },
      test("rejects an overlong header value") {
        val decoder = new H1Decoder(tiny)

        assertTrue(
          is[H1Error.HeaderValueTooLong](
            decoder.feed(wire("GET / HTTP/1.1\r\nA: 123456789\r\n\r\n")),
          ),
        )
      },
      test("rejects an overlong chunk-size line") {
        val decoder = new H1Decoder(roomyHeaders)
        val raw     = "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n" + "F" * 8 + "\r\n"

        assertTrue(is[H1Error.LineTooLong](decoder.feed(wire(raw))))
      },
      test("rejects an overlarge trailer section") {
        val decoder = new H1Decoder(roomyHeaders)
        val raw = "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nX-A: 1234567890123456\r\n\r\n"

        assertTrue(is[H1Error.TrailerSectionTooLarge](decoder.feed(wire(raw))))
      },
      test("rejects too many trailers") {
        val decoder = new H1Decoder(roomyHeaders)
        val raw     = "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nA: 1\r\nB: 2\r\n\r\n"

        assertTrue(is[H1Error.TooManyTrailers](decoder.feed(wire(raw))))
      },
      test("rejects a single feed larger than the retention cap") {
        // The request-line cap is enforced mid-scan now, so lift it (as the
        // cross-feed sibling below already does) to isolate the retention
        // bound: unterminated garbage past maxRequestLineLength is rejected as
        // LineTooLong before retention is ever consulted.
        val decoder = new H1Decoder(tiny.copy(maxRequestLineLength = 128))
        val raw     = "X" * 65

        assertTrue(is[H1Error.RetentionOverflow](decoder.feed(wire(raw))))
      },
      test("rejects an unterminated request line as soon as it exceeds the line cap") {
        val decoder = new H1Decoder(tiny)
        val raw     = "X" * 17

        assertTrue(is[H1Error.LineTooLong](decoder.feed(wire(raw))) && decoder.poisoned)
      },
      test("rejects retention overflow accumulated across feeds") {
        val decoder = new H1Decoder(tiny.copy(maxRequestLineLength = 128))
        val first   = decoder.feed(wire("X" * 40))
        val second  = decoder.feed(wire("Y" * 40))

        assertTrue(first == Right(Nil) && is[H1Error.RetentionOverflow](second))
      },
      test("rejects a declared body larger than the retention cap") {
        val decoder = new H1Decoder(roomyHeaders)

        assertTrue(
          is[H1Error.BodyTooLarge](
            decoder.feed(wire("POST / HTTP/1.1\r\nHost: x\r\nContent-Length: 1000\r\n\r\n")),
          ),
        )
      },
      test("rejects a chunk size larger than the retention cap") {
        val decoder = new H1Decoder(roomyHeaders)
        val raw     = "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\nFFF\r\n"

        assertTrue(is[H1Error.BodyTooLarge](decoder.feed(wire(raw))))
      },
      test("accepts a start line of exactly the maximum length") {
        val limits  = tiny.copy(maxRequestLineLength = 14)
        val decoder = new H1Decoder(limits)
        // "GET / HTTP/1.1" is exactly 14 bytes
        val result  = decoder.feed(wire("GET / HTTP/1.1\r\nHost: x\r\n\r\n"))

        assertTrue(result.exists(_.length == 1))
      },
    )

  private def wire(s: String): Chunk[Byte] =
    Chunk.fromArray(s.getBytes("ISO-8859-1"))

  private def is[E <: H1Error](result: Either[H1Error, Any])(implicit tag: scala.reflect.ClassTag[E]): Boolean =
    result match {
      case Left(error) if tag.runtimeClass.isInstance(error) => true
      case _                                                 => false
    }
}
