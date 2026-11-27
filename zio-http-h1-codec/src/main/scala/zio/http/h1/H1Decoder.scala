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

import scala.collection.mutable.ArrayBuffer

import zio.blocks.chunk.Chunk

private[h1] final case class H1CompletedRequest(message: H1Request, next: Int)
private[h1] final case class H1CompletedResponse(message: H1Response, next: Int)
private[h1] final case class H1Line(content: String, next: Int)
private[h1] final case class H1ParsedHeaders(headers: H1Headers, next: Int)
private[h1] final case class H1ChunkedBody(body: Chunk[Byte], trailers: H1Headers, next: Int)
private[h1] final case class H1ParsedTrailers(headers: H1Headers, next: Int)

final class H1Decoder(val limits: H1Limits = H1Limits.Default) {
  private val CR: Byte = 13
  private val LF: Byte = 10

  private var buffer: Array[Byte]   = new Array[Byte](8192)
  private var start: Int            = 0
  private var end: Int              = 0
  private var poisonedFlag: Boolean = false
  private var compactionCount: Long = 0L

  def poisoned: Boolean   = poisonedFlag
  def bufferedBytes: Int  = end - start
  def bufferCapacity: Int = buffer.length
  def compactions: Long   = compactionCount

  def feed(bytes: Chunk[Byte]): Either[H1Error, List[H1Request]] = {
    if (poisonedFlag) return Left(H1Error.DecoderPoisoned())
    append(bytes)
    val messages = List.newBuilder[H1Request]
    var pos      = start
    var failure  = Option.empty[H1Error]
    var blocked  = false
    while (failure.isEmpty && !blocked) {
      parseRequest(pos) match {
        case Left(error)            => failure = Some(error)
        case Right(None)            => blocked = true
        case Right(Some(completed)) =>
          messages += completed.message
          pos = completed.next
      }
    }
    if (failure.isDefined) poison(failure.get)
    else {
      compact(pos)
      if (end - start > limits.maxUndecodedBytes) poison(H1Error.RetentionOverflow())
      else Right(messages.result())
    }
  }

  def feedResponse(bytes: Chunk[Byte], requestMethod: String): Either[H1Error, List[H1Response]] = {
    if (poisonedFlag) return Left(H1Error.DecoderPoisoned())
    append(bytes)
    val messages = List.newBuilder[H1Response]
    var pos      = start
    var failure  = Option.empty[H1Error]
    var blocked  = false
    while (failure.isEmpty && !blocked) {
      parseResponse(pos, requestMethod) match {
        case Left(error)            => failure = Some(error)
        case Right(None)            => blocked = true
        case Right(Some(completed)) =>
          messages += completed.message
          pos = completed.next
      }
    }
    if (failure.isDefined) poison(failure.get)
    else {
      compact(pos)
      if (end - start > limits.maxUndecodedBytes) poison(H1Error.RetentionOverflow())
      else Right(messages.result())
    }
  }

  private def poison(error: H1Error): Either[H1Error, Nothing] = {
    poisonedFlag = true
    start = 0
    end = 0
    Left(error)
  }

  private def compact(pos: Int): Unit =
    if (pos > start) {
      val remaining = end - pos
      if (remaining > 0) System.arraycopy(buffer, pos, buffer, 0, remaining)
      start = 0
      end = remaining
      compactionCount += 1L
    }

  private def append(bytes: Chunk[Byte]): Unit = {
    val needed = (end - start) + bytes.length
    if (needed > buffer.length) {
      var capacity = buffer.length * 2
      while (capacity < needed) capacity *= 2
      val grown    = new Array[Byte](capacity)
      System.arraycopy(buffer, start, grown, 0, end - start)
      buffer = grown
      end = end - start
      start = 0
    }
    var i      = 0
    while (i < bytes.length) {
      buffer(end) = bytes(i)
      end += 1
      i += 1
    }
  }

