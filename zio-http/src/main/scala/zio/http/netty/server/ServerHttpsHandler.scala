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

import io.netty.channel.{ChannelFutureListener, ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{DefaultHttpResponse, HttpMessage, HttpResponseStatus, HttpVersion}

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

import zio.http.SSLConfig.HttpBehaviour

private[zio] class ServerHttpsHandler(httpBehaviour: HttpBehaviour) extends SimpleChannelInboundHandler[HttpMessage] {
  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpMessage): Unit = {

    // TODO: PatMat maybe???
    if (msg.isInstanceOf[HttpMessage]) {
      if (httpBehaviour == HttpBehaviour.Redirect) {
        val message  = msg.asInstanceOf[HttpMessage]
        val address  = message.headers.get("Host")
        val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.PERMANENT_REDIRECT)
        if (address != null) {
          response.headers.set("Location", "https://" + address)
        }
        ctx.channel.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
        ()
      } else {
        val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_ACCEPTABLE)
        ctx.channel.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE)
        ()
      }
    }
  }
}
