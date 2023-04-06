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

import zio._

import zio.http.Body
import zio.http.Body._
import zio.http.netty.NettyBody.{AsciiStringBody, AsyncBody, ByteBufBody}

import io.netty.buffer.Unpooled
import io.netty.channel._
import io.netty.handler.codec.http.{DefaultHttpContent, LastHttpContent}

object NettyBodyWriter {

  def write(body: Body, ctx: ChannelHandlerContext): ZIO[Any, Throwable, Boolean] =
    body match {
      case body: ByteBufBody                  =>
        ZIO.succeed {
          ctx.write(body.byteBuf)
          false
        }
      case body: FileBody                     =>
        ZIO.succeed {
          val file = body.file
          // Write the content.
          ctx.write(new DefaultFileRegion(file, 0, file.length()))

          // Write the end marker.
          ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
          true
        }
      case AsyncBody(async, _, _)             =>
        ZIO.attempt {
          async { (ctx, msg, isLast) =>
            ctx.writeAndFlush(msg)
            val _ =
              if (isLast) ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
              else ctx.read()
          }
          true
        }
      case AsciiStringBody(asciiString, _, _) =>
        ZIO.attempt {
          ctx.write(Unpooled.wrappedBuffer(asciiString.array()))
          false
        }
      case StreamBody(stream, _, _)           =>
        stream
          .runForeachChunk(chunk =>
            NettyFutureExecutor.executed {
              println(s"writing chunk ${new String(chunk.toArray)}")
              ctx.writeAndFlush(new DefaultHttpContent(Unpooled.wrappedBuffer(chunk.toArray)))
            }
              .debug("wrote chunk"),
          )
          .flatMap { _ =>
            println("starting to write last chunk")
            NettyFutureExecutor.executed {
              println("writing last chunk")
              ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
            }.as(true).debug("wrote last chunk")
          }
      case ChunkBody(data, _, _)              =>
        ZIO.succeed {
          ctx.write(Unpooled.wrappedBuffer(data.toArray))
          false
        }
      case EmptyBody                          => ZIO.succeed(false)
    }
}
