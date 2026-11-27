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

/**
 * Deterministic linearity proofs for the resumable request decoder: a head or
 * chunked body arriving one byte per feed must decode correctly while the
 * scanner examines each buffered byte O(1) times, witnessed by
 * [[H1Decoder.scannedBytes]] staying within a small constant multiple of the
 * message size. Also pins the mid-scan line caps: an unterminated line past its
 * bound is rejected during the scan, before any CRLF arrives.
 */
object H1DecoderLinearitySpec extends ZIOSpecDefault {
  override def spec =
    suite("H1DecoderLinearitySpec")(
      suite("drip-fed decoding is linear")(
        test("decodes a 32 KB head delivered one byte per feed with linear scan work") {
          val head     = bigHead
          val decoder  = new H1Decoder()
          var decoded  = List.empty[H1Request]
          var failed   = Option.empty[H1Error]
          var index    = 0
          var lastSeen = 0L
          var monotone = true
          while (index < head.length && failed.isEmpty) {
            decoder.feed(head.slice(index, index + 1)) match {
              case Right(msgs) => decoded = decoded ++ msgs
              case Left(error) => failed = Some(error)
            }
            if (decoder.scannedBytes < lastSeen) monotone = false
            lastSeen = decoder.scannedBytes
            index += 1
          }

          assertTrue(
            head.length >= 32000 &&
              failed.isEmpty &&
              decoded.length == 1 &&
              decoded.head.method == "POST" &&
              decoded.head.target == "/bench/head" &&
              decoded.head.headers.fields.length == 5 &&
              decoder.bufferedBytes == 0 &&
              monotone &&
              decoder.scannedBytes <= 8L * head.length.toLong,
          )
        },
        test("decodes a 256 KB chunked body delivered one byte per feed with linear scan work") {
          val message  = bigChunked
          val decoder  = new H1Decoder()
          var decoded  = List.empty[H1Request]
          var failed   = Option.empty[H1Error]
          var index    = 0
          var lastSeen = 0L
          var monotone = true
          while (index < message.length && failed.isEmpty) {
            decoder.feed(message.slice(index, index + 1)) match {
              case Right(msgs) => decoded = decoded ++ msgs
              case Left(error) => failed = Some(error)
            }
            if (decoder.scannedBytes < lastSeen) monotone = false
            lastSeen = decoder.scannedBytes
            index += 1
          }

          assertTrue(
            decoded.head.body.length == 256 * 1024 &&
              failed.isEmpty &&
              decoded.length == 1 &&
              decoded.head.framing == H1BodyFraming.Chunked &&
              decoded.head.body == wire("b" * (256 * 1024)) &&
              decoder.bufferedBytes == 0 &&
              monotone &&
              decoder.scannedBytes <= 8L * message.length.toLong,
          )
        },
        test("decodes a byte-at-a-time pipelined pair in order") {
          val bytes   = wire("GET /a HTTP/1.1\r\nHost: x\r\n\r\nGET /b HTTP/1.1\r\nHost: x\r\n\r\n")
          val decoder = new H1Decoder()
          var decoded = List.empty[H1Request]
          var failed  = Option.empty[H1Error]
          var index   = 0
          while (index < bytes.length && failed.isEmpty) {
            decoder.feed(bytes.slice(index, index + 1)) match {
              case Right(msgs) => decoded = decoded ++ msgs
              case Left(error) => failed = Some(error)
            }
            index += 1
          }

          assertTrue(
            failed.isEmpty &&
              decoded.map(_.target) == List("/a", "/b") &&
              decoder.bufferedBytes == 0 &&
              decoder.scannedBytes > 0L &&
              decoder.scannedBytes <= 8L * bytes.length.toLong,
          )
        },
      ),
      suite("line caps fire during the scan, before the terminator")(
        test("rejects an unterminated request line as soon as it exceeds the cap") {
          val decoder = new H1Decoder(H1Limits.Default.copy(maxRequestLineLength = 16))

          val result = decoder.feed(wire("X" * 17))

          assertTrue(is[H1Error.LineTooLong](result) && decoder.poisoned)
        },
        test("rejects a drip-fed unterminated request line at the crossing byte") {
          val decoder = new H1Decoder(H1Limits.Default.copy(maxRequestLineLength = 16))
          val bytes   = wire("X" * 17)
          var failure = Option.empty[H1Error]
          var index   = 0
          while (index < bytes.length && failure.isEmpty) {
            decoder.feed(bytes.slice(index, index + 1)) match {
              case Right(_)  => ()
              case Left(err) => failure = Some(err)
            }
            index += 1
          }

          assertTrue(
            failure.exists(_.isInstanceOf[H1Error.LineTooLong]) &&
              index == 17 &&
              decoder.poisoned,
          )
        },
        test("rejects an unterminated chunk-size line as soon as it exceeds the cap") {
          val decoder = new H1Decoder(H1Limits.Default.copy(maxChunkLineLength = 4))
          val head    = wire("POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n")

          val first  = decoder.feed(head)
          val second = decoder.feed(wire("FFFFF"))

          assertTrue(
            first == Right(Nil) &&
              is[H1Error.LineTooLong](second) &&
              decoder.poisoned,
          )
        },
        test("rejects an unterminated header line that would exceed the section bound") {
          val decoder = new H1Decoder(H1Limits.Default.copy(maxHeaderSectionBytes = 64))
          val head    = wire("GET / HTTP/1.1\r\nHost: x\r\nX-A: ")

          val first  = decoder.feed(head)
          val second = decoder.feed(wire("a" * 80))

          assertTrue(
            first == Right(Nil) &&
              is[H1Error.HeaderSectionTooLarge](second) &&
              decoder.poisoned,
          )
        },
        test("rejects an unterminated trailer line that would exceed the trailer section bound") {
          val decoder = new H1Decoder(H1Limits.Default.copy(maxTrailerSectionBytes = 16))
          val head    = wire("POST / HTTP/1.1\r\nHost: x\r\nTransfer-Encoding: chunked\r\n\r\n0\r\n")

          val first  = decoder.feed(head)
          val second = decoder.feed(wire("X-T: " + "a" * 30))

          assertTrue(
            first == Right(Nil) &&
              is[H1Error.TrailerSectionTooLarge](second) &&
              decoder.poisoned,
          )
        },
      ),
    )