  private def parseRequest(pos: Int): Either[H1Error, Option[H1CompletedRequest]] = {
    var p = pos
    if (end - p >= 2 && buffer(p) == CR && buffer(p + 1) == LF) p += 2
    readLine(p) match {
      case Left(error)       => Left(error)
      case Right(None)       => Right(None)
      case Right(Some(line)) =>
        parseRequestLine(line.content) match {
          case Left(error)             => Left(error)
          case Right((method, target)) =>
            parseHeaders(line.next) match {
              case Left(error)          => Left(error)
              case Right(None)          => Right(None)
              case Right(Some(headers)) =>
                if (target.startsWith("/") && !headers.headers.contains("Host"))
                  Left(H1Error.InvalidMessage("origin-form target requires a Host header"))
                else {
                  H1Framing.resolve(
                    headers.headers,
                    limits,
                    isResponse = false,
                    statusCode = 0,
                    requestMethod = method,
                  ) match {
                    case Left(error)    => Left(error)
                    case Right(framing) =>
                      framing match {
                        case H1BodyFraming.Empty         =>
                          Right(
                            Some(
                              H1CompletedRequest(
                                H1Request(method, target, headers.headers, emptyChunk, framing, H1Headers.Empty),
                                headers.next,
                              ),
                            ),
                          )
                        case H1BodyFraming.Fixed(length) =>
                          val available = (end - headers.next).toLong
                          if (available < length) Right(None)
                          else {
                            val body = copyChunk(headers.next, length.toInt)
                            Right(
                              Some(
                                H1CompletedRequest(
                                  H1Request(method, target, headers.headers, body, framing, H1Headers.Empty),
                                  headers.next + length.toInt,
                                ),
                              ),
                            )
                          }
                        case H1BodyFraming.Chunked       =>
                          parseChunkedBody(headers.next) match {
                            case Left(error)          => Left(error)
                            case Right(None)          => Right(None)
                            case Right(Some(chunked)) =>
                              Right(
                                Some(
                                  H1CompletedRequest(
                                    H1Request(method, target, headers.headers, chunked.body, framing, chunked.trailers),
                                    chunked.next,
                                  ),
                                ),
                              )
                          }
                      }
                  }
                }
            }
        }
    }
  }

  private def parseResponse(pos: Int, requestMethod: String): Either[H1Error, Option[H1CompletedResponse]] =
    readLine(pos) match {
      case Left(error)       => Left(error)
      case Right(None)       => Right(None)
      case Right(Some(line)) =>
        parseStatusLine(line.content) match {
          case Left(error)             => Left(error)
          case Right((status, reason)) =>
            parseHeaders(line.next) match {
              case Left(error)          => Left(error)
              case Right(None)          => Right(None)
              case Right(Some(headers)) =>
                if (isNoBody(status, requestMethod)) {
                  if (headers.headers.contains("Transfer-Encoding"))
                    Left(H1Error.AmbiguousFraming("Transfer-Encoding is forbidden on a bodyless response"))
                  else
                    Right(
                      Some(
                        H1CompletedResponse(
                          H1Response(status, reason, headers.headers, emptyChunk, H1BodyFraming.Empty, H1Headers.Empty),
                          headers.next,
                        ),
                      ),
                    )
                } else {
                  H1Framing.resolve(
                    headers.headers,
                    limits,
                    isResponse = true,
                    statusCode = status,
                    requestMethod = requestMethod,
                  ) match {
                    case Left(error)    => Left(error)
                    case Right(framing) =>
                      framing match {
                        case H1BodyFraming.Empty         =>
                          Right(
                            Some(
                              H1CompletedResponse(
                                H1Response(status, reason, headers.headers, emptyChunk, framing, H1Headers.Empty),
                                headers.next,
                              ),
                            ),
                          )
                        case H1BodyFraming.Fixed(length) =>
                          val available = (end - headers.next).toLong
                          if (available < length) Right(None)
                          else {
                            val body = copyChunk(headers.next, length.toInt)
                            Right(
                              Some(
                                H1CompletedResponse(
                                  H1Response(status, reason, headers.headers, body, framing, H1Headers.Empty),
                                  headers.next + length.toInt,
                                ),
                              ),
                            )
                          }
                        case H1BodyFraming.Chunked       =>
                          parseChunkedBody(headers.next) match {
                            case Left(error)          => Left(error)
                            case Right(None)          => Right(None)
                            case Right(Some(chunked)) =>
                              Right(
                                Some(
                                  H1CompletedResponse(
                                    H1Response(
                                      status,
                                      reason,
                                      headers.headers,
                                      chunked.body,
                                      framing,
                                      chunked.trailers,
                                    ),
                                    chunked.next,
                                  ),
                                ),
                              )
                          }
                      }
                  }
                }
            }
        }
    }

  private def isNoBody(status: Int, requestMethod: String): Boolean =
    (status >= 100 && status <= 199) || status == 204 || status == 304 ||
      H1Validation.equalsIgnoreCase(requestMethod, "HEAD")

  private def readLine(pos: Int): Either[H1Error, Option[H1Line]] = {
    val lf = indexOfLF(pos)
    if (lf < 0) Right(None)
    else if (lf == pos || buffer(lf - 1) != CR) Left(H1Error.InvalidMessage("line must end with CRLF"))
    else {
      var i = pos
      while (i < lf - 1) {
        if (buffer(i) == CR) return Left(H1Error.InvalidMessage("lone CR inside a line"))
        i += 1
      }
      Right(Some(H1Line(latin1(pos, lf - 1), lf + 1)))
    }
  }

  private def indexOfLF(from: Int): Int = {
    var i = from
    while (i < end) {
      if (buffer(i) == LF) return i
      i += 1
    }
    -1
  }

