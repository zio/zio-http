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

object H1EncoderSpec extends ZIOSpecDefault {
  override def spec =
    suite("H1EncoderSpec")(
      suite("roundtrip properties")(
        test("decode inverts encode over a method/target/header/body matrix") {
          val methods = List("GET", "POST", "PUT", "DELETE", "CUSTOM")
          val targets = List("/", "/a?b=c", "http://x/p", "*")
          val bodies  = List(Chunk.empty[Byte], wire("hello"))
          val checks  = for {
            method <- methods
            target <- targets
            body   <- bodies
          } yield {
            val headers = H1Headers(List(H1Header("Host", "x")))
            val framing = if (body == Chunk.empty) H1BodyFraming.Empty else H1BodyFraming.Fixed(body.length.toLong)
            val request = H1Request(method, target, headers, body, framing, H1Headers.Empty)
            val decoder = new H1Decoder()
            val decoded = H1Encoder.encodeRequest(request).flatMap(decoder.feed)
            decoded == Right(List(request.copy(headers = headersWithFraming(headers, framing))))
          }

          assertTrue(checks.forall(identity))
        },
        test("decode inverts encode for chunked requests with trailers") {
          val request = H1Request(
            method = "POST",
            target = "/u",
            headers = H1Headers(List(H1Header("Host", "x"), H1Header("Trailer", "X-Sum"))),
            body = wire("hello world"),
            framing = H1BodyFraming.Chunked,
            trailers = H1Headers(List(H1Header("X-Sum", "9"))),
          )
          val decoder = new H1Decoder()
          val decoded = H1Encoder.encodeRequest(request).flatMap(decoder.feed)

          assertTrue(decoded == Right(List(request.copy(headers = chunkedHeaders(request.headers)))))
        },
        test("decode inverts encode for responses") {
          val statuses = List(200, 204, 304, 404, 500)
          val checks   = statuses.map { status =>
            val bodyless = status == 204 || status == 304
            val response = H1Response(
              status = status,
              reason = "Reason",
              headers = if (bodyless) H1Headers.Empty else H1Headers(List(H1Header("Content-Length", "3"))),
              body = if (bodyless) Chunk.empty else wire("abc"),
              framing = if (bodyless) H1BodyFraming.Empty else H1BodyFraming.Fixed(3L),
              trailers = H1Headers.Empty,
            )
            val decoder  = new H1Decoder()
            H1Encoder.encodeResponse(response).flatMap(decoder.feedResponse(_, "GET")) match {
              case Right(List(decoded)) =>
                decoded.status == status && decoded.reason == "Reason" &&
                (if (bodyless) decoded.body == Chunk.empty else decoded.body == wire("abc"))
              case _                    => false
            }
          }

          assertTrue(checks.forall(identity))
        },
        test("encode is canonical: decode then encode reproduces the wire") {
          val wires  = List(
            "GET /a HTTP/1.1\r\nHost: x\r\n\r\n",
            "POST /s HTTP/1.1\r\nHost: x\r\nContent-Length: 5\r\n\r\nhello",
            "HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhello",
            "POST /u HTTP/1.1\r\nHost: x\r\nTrailer: X-S\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n0\r\nX-S: 1\r\n\r\n",
          )
          val checks = wires.map { raw =>
            val decoder = new H1Decoder()
            if (raw.startsWith("HTTP/")) {
              val result = for {
                msgs <- decoder.feedResponse(wire(raw), "GET")
                back <- msgs.headOption match {
                  case Some(response) => H1Encoder.encodeResponse(response)
                  case None           => Left(H1Error.InvalidMessage("expected response"))
                }
              } yield back
              result == Right(wire(raw))
            } else {
              val result = for {
                msgs <- decoder.feed(wire(raw))
                back <- msgs.headOption match {
                  case Some(request) => H1Encoder.encodeRequest(request)
                  case None          => Left(H1Error.InvalidMessage("expected request"))
                }
              } yield back
              result == Right(wire(raw))
            }
          }

          assertTrue(checks.forall(identity))
        },
      ),
      suite("exact wire shape")(
        test("encodes a chunked request with the exact chunk framing") {
          val request = H1Request(
            method = "POST",
            target = "/u",
            headers = H1Headers(List(H1Header("Host", "x"))),
            body = wire("hello"),
            framing = H1BodyFraming.Chunked,
            trailers = H1Headers(List(H1Header("X-Sum", "1"))),
          )

          assertTrue(
            H1Encoder.encodeRequest(request) ==
              Right(
                wire(
                  "POST /u HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n0\r\nX-Sum: 1\r\n\r\n",
                ),
              ),
          )
        },
        test("encodes a fixed-length response with the exact wire shape") {
          val response = H1Response(
            status = 200,
            reason = "OK",
            headers = H1Headers(List(H1Header("Content-Length", "5"))),
            body = wire("hello"),
            framing = H1BodyFraming.Fixed(5L),
            trailers = H1Headers.Empty,
          )

          assertTrue(
            H1Encoder.encodeResponse(response) ==
              Right(wire("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhello")),
          )
        },
      ),
      suite("encoder validation")(
        test("rejects a method containing whitespace") {
          val request = H1Request("GE T", "/", H1Headers.Empty, Chunk.empty, H1BodyFraming.Empty, H1Headers.Empty)

          assertTrue(isInvalid(H1Encoder.encodeRequest(request)))
        },
        test("rejects a target containing whitespace") {
          val request = H1Request("GET", "/a b", H1Headers.Empty, Chunk.empty, H1BodyFraming.Empty, H1Headers.Empty)

          assertTrue(isInvalid(H1Encoder.encodeRequest(request)))
        },
        test("rejects an invalid header name on encode") {
          val request = H1Request(
            "GET",
            "/",
            H1Headers(List(H1Header("bad name", "v"))),
            Chunk.empty,
            H1BodyFraming.Empty,
            H1Headers.Empty,
          )

          assertTrue(isInvalid(H1Encoder.encodeRequest(request)))
        },
        test("rejects a control character in a header value on encode") {
          val request = H1Request(
            "GET",
            "/",
            H1Headers(List(H1Header("X-A", "a\u0001b"))),
            Chunk.empty,
            H1BodyFraming.Empty,
            H1Headers.Empty,
          )

          assertTrue(isInvalid(H1Encoder.encodeRequest(request)))
        },
        test("rejects an out-of-range status on encode") {
          val response = H1Response(99, "X", H1Headers.Empty, Chunk.empty, H1BodyFraming.Empty, H1Headers.Empty)

          assertTrue(isInvalid(H1Encoder.encodeResponse(response)))
        },
        test("rejects a reason phrase with a control character on encode") {
          val response = H1Response(200, "O\u007fK", H1Headers.Empty, Chunk.empty, H1BodyFraming.Empty, H1Headers.Empty)

          assertTrue(isInvalid(H1Encoder.encodeResponse(response)))
        },
      ),
      suite("framing consistency rejections")(
        test("rejects duplicate Content-Length even when the values agree") {
          val request = H1Request(
            "POST",
            "/",
            H1Headers(List(H1Header("Host", "x"), H1Header("Content-Length", "5"), H1Header("Content-Length", "5"))),
            wire("hello"),
            H1BodyFraming.Fixed(5L),
            H1Headers.Empty,
          )

          assertTrue(isAmbiguous(H1Encoder.encodeRequest(request)))
        },
        test("rejects duplicate Transfer-Encoding") {
          val response = H1Response(
            200,
            "OK",
            H1Headers(
              List(H1Header("Transfer-Encoding", "chunked"), H1Header("Transfer-Encoding", "chunked")),
            ),
            wire("hello"),
            H1BodyFraming.Chunked,
            H1Headers.Empty,
          )

          assertTrue(isAmbiguous(H1Encoder.encodeResponse(response)))
        },
        test("rejects Content-Length combined with Transfer-Encoding") {
          val request = H1Request(
            "POST",
            "/",
            H1Headers(
              List(H1Header("Host", "x"), H1Header("Content-Length", "5"), H1Header("Transfer-Encoding", "chunked")),
            ),
            wire("hello"),
            H1BodyFraming.Chunked,
            H1Headers.Empty,
          )

          assertTrue(isAmbiguous(H1Encoder.encodeRequest(request)))
        },
        test("rejects Content-Length that disagrees with fixed framing") {
          val request = H1Request(
            "POST",
            "/",
            H1Headers(List(H1Header("Host", "x"), H1Header("Content-Length", "4"))),
            wire("hello"),
            H1BodyFraming.Fixed(5L),
            H1Headers.Empty,
          )

          assertTrue(isAmbiguous(H1Encoder.encodeRequest(request)))
        },
        test("rejects Content-Length with chunked framing") {
          val request = H1Request(
            "POST",
            "/",
            H1Headers(List(H1Header("Host", "x"), H1Header("Content-Length", "5"))),
            wire("hello"),
            H1BodyFraming.Chunked,
            H1Headers.Empty,
          )

          assertTrue(isAmbiguous(H1Encoder.encodeRequest(request)))
        },
        test("rejects Transfer-Encoding that is not exactly chunked") {
          val request = H1Request(
            "POST",
            "/",
            H1Headers(List(H1Header("Host", "x"), H1Header("Transfer-Encoding", "gzip"))),
            wire("hello"),
            H1BodyFraming.Chunked,
            H1Headers.Empty,
          )

          assertTrue(isAmbiguous(H1Encoder.encodeRequest(request)))
        },
        test("rejects Transfer-Encoding chunked with fixed framing") {
          val request = H1Request(
            "POST",
            "/",
            H1Headers(List(H1Header("Host", "x"), H1Header("Transfer-Encoding", "chunked"))),
            wire("hello"),
            H1BodyFraming.Fixed(5L),
            H1Headers.Empty,
          )

          assertTrue(isAmbiguous(H1Encoder.encodeRequest(request)))
        },
        test("rejects a response whose body length disagrees with fixed framing") {
          val response = H1Response(
            200,
            "OK",
            H1Headers(List(H1Header("Content-Length", "5"))),
            wire("hello!"),
            H1BodyFraming.Fixed(5L),
            H1Headers.Empty,
          )

          assertTrue(isInvalid(H1Encoder.encodeResponse(response)))
        },
        test("rejects Transfer-Encoding on a bodyless response") {
          val response = H1Response(
            204,
            "No Content",
            H1Headers(List(H1Header("Transfer-Encoding", "chunked"))),
            Chunk.empty,
            H1BodyFraming.Empty,
            H1Headers.Empty,
          )

          assertTrue(isAmbiguous(H1Encoder.encodeResponse(response)))
        },
        test("rejects Content-Length on a bodyless response") {
          val response = H1Response(
            304,
            "Not Modified",
            H1Headers(List(H1Header("Content-Length", "0"))),
            Chunk.empty,
            H1BodyFraming.Empty,
            H1Headers.Empty,
          )

          assertTrue(isAmbiguous(H1Encoder.encodeResponse(response)))
        },
        test("rejects chunked framing on a bodyless response") {
          val response = H1Response(
            204,
            "No Content",
            H1Headers.Empty,
            Chunk.empty,
            H1BodyFraming.Chunked,
            H1Headers.Empty,
          )

          assertTrue(isAmbiguous(H1Encoder.encodeResponse(response)))
        },
        test("rejects a response body when framing is Empty") {
          val response = H1Response(200, "OK", H1Headers.Empty, wire("hello"), H1BodyFraming.Empty, H1Headers.Empty)

          assertTrue(isInvalid(H1Encoder.encodeResponse(response)))
        },
        test("rejects a request body when framing is Empty") {
          val request = H1Request(
            "POST",
            "/",
            H1Headers(List(H1Header("Host", "x"))),
            wire("hello"),
            H1BodyFraming.Empty,
            H1Headers.Empty,
          )

          assertTrue(isInvalid(H1Encoder.encodeRequest(request)))
        },
      ),
      suite("trailer rejections")(
        test("rejects Content-Length inside the trailer section") {
          val request = H1Request(
            "POST",
            "/",
            H1Headers(List(H1Header("Host", "x"), H1Header("Transfer-Encoding", "chunked"))),
            wire("hello"),
            H1BodyFraming.Chunked,
            H1Headers(List(H1Header("Content-Length", "5"))),
          )

          assertTrue(isAmbiguous(H1Encoder.encodeRequest(request)))
        },
        test("rejects Transfer-Encoding inside the trailer section") {
          val request = H1Request(
            "POST",
            "/",
            H1Headers(List(H1Header("Host", "x"), H1Header("Transfer-Encoding", "chunked"))),
            wire("hello"),
            H1BodyFraming.Chunked,
            H1Headers(List(H1Header("Transfer-Encoding", "chunked"))),
          )

          assertTrue(isAmbiguous(H1Encoder.encodeRequest(request)))
        },
        test("rejects Trailer inside the trailer section") {
          val request = H1Request(
            "POST",
            "/",
            H1Headers(List(H1Header("Host", "x"), H1Header("Transfer-Encoding", "chunked"))),
            wire("hello"),
            H1BodyFraming.Chunked,
            H1Headers(List(H1Header("Trailer", "X-Sum"))),
          )

          assertTrue(isAmbiguous(H1Encoder.encodeRequest(request)))
        },
        test("rejects trailers without chunked framing") {
          val request = H1Request(
            "POST",
            "/",
            H1Headers(List(H1Header("Host", "x"), H1Header("Content-Length", "5"))),
            wire("hello"),
            H1BodyFraming.Fixed(5L),
            H1Headers(List(H1Header("X-Sum", "1"))),
          )

          assertTrue(isInvalid(H1Encoder.encodeRequest(request)))
        },
      ),
      suite("request Host validation")(
        test("rejects a request without a Host header") {
          val request = H1Request("GET", "/", H1Headers.Empty, Chunk.empty, H1BodyFraming.Empty, H1Headers.Empty)

          assertTrue(isInvalid(H1Encoder.encodeRequest(request)))
        },
        test("rejects a request with an empty Host header") {
          val request = H1Request(
            "GET",
            "/",
            H1Headers(List(H1Header("Host", ""))),
            Chunk.empty,
            H1BodyFraming.Empty,
            H1Headers.Empty,
          )

          assertTrue(isInvalid(H1Encoder.encodeRequest(request)))
        },
        test("rejects a request with multiple Host headers") {
          val request = H1Request(
            "GET",
            "/",
            H1Headers(List(H1Header("Host", "x"), H1Header("Host", "y"))),
            Chunk.empty,
            H1BodyFraming.Empty,
            H1Headers.Empty,
          )

          assertTrue(isInvalid(H1Encoder.encodeRequest(request)))
        },
      ),
      suite("valid messages")(
        test("accepts matching Content-Length and fixed framing") {
          val request = H1Request(
            "POST",
            "/u",
            H1Headers(List(H1Header("Host", "x"), H1Header("Content-Length", "5"))),
            wire("hello"),
            H1BodyFraming.Fixed(5L),
            H1Headers.Empty,
          )

          assertTrue(
            H1Encoder.encodeRequest(request) ==
              Right(wire("POST /u HTTP/1.1\r\nHost: x\r\nContent-Length: 5\r\n\r\nhello")),
          )
        },
        test("accepts Transfer-Encoding chunked with chunked framing") {
          val request = H1Request(
            "POST",
            "/u",
            H1Headers(List(H1Header("Host", "x"), H1Header("Transfer-Encoding", "chunked"))),
            wire("hello"),
            H1BodyFraming.Chunked,
            H1Headers.Empty,
          )

          assertTrue(
            H1Encoder.encodeRequest(request) ==
              Right(wire("POST /u HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n0\r\n\r\n")),
          )
        },
        test("accepts a request with a Host header") {
          val request = H1Request(
            "GET",
            "/",
            H1Headers(List(H1Header("Host", "x"))),
            Chunk.empty,
            H1BodyFraming.Empty,
            H1Headers.Empty,
          )

          assertTrue(H1Encoder.encodeRequest(request) == Right(wire("GET / HTTP/1.1\r\nHost: x\r\n\r\n")))
        },
        test("accepts announced trailers with chunked framing") {
          val request = H1Request(
            "POST",
            "/u",
            H1Headers(
              List(H1Header("Host", "x"), H1Header("Trailer", "X-Sum"), H1Header("Transfer-Encoding", "chunked")),
            ),
            wire("hello"),
            H1BodyFraming.Chunked,
            H1Headers(List(H1Header("X-Sum", "9"))),
          )

          assertTrue(
            H1Encoder.encodeRequest(request) ==
              Right(
                wire(
                  "POST /u HTTP/1.1\r\nHost: x\r\nTrailer: X-Sum\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n0\r\nX-Sum: 9\r\n\r\n",
                ),
              ),
          )
        },
        test("accepts HEAD-style Content-Length metadata with Empty framing") {
          val response = H1Response(
            200,
            "OK",
            H1Headers(List(H1Header("Content-Length", "5"))),
            Chunk.empty,
            H1BodyFraming.Empty,
            H1Headers.Empty,
          )

          assertTrue(
            H1Encoder.encodeResponse(response) == Right(wire("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\n")),
          )
        },
        test("accepts a bodyless response without framing headers") {
          val response =
            H1Response(204, "No Content", H1Headers.Empty, Chunk.empty, H1BodyFraming.Empty, H1Headers.Empty)

          assertTrue(
            H1Encoder.encodeResponse(response) == Right(wire("HTTP/1.1 204 No Content\r\n\r\n")),
          )
        },
      ),
    )

  private def wire(s: String): Chunk[Byte] =
    Chunk.fromArray(s.getBytes("ISO-8859-1"))

  private def headersWithFraming(headers: H1Headers, framing: H1BodyFraming): H1Headers =
    framing match {
      case H1BodyFraming.Empty    => headers
      case H1BodyFraming.Fixed(n) => H1Headers(headers.fields :+ H1Header("Content-Length", n.toString))
      case H1BodyFraming.Chunked  => chunkedHeaders(headers)
    }

  private def chunkedHeaders(headers: H1Headers): H1Headers =
    H1Headers(headers.fields :+ H1Header("Transfer-Encoding", "chunked"))

  private def isInvalid(result: Either[H1Error, Any]): Boolean =
    result match {
      case Left(_: H1Error.InvalidMessage) => true
      case _                               => false
    }

  private def isAmbiguous(result: Either[H1Error, Any]): Boolean =
    result match {
      case Left(_: H1Error.AmbiguousFraming) => true
      case _                                 => false
    }
}
