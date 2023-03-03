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

import io.netty.channel.{
  Channel => JChannel,
  ChannelFactory => JChannelFactory,
  ChannelHandlerContext,
  EventLoopGroup => JEventLoopGroup,
  ServerChannel,
}
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

package object service {
  type ChannelFactory       = JChannelFactory[JChannel]
  type EventLoopGroup       = JEventLoopGroup
  type ServerChannelFactory = JChannelFactory[ServerChannel]
  private[zio] type Ctx     = ChannelHandlerContext
  private[zio] val AUTO_RELEASE_REQUEST               = false
  private[zio] val SERVER_CODEC_HANDLER               = "SERVER_CODEC"
  private[zio] val HTTP_OBJECT_AGGREGATOR             = "HTTP_OBJECT_AGGREGATOR"
  private[zio] val HTTP_REQUEST_HANDLER               = "HTTP_REQUEST"
  private[zio] val HTTP_RESPONSE_HANDLER              = "HTTP_RESPONSE"
  private[zio] val HTTP_KEEPALIVE_HANDLER             = "HTTP_KEEPALIVE"
  private[zio] val FLOW_CONTROL_HANDLER               = "FLOW_CONTROL_HANDLER"
  private[zio] val WEB_SOCKET_HANDLER                 = "WEB_SOCKET_HANDLER"
  private[zio] val SSL_HANDLER                        = "SSL_HANDLER"
  private[zio] val HTTP_ON_HTTPS_HANDLER              = "HTTP_ON_HTTPS_HANDLER"
  private[zio] val HTTP_SERVER_CODEC                  = "HTTP_SERVER_CODEC"
  private[zio] val HTTP_CLIENT_CODEC                  = "HTTP_CLIENT_CODEC"
  private[zio] val HTTP_SERVER_EXPECT_CONTINUE        = "HTTP_SERVER_EXPECT_CONTINUE"
  private[zio] val HTTP_SERVER_FLUSH_CONSOLIDATION    = "HTTP_SERVER_FLUSH_CONSOLIDATION"
  private[zio] val CLIENT_INBOUND_HANDLER             = "CLIENT_INBOUND_HANDLER"
  private[zio] val CLIENT_STREAMING_BODY_HANDLER      = "CLIENT_STREAMING_BODY_HANDLER"
  private[zio] val WEB_SOCKET_CLIENT_PROTOCOL_HANDLER = "WEB_SOCKET_CLIENT_PROTOCOL_HANDLER"
  private[zio] val HTTP_REQUEST_DECOMPRESSION         = "HTTP_REQUEST_DECOMPRESSION"
  private[zio] val HTTP_RESPONSE_COMPRESSION          = "HTTP_RESPONSE_COMPRESSION"
  private[zio] val LOW_LEVEL_LOGGING                  = "LOW_LEVEL_LOGGING"
  private[zio] val PROXY_HANDLER                      = "PROXY_HANDLER"
  private[zio] val HTTP_CONTENT_HANDLER               = "HTTP_CONTENT_HANDLER"
  private[zio] val HTTP_CLIENT_CONTENT_HANDLER        = "HTTP_CLIENT_CONTENT_HANDLER"
}
