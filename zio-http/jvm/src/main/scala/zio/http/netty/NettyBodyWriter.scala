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

  def writeAndFlush(body: Body, contentLength: Option[Long], ctx: ChannelHandlerContext)(implicit
    trace: Trace,
  ): Option[Task[Unit]] = {

    def writeArray(body: Array[Byte], isLast: Boolean) = {
      val content = new DefaultHttpContent(Unpooled.wrappedBuffer(body))
      if (isLast) {
        // Flushes the last body content and LastHttpContent together to avoid race conditions.
        ctx.write(content)
        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
      } else {
        ctx.writeAndFlush(content)
      }
    }

    body match {
      case body: ByteBufBody                  =>
        ctx.write(new DefaultHttpContent(body.byteBuf))
        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
        None
      case body: FileBody                     =>
        val file = body.file
        // Write the content.
        ctx.write(new DefaultFileRegion(file, 0, file.length()))

        // Write the end marker.
        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
        None
      case AsyncBody(async, _, _, _)          =>
        async(
          new UnsafeAsync {
            override def apply(message: Chunk[Byte], isLast: Boolean): Unit = {
              val arr = message match {
                case b: ByteArray => b.array
                case other        => other.toArray
              }
              writeArray(arr, isLast)
            }

            override def fail(cause: Throwable): Unit = {
              ctx.fireExceptionCaught(cause)
              ()
            }
          },
        )
        None
      case AsciiStringBody(asciiString, _, _) =>
        writeArray(asciiString.array(), isLast = true)
        None
      case StreamBody(stream, _, _, _)        =>
        Some(
          contentLength.orElse(body.knownContentLength) match {
            case Some(length) =>
              stream.chunks
                .runFoldZIO(length) { (remaining, bytes) =>
                  remaining - bytes.size match {
                    case 0L =>
                      NettyFutureExecutor.executed {
                        writeArray(bytes.toArray, isLast = true)
                      }.as(0L)

                    case n =>
                      NettyFutureExecutor.executed {
                        writeArray(bytes.toArray, isLast = false)
                      }.as(n)
                  }
                }
                .flatMap {
                  case 0L        =>
                    ZIO.unit
                  case remaining =>
                    val actualLength = length - remaining
                    ZIO.logWarning(s"Expected Content-Length of $length, but sent $actualLength bytes") *>
                      NettyFutureExecutor.executed {
                        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                      }
                }

            case None =>
              stream.chunks.mapZIO { bytes =>
                NettyFutureExecutor.executed {
                  writeArray(bytes.toArray, isLast = false)
                }
              }.runDrain.zipRight {
                NettyFutureExecutor.executed {
                  ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
                }
              }
          },
        )
      case ArrayBody(data, _, _)              =>
        writeArray(data, isLast = true)
        None
      case ChunkBody(data, _, _)              =>
        writeArray(data.toArray, isLast = true)
        None
      case EmptyBody                          =>
        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
        None
    }
  }
}