  /**
   * A valid origin-form POST head of ~32 KB: request line (27 B), Host (19 B),
   * four `X-Pad` fields with near-maximum values (8170/8169 B), and the blank
   * terminator. Header-section bytes total 32738 <= 32768 and every value stays
   * under `maxHeaderValueLength` (8192), so the head is legal under the default
   * limits.
   */
  private def bigHead: Chunk[Byte] = {
    val sb     = new StringBuilder
    sb.append("POST /bench/head HTTP/1.1\r\n")
    sb.append("Host: bench.local\r\n")
    val values = List(8170, 8170, 8170, 8169)
    var i      = 0
    while (i < values.length) {
      sb.append("X-Pad").append(i).append(": ")
      var j = 0
      while (j < values(i)) {
        sb.append('a')
        j += 1
      }
      sb.append("\r\n")
      i += 1
    }
    sb.append("\r\n")
    wire(sb.toString)
  }

  /**
   * A valid chunked POST whose body is exactly 256 KB: 64 chunks of 4096 `b`
   * bytes, no trailers. The whole message stays far below the default retention
   * bound, so a one-byte-per-feed delivery must decode without error.
   */
  private def bigChunked: Chunk[Byte] = {
    val sb    = new StringBuilder
    sb.append("POST /bench/upload HTTP/1.1\r\nHost: bench.local\r\nTransfer-Encoding: chunked\r\n\r\n")
    var chunk = 0
    while (chunk < 64) {
      sb.append("1000\r\n")
      var j = 0
      while (j < 4096) {
        sb.append('b')
        j += 1
      }
      sb.append("\r\n")
      chunk += 1
    }
    sb.append("0\r\n\r\n")
    wire(sb.toString)
  }

  private def wire(s: String): Chunk[Byte] =
    Chunk.fromArray(s.getBytes("ISO-8859-1"))

  private def is[E <: H1Error](result: Either[H1Error, Any])(implicit tag: scala.reflect.ClassTag[E]): Boolean =
    result match {
      case Left(error) if tag.runtimeClass.isInstance(error) => true
      case _                                                 => false
    }
}
