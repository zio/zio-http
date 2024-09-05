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

package zio.http.netty.client

import zio.Unsafe
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.netty.model.Conversions
import zio.http.{Body, Request, URL}

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{DefaultFullHttpRequest, DefaultHttpRequest, HttpHeaderNames, HttpRequest}

private[zio] object NettyRequestEncoder {

  /**
   * Converts a ZIO HTTP request to a Netty HTTP request.
   */
  def encode(req: Request): HttpRequest = {
    val method   = Conversions.methodToNetty(req.method)
    val jVersion = Conversions.versionToNetty(req.version)

    val path    = URL.encodeHttpPath(req.url)
    val headers = Conversions.headersToNetty(req.allHeaders)

    req.url.hostPort match {
      case Some(host) if !headers.contains(HttpHeaderNames.HOST) =>
        headers.set(HttpHeaderNames.HOST, host)
      case _                                                     =>
    }

    req.body match {
      case body: Body.UnsafeBytes =>
        val array   = body.unsafeAsArray(Unsafe.unsafe)
        val content = Unpooled.wrappedBuffer(array)

        headers.set(HttpHeaderNames.CONTENT_LENGTH, array.length.toString)

        val jReq = new DefaultFullHttpRequest(jVersion, method, path, content)
        jReq.headers().set(headers)
        jReq
      case _                      =>
        req.body.knownContentLength match {
          case Some(length) =>
            headers.set(HttpHeaderNames.CONTENT_LENGTH, length.toString)
          case None         =>
            headers.set(HttpHeaderNames.TRANSFER_ENCODING, "chunked")
        }
        new DefaultHttpRequest(jVersion, method, path, headers)
    }
  }

}
