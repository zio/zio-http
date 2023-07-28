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

package zio.http

import zio.stacktracer.TracingImplicits.disableAutoTrace
import zio.{Trace, ZIO}

import io.netty.util.AsciiString
package object netty {

  private[zio] object Names {
    val HttpObjectAggregator           = "HTTP_OBJECT_AGGREGATOR"
    val HttpRequestHandler             = "HTTP_REQUEST"
    val HttpKeepAliveHandler           = "HTTP_KEEPALIVE"
    val FlowControlHandler             = "FLOW_CONTROL_HANDLER"
    val WebSocketHandler               = "WEB_SOCKET_HANDLER"
    val SSLHandler                     = "SSL_HANDLER"
    val HttpClientCodec                = "HTTP_CLIENT_CODEC"
    val HttpServerExpectContinue       = "HTTP_SERVER_EXPECT_CONTINUE"
    val HttpServerFlushConsolidation   = "HTTP_SERVER_FLUSH_CONSOLIDATION"
    val ClientInboundHandler           = "CLIENT_INBOUND_HANDLER"
    val ClientFailureHandler           = "CLIENT_FAILURE_HANDLER"
    val ClientStreamingBodyHandler     = "CLIENT_STREAMING_BODY_HANDLER"
    val WebSocketClientProtocolHandler = "WEB_SOCKET_CLIENT_PROTOCOL_HANDLER"
    val HttpRequestDecompression       = "HTTP_REQUEST_DECOMPRESSION"
    val HttpResponseCompression        = "HTTP_RESPONSE_COMPRESSION"
    val HttpContentHandler             = "HTTP_CONTENT_HANDLER"
    val HttpRequestDecoder             = "HTTP_REQUEST_DECODER"
    val HttpResponseEncoder            = "HTTP_RESPONSE_ENCODER"
    val ProxyHandler                   = "PROXY_HANDLER"
    val ReadTimeoutHandler             = "READ_TIMEOUT_HANDLER"
  }

  implicit class BodyExtensions(val body: Body) extends AnyVal {

    final def asCharSeq(implicit trace: Trace): ZIO[Any, Throwable, CharSequence] =
      body match {
        case _ => body.asArray.map { buf => new AsciiString(buf, false) }
      }
  }
}