  private def latin1(from: Int, until: Int): String = {
    val chars = new Array[Char](until - from)
    var i     = 0
    while (from + i < until) {
      chars(i) = (buffer(from + i) & 0xff).toChar
      i += 1
    }
    new String(chars)
  }

  private def parseRequestLine(content: String): Either[H1Error, (String, String)] = {
    val first  = content.indexOf(' ')
    val second = if (first < 0) -1 else content.indexOf(' ', first + 1)
    if (first <= 0 || second <= first + 1 || second == content.length - 1 || content.indexOf(' ', second + 1) >= 0)
      Left(H1Error.InvalidMessage("invalid request line: '" + content + "'"))
    else {
      val method  = content.substring(0, first)
      val target  = content.substring(first + 1, second)
      val version = content.substring(second + 1)
      if (content.length > limits.maxRequestLineLength) Left(H1Error.LineTooLong("request line exceeds the bound"))
      else if (!H1Validation.isToken(method)) Left(H1Error.InvalidMessage("invalid method: '" + method + "'"))
      else if (!H1Validation.isValidTarget(target)) Left(H1Error.InvalidMessage("invalid target: '" + target + "'"))
      else if (version != "HTTP/1.1") Left(H1Error.InvalidMessage("unsupported version: '" + version + "'"))
      else Right((method, target))
    }
  }

  private def parseStatusLine(content: String): Either[H1Error, (Int, String)] = {
    if (content.length > limits.maxStatusLineLength) return Left(H1Error.LineTooLong("status line exceeds the bound"))
    if (!content.startsWith("HTTP/1.1 ") || content.length < 12)
      return Left(H1Error.InvalidMessage("invalid status line: '" + content + "'"))
    val digits = content.substring(9, 12)
    if (!isDigits(digits) || content.length < 13 || content.charAt(12) != ' ')
      return Left(H1Error.InvalidMessage("invalid status line: '" + content + "'"))
    val status = (digits.charAt(0) - '0') * 100 + (digits.charAt(1) - '0') * 10 + (digits.charAt(2) - '0')
    if (status < 100 || status > 599) return Left(H1Error.InvalidMessage("invalid status code: '" + digits + "'"))
    val reason = content.substring(13)
    if (!H1Validation.isReasonPhrase(reason)) Left(H1Error.InvalidMessage("invalid reason phrase"))
    else Right((status, reason))
  }

  private def isDigits(s: String): Boolean = {
    var i = 0
    if (s.isEmpty) return false
    while (i < s.length) {
      if (s.charAt(i) < '0' || s.charAt(i) > '9') return false
      i += 1
    }
    true
  }

  private def parseHeaders(pos: Int): Either[H1Error, Option[H1ParsedHeaders]] = {
    val fields       = List.newBuilder[H1Header]
    var cursor       = pos
    var sectionBytes = 0
    var count        = 0
    var done         = false
    var failure      = Option.empty[H1Error]
    while (!done && failure.isEmpty) {
      readLine(cursor) match {
        case Left(error)       => failure = Some(error)
        case Right(None)       => return Right(None)
        case Right(Some(line)) =>
          if (line.content.isEmpty) {
            done = true
            cursor = line.next
          } else {
            parseHeaderField(line.content) match {
              case Left(error)  => failure = Some(error)
              case Right(field) =>
                sectionBytes += (line.content.length + 2)
                count += 1
                if (field.name.length > limits.maxHeaderNameLength)
                  failure = Some(H1Error.HeaderNameTooLong("header name exceeds the bound: '" + field.name + "'"))
                else if (field.value.length > limits.maxHeaderValueLength)
                  failure = Some(H1Error.HeaderValueTooLong("header value exceeds the bound"))
                else if (sectionBytes > limits.maxHeaderSectionBytes)
                  failure = Some(H1Error.HeaderSectionTooLarge("header section exceeds the bound"))
                else if (count > limits.maxHeaderCount)
                  failure = Some(H1Error.TooManyHeaders("too many header fields"))
                else {
                  fields += field
                  cursor = line.next
                }
            }
          }
      }
    }
    if (failure.isDefined) Left(failure.get)
    else Right(Some(H1ParsedHeaders(H1Headers(fields.result()), cursor)))
  }

  private def parseHeaderField(content: String): Either[H1Error, H1Header] = {
    val first  = if (content.isEmpty) ' ' else content.charAt(0)
    if (first == ' ' || first == '\t') return Left(H1Error.InvalidMessage("obs-fold continuation is forbidden"))
    val colon  = content.indexOf(':')
    if (colon <= 0) return Left(H1Error.InvalidMessage("header field without a colon"))
    val before = content.charAt(colon - 1)
    if (before == ' ' || before == '\t') return Left(H1Error.InvalidMessage("whitespace before the header colon"))
    val name   = content.substring(0, colon)
    val value  = H1Validation.trimOWS(content.substring(colon + 1))
    if (!H1Validation.isToken(name)) Left(H1Error.InvalidMessage("invalid header name: '" + name + "'"))
    else if (!H1Validation.isHeaderValue(value)) Left(H1Error.InvalidMessage("invalid header value"))
    else Right(H1Header(name, value))
  }

