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

object H1DecoderSpec extends ZIOSpecDefault {
  override def spec =
    suite("H1DecoderSpec")(
      suite("valid requests")(
        test("decodes a simple GET request") {
          val decoder = new H1Decoder()
          val result  = decoder.feed(wire("GET /hello HTTP/1.1\r\nHost: example.com\r\n\r\n"))

          assertTrue(
            result == Right(
              List(
                H1Request(
                  method = "GET",
                  target = "/hello",
                  headers = H1Headers(List(H1Header("Host", "example.com"))),
                  body = Chunk.empty,
                  framing = H1BodyFraming.Empty,
                  trailers = H1Headers.Empty,
                ),
              ),
            ),
          )
        },
        test("decodes a POST with Content-Length") {
          val decoder = new H1Decoder()
          val result  = decoder.feed(wire("POST /submit HTTP/1.1\r\nHost: x\r\nContent-Length: 5\r\n\r\nhello"))

          assertTrue(
            result == Right(
              List(
                H1Request(
                  method = "POST",
                  target = "/submit",
                  headers = H1Headers(List(H1Header("Host", "x"), H1Header("Content-Length", "5"))),
                  body = wire("hello"),
                  framing = H1BodyFraming.Fixed(5L),
                  trailers = H1Headers.Empty,
                ),
              ),
            ),
          )
        },
        test("decodes a chunked request with trailers") {
          val decoder = new H1Decoder()
          val result  = decoder.feed(
            wire(
              "POST /upload HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\nTrailer: X-Sum\r\n\r\n5\r\nhello\r\n0\r\nX-Sum: 1\r\n\r\n",
            ),
          )

          assertTrue(
            result == Right(
              List(
                H1Request(
                  method = "POST",
                  target = "/upload",
                  headers = H1Headers(
                    List(H1Header("Host", "x"), H1Header("Transfer-Encoding", "chunked"), H1Header("Trailer", "X-Sum")),
                  ),
                  body = wire("hello"),
                  framing = H1BodyFraming.Chunked,
                  trailers = H1Headers(List(H1Header("X-Sum", "1"))),
                ),
              ),
            ),
          )
        },
        test("trims optional whitespace around header values") {
          val decoder = new H1Decoder()
          val result  = decoder.feed(wire("GET / HTTP/1.1\r\nHost:   example.com  \t\r\nX-A:\r\n\r\n"))

          assertTrue(
            result == Right(
              List(
                H1Request(
                  method = "GET",
                  target = "/",
                  headers = H1Headers(List(H1Header("Host", "example.com"), H1Header("X-A", ""))),
                  body = Chunk.empty,
                  framing = H1BodyFraming.Empty,
                  trailers = H1Headers.Empty,
                ),
              ),
            ),
          )
        },
        test("ignores a single leading CRLF before the request line") {
          val decoder = new H1Decoder()
          val result  = decoder.feed(wire("\r\nGET / HTTP/1.1\r\nHost: x\r\n\r\n"))

          assertTrue(result.exists(_.length == 1) && result.exists(_.head.method == "GET"))
        },
        test("accepts absolute-form target without Host") {
          val decoder = new H1Decoder()
          val result  = decoder.feed(wire("GET http://example.com/y HTTP/1.1\r\n\r\n"))

          assertTrue(result.exists(_.length == 1) && result.exists(_.head.target == "http://example.com/y"))
        },
        test("parses two keep-alive requests from one feed") {
          val decoder = new H1Decoder()
          val one     = "GET /a HTTP/1.1\r\nHost: x\r\n\r\n"
          val result  = decoder.feed(wire(one + one))

          assertTrue(
            result.exists(_.length == 2) &&
              result.exists(_.map(_.target) == List("/a", "/a")) &&
              decoder.bufferedBytes == 0,
          )
        },
        test("waits for the full body across feeds") {
          val decoder = new H1Decoder()
          val first   = decoder.feed(wire("POST /s HTTP/1.1\r\nHost: x\r\nContent-Length: 5\r\n\r\nhel"))
          val second  = decoder.feed(wire("lo"))

          assertTrue(first == Right(Nil) && second.exists(_.length == 1) && second.exists(_.head.body == wire("hello")))
        },
        test("empty feed is a no-op") {
          val decoder = new H1Decoder()
          val result  = decoder.feed(Chunk.empty)

          assertTrue(result == Right(Nil) && decoder.bufferedBytes == 0)
        },
      ),
      suite("valid responses")(
        test("decodes a response with Content-Length") {
          val decoder = new H1Decoder()
          val result  = decoder.feedResponse(wire("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhello"), "GET")

          assertTrue(
            result == Right(
              List(
                H1Response(
                  status = 200,
                  reason = "OK",
                  headers = H1Headers(List(H1Header("Content-Length", "5"))),
                  body = wire("hello"),
                  framing = H1BodyFraming.Fixed(5L),
                  trailers = H1Headers.Empty,
                ),
              ),
            ),
          )
        },
        test("decodes a HEAD response without consuming a body") {
          val decoder = new H1Decoder()
          val result  = decoder.feedResponse(wire("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\n"), "HEAD")

          assertTrue(
            result.exists(_.length == 1) &&
              result.exists(_.head.body == Chunk.empty) &&
              result.exists(_.head.framing == H1BodyFraming.Empty),
          )
        },
        test("ignores Content-Length on a 204 response") {
          val decoder = new H1Decoder()
          val result  = decoder.feedResponse(wire("HTTP/1.1 204 No Content\r\nContent-Length: 5\r\n\r\n"), "GET")

          assertTrue(
            result.exists(_.length == 1) &&
              result.exists(_.head.body == Chunk.empty) &&
              result.exists(_.head.framing == H1BodyFraming.Empty),
          )
        },
        test("decodes an interim 1xx followed by the final response") {
          val decoder = new H1Decoder()
          val result  = decoder.feedResponse(
            wire("HTTP/1.1 103 Early Hints\r\nLink: </s>\r\n\r\nHTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n"),
            "GET",
          )

          assertTrue(
            result.exists(_.length == 2) &&
              result.exists(_.map(_.status) == List(103, 200)),
          )
        },
        test("decodes a chunked response") {
          val decoder = new H1Decoder()
          val result  = decoder.feedResponse(
            wire("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n0\r\n\r\n"),
            "GET",
          )

          assertTrue(
            result.exists(_.length == 1) && result.exists(_.head.body == wire("hello")),
          )
        },
        test("rejects a HEAD response with chunked framing") {
          val decoder = new H1Decoder()
          val result  = decoder.feedResponse(
            wire("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n"),
            "HEAD",
          )

          assertTrue(isError[H1Error.AmbiguousFraming](result))
        },
        test("rejects a 204 response with chunked framing") {
          val decoder = new H1Decoder()
          val result  = decoder.feedResponse(
            wire("HTTP/1.1 204 No Content\r\nTransfer-Encoding: chunked\r\n\r\n"),
            "GET",
          )

          assertTrue(isError[H1Error.AmbiguousFraming](result))
        },
      ),
      suite("byte segmentation")(
        test("parses a GET under every byte split") {
          val bytes  = wire("GET /hello HTTP/1.1\r\nHost: example.com\r\nX-A: 1\r\n\r\n")
          val checks = (0 to bytes.length).map { split =>
            val decoder = new H1Decoder()
            val result  = for {
              a <- decoder.feed(bytes.take(split))
              b <- decoder.feed(bytes.drop(split))
            } yield a ++ b
            result.exists(_.length == 1) &&
            result.exists(_.head.method == "GET") &&
            result.exists(_.head.target == "/hello") &&
            decoder.bufferedBytes == 0
          }

          assertTrue(checks.forall(identity))
        },
        test("parses a Content-Length POST under every byte split") {
          val bytes  = wire("POST /s HTTP/1.1\r\nHost: x\r\nContent-Length: 11\r\n\r\nhello world")
          val checks = (0 to bytes.length).map { split =>
            val decoder = new H1Decoder()
            val result  = for {
              a <- decoder.feed(bytes.take(split))
              b <- decoder.feed(bytes.drop(split))
            } yield a ++ b
            result.exists(_.length == 1) && result.exists(_.head.body == wire("hello world"))
          }

          assertTrue(checks.forall(identity))
        },
        test("parses a chunked message with trailers under every byte split") {
          val bytes  = wire(
            "POST /u HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\nTrailer: X-Sum\r\n\r\na\r\nhello worl\r\n2\r\nd!\r\n0\r\nX-Sum: 9\r\n\r\n",
          )
          val checks = (0 to bytes.length).map { split =>
            val decoder = new H1Decoder()
            val result  = for {
              a <- decoder.feed(bytes.take(split))
              b <- decoder.feed(bytes.drop(split))
            } yield a ++ b
            result.exists(_.length == 1) &&
            result.exists(_.head.body == wire("hello world!")) &&
            result.exists(_.head.trailers == H1Headers(List(H1Header("X-Sum", "9"))))
          }

          assertTrue(checks.forall(identity))
        },
        test("parses a response under every byte split") {
          val bytes  = wire("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nhello")
          val checks = (0 to bytes.length).map { split =>
            val decoder = new H1Decoder()
            val result  = for {
              a <- decoder.feedResponse(bytes.take(split), "GET")
              b <- decoder.feedResponse(bytes.drop(split), "GET")
            } yield a ++ b
            result.exists(_.length == 1) && result.exists(_.head.body == wire("hello"))
          }

          assertTrue(checks.forall(identity))
        },
        test("parses a GET fed one byte at a time") {
          val bytes   = wire("GET /one HTTP/1.1\r\nHost: x\r\n\r\n")
          val decoder = new H1Decoder()
          var emitted = List.empty[H1Request]
          var index   = 0
          var failed  = Option.empty[H1Error]
          while (index < bytes.length && failed.isEmpty && emitted.isEmpty) {
            decoder.feed(bytes.slice(index, index + 1)) match {
              case Right(msgs) => emitted = emitted ++ msgs
              case Left(error) => failed = Some(error)
            }
            index += 1
          }

          assertTrue(failed.isEmpty && emitted.length == 1 && emitted.head.target == "/one")
        },
      ),
      suite("Expect: 100-continue hook")(
        test("fires the hook once for a fixed-length body") {
          var fired   = List.empty[H1Headers]
          val decoder = new H1Decoder(onHeadersComplete = headers => fired = fired :+ headers)
          val result  =
            decoder.feed(wire("POST /s HTTP/1.1\r\nHost: x\r\nExpect: 100-continue\r\nContent-Length: 5\r\n\r\nhello"))

          assertTrue(
            result.exists(_.length == 1) &&
              fired.length == 1 &&
              fired.head == H1Headers(
                List(H1Header("Host", "x"), H1Header("Expect", "100-continue"), H1Header("Content-Length", "5")),
              ),
          )
        },
        test("fires the hook once for a chunked body") {
          var fired   = List.empty[H1Headers]
          val decoder = new H1Decoder(onHeadersComplete = headers => fired = fired :+ headers)
          val result  = decoder.feed(
            wire(
              "POST /u HTTP/1.1\r\nHost: x\r\nExpect: 100-continue\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n0\r\n\r\n",
            ),
          )

          assertTrue(result.exists(_.length == 1) && fired.length == 1)
        },
        test("does not fire the hook for bodyless requests with Expect") {
          var fired   = List.empty[H1Headers]
          val decoder = new H1Decoder(onHeadersComplete = headers => fired = fired :+ headers)
          val first   = decoder.feed(wire("GET / HTTP/1.1\r\nHost: x\r\nExpect: 100-continue\r\n\r\n"))
          val second  =
            decoder.feed(wire("POST /z HTTP/1.1\r\nHost: x\r\nExpect: 100-continue\r\nContent-Length: 0\r\n\r\n"))

          assertTrue(first.exists(_.length == 1) && second.exists(_.length == 1) && fired.isEmpty)
        },
        test("does not fire the hook without an Expect header") {
          var fired   = List.empty[H1Headers]
          val decoder = new H1Decoder(onHeadersComplete = headers => fired = fired :+ headers)
          val result  = decoder.feed(wire("POST /s HTTP/1.1\r\nHost: x\r\nContent-Length: 5\r\n\r\nhello"))

          assertTrue(result.exists(_.length == 1) && fired.isEmpty)
        },
        test("fires the hook exactly once under byte-at-a-time feeding") {
          val bytes   = wire("POST /s HTTP/1.1\r\nHost: x\r\nExpect: 100-continue\r\nContent-Length: 5\r\n\r\nhello")
          var fired   = List.empty[H1Headers]
          val decoder = new H1Decoder(onHeadersComplete = headers => fired = fired :+ headers)
          var decoded = List.empty[H1Request]
          var index   = 0
          while (index < bytes.length) {
            decoder.feed(bytes.slice(index, index + 1)) match {
              case Right(msgs) => decoded = decoded ++ msgs
              case Left(_)     => ()
            }
            index += 1
          }

          assertTrue(decoded.length == 1 && decoded.head.body == wire("hello") && fired.length == 1)
        },
        test("fires the hook before any body byte is consumed") {
          var fired   = List.empty[H1Headers]
          val decoder = new H1Decoder(onHeadersComplete = headers => fired = fired :+ headers)
          val first   =
            decoder.feed(wire("POST /s HTTP/1.1\r\nHost: x\r\nExpect: 100-continue\r\nContent-Length: 5\r\n\r\nhel"))
          val second  = decoder.feed(wire("lo"))

          assertTrue(
            first == Right(Nil) &&
              fired.length == 1 &&
              second.exists(_.length == 1) &&
              second.exists(_.head.body == wire("hello")) &&
              fired.length == 1,
          )
        },
        test("fires the hook once per pipelined request with Expect") {
          var fired   = List.empty[H1Headers]
          val decoder = new H1Decoder(onHeadersComplete = headers => fired = fired :+ headers)
          val one     = "POST /s HTTP/1.1\r\nHost: x\r\nExpect: 100-continue\r\nContent-Length: 5\r\n\r\nhello"
          val result  = decoder.feed(wire(one + one))

          assertTrue(result.exists(_.length == 2) && fired.length == 2)
        },
      ),
    )

  private def wire(s: String): Chunk[Byte] =
    Chunk.fromArray(s.getBytes("ISO-8859-1"))

  private def isError[E <: H1Error](result: Either[H1Error, Any])(implicit tag: scala.reflect.ClassTag[E]): Boolean =
    result match {
      case Left(error) if tag.runtimeClass.isInstance(error) => true
      case _                                                 => false
    }
}
