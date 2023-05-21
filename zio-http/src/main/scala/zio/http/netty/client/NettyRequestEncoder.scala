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

import zio.{Task, Trace, ZIO}

import zio.http.Request
import zio.http.netty._
import zio.http.netty.model.Conversions

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{DefaultFullHttpRequest, DefaultHttpRequest, HttpHeaderNames, HttpRequest}

private[zio] object NettyRequestEncoder {

  /**
   * Converts a ZIO HTTP request to a Netty HTTP request.
   */
  def encode(req: Request)(implicit trace: Trace): Task[HttpRequest] = {
    val method   = Conversions.methodToNetty(req.method)
    val jVersion = Versions.convertToZIOToNetty(req.version)

    // As per the spec, the path should contain only the relative path.
    // Host and port information should be in the headers.
    val path = req.url.relative.encode

    val encodedReqHeaders = Conversions.headersToNetty(req.allHeaders)

    val headers = req.url.hostPort match {
      case Some(host) => encodedReqHeaders.set(HttpHeaderNames.HOST, host)
      case _          => encodedReqHeaders
    }

    if (req.body.isComplete) {
      req.body.asChunk.map { chunk =>
        val content = Unpooled.wrappedBuffer(chunk.toArray)

        val writerIndex = content.writerIndex()
        headers.set(HttpHeaderNames.CONTENT_LENGTH, writerIndex.toString)

        val jReq = new DefaultFullHttpRequest(jVersion, method, path, content)
        jReq.headers().set(headers)
        jReq
      }
    } else {
      ZIO.attempt {
        headers.set(HttpHeaderNames.TRANSFER_ENCODING, "chunked")
        new DefaultHttpRequest(jVersion, method, path, headers)
      }
    }
  }
}