  private def parseChunkedBody(pos: Int): Either[H1Error, Option[H1ChunkedBody]] = {
    val body    = new ArrayBuffer[Byte]()
    var cursor  = pos
    var failure = Option.empty[H1Error]
    var result  = Option.empty[H1ChunkedBody]
    while (failure.isEmpty && result.isEmpty) {
      readLine(cursor) match {
        case Left(error)       => failure = Some(error)
        case Right(None)       => return Right(None)
        case Right(Some(line)) =>
          if (line.content.isEmpty) failure = Some(H1Error.InvalidMessage("empty chunk-size line"))
          else if (line.content.length > limits.maxChunkLineLength)
            failure = Some(H1Error.LineTooLong("chunk-size line exceeds the bound"))
          else {
            H1Validation.parseChunkSize(line.content) match {
              case Left(error) => failure = Some(error)
              case Right(size) =>
                if (size > limits.maxUndecodedBytes.toLong)
                  failure = Some(H1Error.BodyTooLarge("chunk size exceeds the retention bound"))
                else if (body.size.toLong + size > limits.maxUndecodedBytes.toLong)
                  failure = Some(H1Error.BodyTooLarge("chunked body exceeds the retention bound"))
                else if (size == 0L) {
                  parseTrailers(line.next) match {
                    case Left(error)           => failure = Some(error)
                    case Right(None)           => return Right(None)
                    case Right(Some(trailers)) =>
                      result = Some(H1ChunkedBody(Chunk.fromArray(body.toArray), trailers.headers, trailers.next))
                  }
                } else {
                  val dataStart = line.next
                  val needed    = size + 2L
                  if ((end - dataStart).toLong < needed) return Right(None)
                  val dataEnd   = dataStart + size.toInt
                  if (buffer(dataEnd) != CR || buffer(dataEnd + 1) != LF)
                    failure = Some(H1Error.InvalidMessage("chunk data terminator must be CRLF"))
                  else {
                    var i = dataStart
                    while (i < dataEnd) {
                      body += buffer(i)
                      i += 1
                    }
                    cursor = dataEnd + 2
                  }
                }
            }
          }
      }
    }
    if (failure.isDefined) Left(failure.get)
    else Right(result)
  }

  private def parseTrailers(pos: Int): Either[H1Error, Option[H1ParsedTrailers]] = {
    val fields       = List.newBuilder[H1Header]
    var cursor       = pos
    var sectionBytes = 0
    var count        = 0
    var done         = false
    var failure      = Option.empty[H1Error]
    while (!done && failure.isEmpty) {
      readLine(cursor) match {
        case Left(error)       => failure = Some(error)
        case Right(None)       => return Right(None)
        case Right(Some(line)) =>
          if (line.content.isEmpty) {
            done = true
            cursor = line.next
          } else {
            parseHeaderField(line.content) match {
              case Left(error)  => failure = Some(error)
              case Right(field) =>
                sectionBytes += (line.content.length + 2)
                count += 1
                if (field.name.equalsIgnoreCase("Content-Length") || field.name.equalsIgnoreCase("Transfer-Encoding"))
                  failure = Some(H1Error.AmbiguousFraming("framing header inside trailers: '" + field.name + "'"))
                else if (field.name.length > limits.maxHeaderNameLength)
                  failure = Some(H1Error.HeaderNameTooLong("trailer name exceeds the bound: '" + field.name + "'"))
                else if (field.value.length > limits.maxHeaderValueLength)
                  failure = Some(H1Error.HeaderValueTooLong("trailer value exceeds the bound"))
                else if (sectionBytes > limits.maxTrailerSectionBytes)
                  failure = Some(H1Error.TrailerSectionTooLarge("trailer section exceeds the bound"))
                else if (count > limits.maxTrailerCount)
                  failure = Some(H1Error.TooManyTrailers("too many trailer fields"))
                else {
                  fields += field
                  cursor = line.next
                }
            }
          }
      }
    }
    if (failure.isDefined) Left(failure.get)
    else Right(Some(H1ParsedTrailers(H1Headers(fields.result()), cursor)))
  }

  private val emptyChunk: Chunk[Byte] = Chunk.fromArray(new Array[Byte](0))

  private def copyChunk(from: Int, length: Int): Chunk[Byte] = {
    val out = new Array[Byte](length)
    System.arraycopy(buffer, from, out, 0, length)
    Chunk.fromArray(out)
  }
}
