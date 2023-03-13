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

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Task, Trace}

import zio.http.Request
import zio.http.netty._
import zio.http.netty.model.Conversions

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{DefaultFullHttpRequest, FullHttpRequest, HttpHeaderNames}

trait ClientRequestEncoder {

  /**
   * Converts client params to JFullHttpRequest
   */
  def encode(req: Request)(implicit trace: Trace): Task[FullHttpRequest] =
    req.body.asChunk.map { chunk =>
      val content  = Unpooled.wrappedBuffer(chunk.toArray)
      val method   = Conversions.methodToNetty(req.method)
      val jVersion = Versions.convertToZIOToNetty(req.version)

      // As per the spec, the path should contain only the relative path.
      // Host and port information should be in the headers.
      val path = req.url.relative.encode

      val encodedReqHeaders = Conversions.headersToNetty(req.headers)

      val headers = req.url.hostWithOptionalPort match {
        case Some(host) => encodedReqHeaders.set(HttpHeaderNames.HOST, host)
        case _          => encodedReqHeaders
      }

      val writerIndex = content.writerIndex()
      headers.set(HttpHeaderNames.CONTENT_LENGTH, writerIndex.toString)

      // TODO: we should also add a default user-agent req header as some APIs might reject requests without it.
      val jReq = new DefaultFullHttpRequest(jVersion, method, path, content)
      jReq.headers().set(headers)

      jReq
    }
}
