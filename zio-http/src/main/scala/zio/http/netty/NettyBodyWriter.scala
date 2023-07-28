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

import zio.Chunk.ByteArray
import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.Body
import zio.http.Body._
import zio.http.netty.NettyBody.{AsciiStringBody, AsyncBody, ByteBufBody, UnsafeAsync}

import io.netty.buffer.Unpooled
import io.netty.channel._
import io.netty.handler.codec.http.{DefaultHttpContent, LastHttpContent}
object NettyBodyWriter {

  def writeAndFlush(body: Body, ctx: ChannelHandlerContext)(implicit trace: Trace): ZIO[Any, Throwable, Unit] =
    body match {
      case body: ByteBufBody                  =>
        ZIO.succeed {
          ctx.write(body.byteBuf)
          ctx.flush()
        }
      case body: FileBody                     =>
        ZIO.succeed {
          val file = body.file
          // Write the content.
          ctx.write(new DefaultFileRegion(file, 0, file.length()))

          // Write the end marker.
          ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
        }
      case AsyncBody(async, _, _)             =>
        ZIO.attempt {
          async(
            new UnsafeAsync {
              override def apply(message: Chunk[Byte], isLast: Boolean): Unit = {
                val nettyMsg = message match {
                  case b: ByteArray => Unpooled.wrappedBuffer(b.array)
                  case other        => Unpooled.wrappedBuffer(other.toArray)
                }
                ctx.writeAndFlush(nettyMsg)
                if (isLast) ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
              }

              override def fail(cause: Throwable): Unit =
                ctx.fireExceptionCaught(cause)
            },
          )
        }
      case AsciiStringBody(asciiString, _, _) =>
        ZIO.attempt {
          ctx.write(Unpooled.wrappedBuffer(asciiString.array()))
          ctx.flush()
        }
      case StreamBody(stream, _, _)           =>
        stream.chunks
          .runFoldZIO(Option.empty[Chunk[Byte]]) {
            case (Some(previous), current) =>
              NettyFutureExecutor.executed {
                ctx.writeAndFlush(new DefaultHttpContent(Unpooled.wrappedBuffer(previous.toArray)))
              } *>
                ZIO.succeed(Some(current))
            case (_, current)              =>
              ZIO.succeed(Some(current))
          }
          .flatMap { maybeLastChunk =>
            // last chunk is handled separately to avoid fiber interrupt before EMPTY_LAST_CONTENT is sent
            ZIO.attempt(
              maybeLastChunk.foreach { lastChunk =>
                ctx.write(new DefaultHttpContent(Unpooled.wrappedBuffer(lastChunk.toArray)))
              },
            ) *>
              NettyFutureExecutor.executed {
                ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
              }
          }
      case ChunkBody(data, _, _)              =>
        ZIO.succeed {
          ctx.write(Unpooled.wrappedBuffer(data.toArray))
          ctx.flush()
        }
      case EmptyBody                          => ZIO.succeed(ctx.flush())
    }
}
