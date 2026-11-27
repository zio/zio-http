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

object H1RejectionSpec extends ZIOSpecDefault {
  override def spec =
    suite("H1RejectionSpec")(
      suite("framing ambiguity")(
        test("rejects Transfer-Encoding together with Content-Length") {
          assertIs[H1Error.AmbiguousFraming](
            "POST / HTTP/1.1\r\nHost: x\r\nContent-Length: 5\r\nTransfer-Encoding: chunked\r\n\r\n",
          )
        },
        test("rejects duplicated identical Content-Length") {
          assertIs[H1Error.AmbiguousFraming](
            "POST / HTTP/1.1\r\nHost: x\r\nContent-Length: 5\r\nContent-Length: 5\r\n\r\n",
          )
        },
        test("rejects conflicting Content-Length values") {
          assertIs[H1Error.AmbiguousFraming](
            "POST / HTTP/1.1\r\nHost: x\r\nContent-Length: 5\r\nContent-Length: 6\r\n\r\n",
          )
        },
        test("rejects duplicated Transfer-Encoding") {
          assertIs[H1Error.AmbiguousFraming](
            "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\nTransfer-Encoding: chunked\r\n\r\n",
          )
        },
        test("rejects unsupported transfer coding") {
          assertIs[H1Error.AmbiguousFraming](
            "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: gzip\r\n\r\n",
          )
        },
        test("rejects a transfer coding list that is not exactly chunked") {
          assertIs[H1Error.AmbiguousFraming](
            "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: gzip, chunked\r\n\r\n",
          )
        },
        test("rejects an empty Transfer-Encoding value") {
          assertIs[H1Error.AmbiguousFraming](
            "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding:\r\n\r\n",
          )
        },
        test("rejects a Trailer announcement without chunked framing") {
          assertIs[H1Error.AmbiguousFraming](
            "POST / HTTP/1.1\r\nHost: x\r\nContent-Length: 0\r\nTrailer: X-Sum\r\n\r\n",
          )
        },
        test("rejects Content-Length inside the trailer section") {
          assertIs[H1Error.AmbiguousFraming](
            "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nContent-Length: 5\r\n\r\n",
          )
        },
        test("rejects Transfer-Encoding inside the trailer section") {
          assertIs[H1Error.AmbiguousFraming](
            "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nTransfer-Encoding: chunked\r\n\r\n",
          )
        },
      ),
      suite("start line ambiguity")(
        test("rejects obs-fold continuation lines") {
          assertIs[H1Error.InvalidMessage](
            "GET / HTTP/1.1\r\nHost: x\r\n X-Folded: 1\r\n\r\n",
          )
        },
        test("rejects whitespace before the header colon") {
          assertIs[H1Error.InvalidMessage](
            "GET / HTTP/1.1\r\nHost : x\r\n\r\n",
          )
        },
        test("rejects whitespace inside the request target") {
          assertIs[H1Error.InvalidMessage](
            "GET /a b HTTP/1.1\r\nHost: x\r\n\r\n",
          )
        },
        test("rejects a control character in a header value") {
          assertIs[H1Error.InvalidMessage](
            "GET / HTTP/1.1\r\nHost: x\u0001\r\n\r\n",
          )
        },
        test("rejects a control character in a header name") {
          assertIs[H1Error.InvalidMessage](
            "GET / HTTP/1.1\r\nHo\u007fst: x\r\n\r\n",
          )
        },
        test("rejects bare LF line endings") {
          assertIs[H1Error.InvalidMessage](
            "GET / HTTP/1.1\nHost: x\n\n",
          )
        },
        test("rejects a lone CR in the request line") {
          assertIs[H1Error.InvalidMessage](
            "GET / HTTP/1.1\rHost: x\r\n\r\n",
          )
        },
        test("rejects HTTP/1.0 requests") {
          assertIs[H1Error.InvalidMessage](
            "GET / HTTP/1.0\r\nHost: x\r\n\r\n",
          )
        },
        test("rejects unknown versions") {
          assertIs[H1Error.InvalidMessage](
            "GET / HTTP/2\r\nHost: x\r\n\r\n",
          )
        },
        test("rejects an invalid method token") {
          assertIs[H1Error.InvalidMessage](
            "GE(T) / HTTP/1.1\r\nHost: x\r\n\r\n",
          )
        },
        test("rejects a request line with missing parts") {
          assertIs[H1Error.InvalidMessage](
            "GET /\r\nHost: x\r\n\r\n",
          )
        },
        test("rejects a request line with extra parts") {
          assertIs[H1Error.InvalidMessage](
            "GET / HTTP/1.1 extra\r\nHost: x\r\n\r\n",
          )
        },
        test("rejects a request without Host for origin-form targets") {
          assertIs[H1Error.InvalidMessage](
            "GET / HTTP/1.1\r\nX-A: 1\r\n\r\n",
          )
        },
        test("rejects out-of-range status codes") {
          assertTrue(
            isResponse[H1Error.InvalidMessage]("HTTP/1.1 099 X\r\n\r\n") &&
              isResponse[H1Error.InvalidMessage]("HTTP/1.1 600 X\r\n\r\n") &&
              isResponse[H1Error.InvalidMessage]("HTTP/1.1 20 X\r\n\r\n") &&
              isResponse[H1Error.InvalidMessage]("HTTP/1.1 abc X\r\n\r\n") &&
              isResponse[H1Error.InvalidMessage]("HTTP/1.1 2000 X\r\n\r\n"),
          )
        },
        test("rejects a status line missing the space after the code") {
          assertIs[H1Error.InvalidMessage](
            "HTTP/1.1 200OK\r\n\r\n",
          )
        },
        test("rejects a reason phrase with control characters") {
          assertIs[H1Error.InvalidMessage](
            "HTTP/1.1 200 O\u0001K\r\n\r\n",
          )
        },
      ),
      suite("content framing")(
        test("rejects a negative Content-Length") {
          assertIs[H1Error.InvalidMessage](
            "POST / HTTP/1.1\r\nHost: x\r\nContent-Length: -5\r\n\r\n",
          )
        },
        test("rejects a non-numeric Content-Length") {
          assertIs[H1Error.InvalidMessage](
            "POST / HTTP/1.1\r\nHost: x\r\nContent-Length: 12a\r\n\r\n",
          )
        },
        test("rejects an empty Content-Length") {
          assertIs[H1Error.InvalidMessage](
            "POST / HTTP/1.1\r\nHost: x\r\nContent-Length:\r\n\r\n",
          )
        },
        test("rejects Content-Length with inner whitespace") {
          assertIs[H1Error.InvalidMessage](
            "POST / HTTP/1.1\r\nHost: x\r\nContent-Length: 1 0\r\n\r\n",
          )
        },
        test("rejects an overflowing Content-Length") {
          assertIs[H1Error.InvalidMessage](
            "POST / HTTP/1.1\r\nHost: x\r\nContent-Length: 99999999999999999999999\r\n\r\n",
          )
        },
        test("rejects a non-hex chunk size") {
          assertIs[H1Error.InvalidMessage](
            "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\nxyz\r\n",
          )
        },
        test("rejects an empty chunk-size line") {
          assertIs[H1Error.InvalidMessage](
            "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n\r\n",
          )
        },
        test("rejects a negative chunk size") {
          assertIs[H1Error.InvalidMessage](
            "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n-5\r\n",
          )
        },
        test("rejects chunk extensions") {
          assertIs[H1Error.InvalidMessage](
            "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n5;ext=x\r\nhello\r\n0\r\n\r\n",
          )
        },
        test("rejects an overflowing chunk size") {
          assertIs[H1Error.InvalidMessage](
            "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\nFFFFFFFFFFFFFFFFFFFF\r\n",
          )
        },
        test("rejects chunk data with a corrupt terminator") {
          assertIs[H1Error.InvalidMessage](
            "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhelloXX\r\n0\r\n\r\n",
          )
        },
        test("rejects a last-chunk with extensions") {
          assertIs[H1Error.InvalidMessage](
            "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n0;ext=x\r\n\r\n",
          )
        },
        test("rejects garbage where a new message should start") {
          assertIs[H1Error.InvalidMessage](
            "GET / HTTP/1.1\r\nHost: x\r\n\r\nGARBAGE!!!\r\n\r\n",
          )
        },
        test("rejects a comma-joined Content-Length") {
          assertIs[H1Error.InvalidMessage](
            "POST / HTTP/1.1\r\nHost: x\r\nContent-Length: 5, 5\r\n\r\n",
          )
        },
      ),
      suite("request host identity")(
        test("rejects multiple Host headers") {
          assertIs[H1Error.InvalidMessage](
            "GET / HTTP/1.1\r\nHost: a\r\nHost: b\r\n\r\n",
          )
        },
        test("rejects multiple Host headers with an absolute-form target") {
          assertIs[H1Error.InvalidMessage](
            "GET http://a.com/x HTTP/1.1\r\nHost: a.com\r\nHost: a.com\r\n\r\n",
          )
        },
        test("rejects a Host that conflicts with the absolute-form authority") {
          assertIs[H1Error.InvalidMessage](
            "GET http://a.com/x HTTP/1.1\r\nHost: b.com\r\n\r\n",
          )
        },
        test("rejects a Host port that conflicts with the absolute-form port") {
          assertIs[H1Error.InvalidMessage](
            "GET http://a.com:8080/x HTTP/1.1\r\nHost: a.com\r\n\r\n",
          )
        },
        test("rejects a Host port that conflicts with the https scheme default") {
          assertIs[H1Error.InvalidMessage](
            "GET https://a.com/x HTTP/1.1\r\nHost: a.com:80\r\n\r\n",
          )
        },
        test("accepts a Host that matches the absolute-form authority case-insensitively") {
          assertTrue(accepts("GET http://a.com/x HTTP/1.1\r\nHost: A.COM\r\n\r\n"))
        },
        test("accepts a Host port equal to the scheme default") {
          assertTrue(
            accepts("GET http://a.com/x HTTP/1.1\r\nHost: a.com:80\r\n\r\n") &&
              accepts("GET https://a.com/x HTTP/1.1\r\nHost: a.com:443\r\n\r\n"),
          )
        },
      ),
      suite("trailer announcement")(
        test("rejects a trailer field when nothing is announced") {
          assertIs[H1Error.AmbiguousFraming](
            "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nX-Other: 1\r\n\r\n",
          )
        },
        test("rejects a trailer field missing from the announced set") {
          assertIs[H1Error.AmbiguousFraming](
            "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\nTrailer: X-Sum\r\n\r\n0\r\nX-Other: 1\r\n\r\n",
          )
        },
        test("accepts an announced trailer field") {
          assertTrue(
            accepts(
              "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\nTrailer: X-Sum\r\n\r\n0\r\nX-Sum: 1\r\n\r\n",
            ),
          )
        },
        test("accepts an announcement whose trailer never arrives") {
          assertTrue(
            accepts("POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\nTrailer: X-Sum\r\n\r\n0\r\n\r\n"),
          )
        },
        test("accepts a comma-joined announcement covering the received fields") {
          assertTrue(
            accepts(
              "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\nTrailer: X-A, X-B\r\n\r\n0\r\nX-A: 1\r\nX-B: 2\r\n\r\n",
            ),
          )
        },
      ),
      suite("non-ASCII bytes")(
        test("rejects a high byte in a header value") {
          assertIs[H1Error.InvalidMessage](
            "GET / HTTP/1.1\r\nHost: x\r\nX-A: \u00ff\r\n\r\n",
          )
        },
        test("rejects a high byte in the request target") {
          assertIs[H1Error.InvalidMessage](
            "GET /\u00ff HTTP/1.1\r\nHost: x\r\n\r\n",
          )
        },
      ),
      suite("Expect handling")(
        test("rejects an unsupported Expect token") {
          assertIs[H1Error.UnsupportedExpectation](
            "POST / HTTP/1.1\r\nHost: x\r\nContent-Length: 5\r\nExpect: 100-continue-please\r\n\r\nhello",
          )
        },
        test("rejects multiple Expect headers") {
          assertIs[H1Error.UnsupportedExpectation](
            "POST / HTTP/1.1\r\nHost: x\r\nContent-Length: 5\r\nExpect: 100-continue\r\nExpect: 100-continue\r\n\r\nhello",
          )
        },
        test("rejects an empty Expect value") {
          assertIs[H1Error.UnsupportedExpectation](
            "POST / HTTP/1.1\r\nHost: x\r\nContent-Length: 5\r\nExpect:\r\n\r\nhello",
          )
        },
        test("accepts a legal Expect on a bodyless request") {
          assertTrue(accepts("GET / HTTP/1.1\r\nHost: x\r\nExpect: 100-continue\r\n\r\n"))
        },
      ),
      suite("reuse poisoning and close classification")(
        test("poisons the decoder after a fatal error") {
          val decoder = new H1Decoder()
          val first   = decoder.feed(wire("GET / HTTP/1.0\r\nHost: x\r\n\r\n"))
          val second  = decoder.feed(wire("GET / HTTP/1.1\r\nHost: x\r\n\r\n"))

          assertTrue(
            is[H1Error.InvalidMessage](first) &&
              is[H1Error.DecoderPoisoned](second) &&
              decoder.poisoned &&
              decoder.bufferedBytes == 0,
          )
        },
        test("poisons response parsing after a fatal error") {
          val decoder = new H1Decoder()
          val first   = decoder.feedResponse(wire("NOPE\r\n\r\n"), "GET")
          val second  = decoder.feedResponse(wire("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n"), "GET")

          assertTrue(is[H1Error.InvalidMessage](first) && is[H1Error.DecoderPoisoned](second))
        },
        test("classifies every corpus rejection as must-close") {
          val wires  = List(
            "POST / HTTP/1.1\r\nHost: x\r\nContent-Length: 5\r\nTransfer-Encoding: chunked\r\n\r\n",
            "POST / HTTP/1.1\r\nHost: x\r\nContent-Length: 5\r\nContent-Length: 6\r\n\r\n",
            "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: gzip\r\n\r\n",
            "GET / HTTP/1.1\r\nHost: x\r\n X: 1\r\n\r\n",
            "GET / HTTP/1.0\r\nHost: x\r\n\r\n",
            "GET / HTTP/1.1\nHost: x\n\n",
            "POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\nxyz\r\n",
            "POST / HTTP/1.1\r\nHost: x\r\nContent-Length: abc\r\n\r\n",
            "GET / HTTP/1.1\r\nX-A: 1\r\n\r\n",
          )
          val errors = wires.map { raw =>
            val decoder = new H1Decoder()
            decoder.feed(wire(raw)) match {
              case Left(error) => error
              case Right(_)    => null
            }
          }

          assertTrue(errors.forall(_ != null) && errors.forall(_.mustClose))
        },
      ),
    )

  private def wire(s: String): Chunk[Byte] =
    Chunk.fromArray(s.getBytes("ISO-8859-1"))

  private def is[E <: H1Error](result: Either[H1Error, Any])(implicit tag: scala.reflect.ClassTag[E]): Boolean =
    result match {
      case Left(error) if tag.runtimeClass.isInstance(error) => true
      case _                                                 => false
    }

  private def assertIs[E <: H1Error](raw: String)(implicit tag: scala.reflect.ClassTag[E]): zio.test.TestResult = {
    val decoder = new H1Decoder()
    assertTrue(is[E](decoder.feed(wire(raw))))
  }

  private def accepts(raw: String): Boolean = {
    val decoder = new H1Decoder()
    decoder.feed(wire(raw)).exists(_.length == 1)
  }

  private def isResponse[E <: H1Error](raw: String)(implicit tag: scala.reflect.ClassTag[E]): Boolean = {
    val decoder = new H1Decoder()
    is[E](decoder.feedResponse(wire(raw), "GET"))
  }
}
