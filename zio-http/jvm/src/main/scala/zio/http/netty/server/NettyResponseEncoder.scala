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

package zio.http.netty.server

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http._
import zio.http.netty.CachedDateHeader
import zio.http.netty.model.Conversions

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http._

private object NettyResponseEncoder {
  private val dateHeaderCache = CachedDateHeader.default

  def encode(method: Method, response: Response)(implicit unsafe: Unsafe): HttpResponse =
    response.body match {
      case body: Body.UnsafeBytes =>
        fastEncode(method, response, body.unsafeAsArray)
      case body                   =>
        val status   = response.status
        val jHeaders = Conversions.headersToNetty(response.headers)
        val jStatus  = Conversions.statusToNetty(status)
        maybeAddDateHeader(jHeaders, status)

        val hasContentLength = jHeaders.contains(HttpHeaderNames.CONTENT_LENGTH)

        // See https://github.com/zio/zio-http/issues/3080
        if (method == Method.HEAD && hasContentLength) ()
        else
          body.knownContentLength match {
            case Some(contentLength)    =>
              jHeaders.set(HttpHeaderNames.CONTENT_LENGTH, contentLength)
            case _ if !hasContentLength =>
              jHeaders.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
            case _                      =>
              ()
          }
        new DefaultHttpResponse(HttpVersion.HTTP_1_1, jStatus, jHeaders)
    }

  def fastEncode(method: Method, response: Response, bytes: Array[Byte])(implicit unsafe: Unsafe): FullHttpResponse = {
    if (response.encoded eq null) {
      response.encoded = doEncode(method, response, bytes)
    }
    response.encoded.asInstanceOf[FullHttpResponse]
  }

  private def doEncode(method: Method, response: Response, bytes: Array[Byte]): FullHttpResponse = {
    val jHeaders = Conversions.headersToNetty(response.headers)
    val status   = response.status
    maybeAddDateHeader(jHeaders, status)

    val jStatus = Conversions.statusToNetty(status)

    val jContent = Unpooled.wrappedBuffer(bytes)

    /*
     * The content-length MUST match the length of the content we are sending,
     * except for HEAD requests where the content-length must equal the length
     * of the content we would have sent if this was a GET request.
     */
    if (method == Method.HEAD && jHeaders.contains(HttpHeaderNames.CONTENT_LENGTH)) ()
    else jHeaders.set(HttpHeaderNames.CONTENT_LENGTH, bytes.length)

    new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, jStatus, jContent, jHeaders, EmptyHttpHeaders.INSTANCE)
  }

  /**
   * We don't need to add the Date header in the following case:
   *   - Status code is 1xx
   *   - Status code is 5xx
   *   - User already provided it
   */
  private def maybeAddDateHeader(headers: HttpHeaders, status: Status): Unit = {
    if (status.isInformational || status.isServerError || headers.contains(HttpHeaderNames.DATE)) ()
    else {
      val _ = headers.set(HttpHeaderNames.DATE, dateHeaderCache.get())
    }
  }

}
