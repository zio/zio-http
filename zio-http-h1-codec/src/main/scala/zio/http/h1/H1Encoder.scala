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

object H1Encoder {
  def encodeRequest(request: H1Request): Either[H1Error, Chunk[Byte]] =
    for {
      _   <- validateMethod(request.method)
      _   <- validateTarget(request.target)
      _   <- validateHeaders(request.headers)
      _   <- validateTrailers(request.framing, request.trailers)
      out <- encodeMessage(
        request.headers,
        request.body,
        request.framing,
        request.trailers,
        isResponse = false,
        statusCode = 0,
        reason = "",
        method = request.method,
        target = request.target,
      )
    } yield out

  def encodeResponse(response: H1Response): Either[H1Error, Chunk[Byte]] =
    for {
      _   <- validateStatus(response.status)
      _   <- validateReason(response.reason)
      _   <- validateHeaders(response.headers)
      _   <- validateTrailers(response.framing, response.trailers)
      out <- encodeMessage(
        response.headers,
        response.body,
        response.framing,
        response.trailers,
        isResponse = true,
        statusCode = response.status,
        reason = response.reason,
        method = "",
        target = "",
      )
    } yield out

  private def encodeMessage(
    headers: H1Headers,
    body: Chunk[Byte],
    framing: H1BodyFraming,
    trailers: H1Headers,
    isResponse: Boolean,
    statusCode: Int,
    reason: String,
    method: String,
    target: String,
  ): Either[H1Error, Chunk[Byte]] = {
    val startLine                      =
      if (isResponse) "HTTP/1.1 " + statusCode.toString + " " + reason + "\r\n"
      else method + " " + target + " HTTP/1.1\r\n"
    val framingHeaders: List[H1Header] = framing match {
      case H1BodyFraming.Empty         => Nil
      case H1BodyFraming.Fixed(length) =>
        if (headers.contains("Content-Length")) Nil
        else List(H1Header("Content-Length", length.toString))
      case H1BodyFraming.Chunked       =>
        if (headers.contains("Transfer-Encoding")) Nil
        else List(H1Header("Transfer-Encoding", "chunked"))
    }
    val bodyless = isResponse && (statusCode == 204 || statusCode == 304 || (statusCode >= 100 && statusCode <= 199))
    if (!isResponse) {
      framing match {
        case H1BodyFraming.Fixed(length) =>
          if (body.length.toLong != length)
            return Left(H1Error.InvalidMessage("fixed body length does not match framing"))
        case H1BodyFraming.Empty         =>
          if (!body.isEmpty) return Left(H1Error.InvalidMessage("body requires framing"))
        case H1BodyFraming.Chunked       => ()
      }
    }
    val head                           = new StringBuilder(startLine)
    (headers.fields ++ framingHeaders).foreach { header =>
      head.append(header.name).append(": ").append(header.value).append("\r\n")
    }
    head.append("\r\n")
    val headBytes                      = latin1(head.toString)
    framing match {
      case H1BodyFraming.Empty    =>
        Right(Chunk.fromArray(headBytes))
      case H1BodyFraming.Fixed(_) =>
        if (bodyless) Right(Chunk.fromArray(headBytes))
        else Right(Chunk.fromArray(headBytes ++ toArray(body)))
      case H1BodyFraming.Chunked  =>
        val out       = new StringBuilder()
        out.append(Integer.toHexString(body.length).toUpperCase)
        out.append("\r\n")
        val chunkHead = latin1(out.toString)
        val tail      = new StringBuilder()
        tail.append("\r\n0\r\n")
        trailers.fields.foreach { trailer =>
          tail.append(trailer.name).append(": ").append(trailer.value).append("\r\n")
        }
        tail.append("\r\n")
        Right(Chunk.fromArray(headBytes ++ chunkHead ++ toArray(body) ++ latin1(tail.toString)))
    }
  }

  private def validateMethod(method: String): Either[H1Error, Unit] =
    if (H1Validation.isToken(method)) Right(())
    else Left(H1Error.InvalidMessage("invalid method: '" + method + "'"))

  private def validateTarget(target: String): Either[H1Error, Unit] =
    if (H1Validation.isValidTarget(target)) Right(())
    else Left(H1Error.InvalidMessage("invalid target: '" + target + "'"))

  private def validateStatus(status: Int): Either[H1Error, Unit] =
    if (status >= 100 && status <= 599) Right(())
    else Left(H1Error.InvalidMessage("invalid status code: '" + status.toString + "'"))

  private def validateReason(reason: String): Either[H1Error, Unit] =
    if (H1Validation.isReasonPhrase(reason)) Right(())
    else Left(H1Error.InvalidMessage("invalid reason phrase"))

  private def validateHeaders(headers: H1Headers): Either[H1Error, Unit] = {
    var rest = headers.fields
    while (rest.nonEmpty) {
      val header = rest.head
      if (!H1Validation.isToken(header.name))
        return Left(H1Error.InvalidMessage("invalid header name: '" + header.name + "'"))
      if (!H1Validation.isHeaderValue(header.value))
        return Left(H1Error.InvalidMessage("invalid header value"))
      rest = rest.tail
    }
    Right(())
  }

  private def validateTrailers(framing: H1BodyFraming, trailers: H1Headers): Either[H1Error, Unit] =
    if (trailers.fields.nonEmpty && framing != H1BodyFraming.Chunked)
      Left(H1Error.InvalidMessage("trailers require chunked framing"))
    else validateHeaders(trailers)

  private def latin1(s: String): Array[Byte] = {
    val out = new Array[Byte](s.length)
    var i   = 0
    while (i < s.length) {
      out(i) = s.charAt(i).toByte
      i += 1
    }
    out
  }

  private def toArray(bytes: Chunk[Byte]): Array[Byte] = {
    val out = new Array[Byte](bytes.length)
    var i   = 0
    while (i < bytes.length) {
      out(i) = bytes(i)
      i += 1
    }
    out
  }
}
