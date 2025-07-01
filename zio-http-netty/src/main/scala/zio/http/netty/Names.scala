
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

package zio.http.netty

private[netty] object Names {
  val HTTP_REQUEST               = "HTTP_REQUEST"
  val HTTP_RESPONSE              = "HTTP_RESPONSE"
  val HTTP_SERVER_CODEC          = "HTTP_SERVER_CODEC"
  val HTTP_CLIENT_CODEC          = "HTTP_CLIENT_CODEC"
  val HTTP_KEEPALIVE             = "HTTP_KEEPALIVE"
  val HTTP_OBJECT_AGGREGATOR     = "HTTP_OBJECT_AGGREGATOR"
  val HTTP_CONTENT_COMPRESSOR    = "HTTP_CONTENT_COMPRESSOR"
  val HTTP_CONTENT_DECOMPRESSOR  = "HTTP_CONTENT_DECOMPRESSOR"
  val HTTP_REQUEST_DECOMPRESSION = "HTTP_REQUEST_DECOMPRESSION"
  val FLOW_CONTROL               = "FLOW_CONTROL"
  val WEB_SOCKET_HANDLER         = "WEB_SOCKET_HANDLER"
  val SSL_HANDLER                = "SSL_HANDLER"
  val PROXY_HANDLER              = "PROXY_HANDLER"
  val HTTP_LOGGING_HANDLER       = "HTTP_LOGGING_HANDLER"
}
