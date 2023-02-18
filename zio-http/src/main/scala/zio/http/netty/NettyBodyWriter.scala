package zio.http.netty

import zio._

import zio.http.Body
import zio.http.Body._

import io.netty.buffer.Unpooled
import io.netty.channel._
import io.netty.handler.codec.http.{DefaultHttpContent, LastHttpContent}

object NettyBodyWriter {

  def write(body: Body, ctx: ChannelHandlerContext): ZIO[Any, Throwable, Boolean] =
    body match {
      case body: ByteBufBody            =>
        ZIO.succeed {
          ctx.write(body.byteBuf)
          false
        }
      case body: FileBody               =>
        ZIO.succeed {
          val file = body.file
          // Write the content.
          ctx.write(new DefaultFileRegion(file, 0, file.length()))

          // Write the end marker.
          ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
          true
        }
      case AsyncBody(async)             =>
        ZIO.attempt {
          async { (ctx, msg, isLast) =>
            ctx.writeAndFlush(msg)
            val _ =
              if (isLast) ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
              else ctx.read()
          }
          true
        }
      case AsciiStringBody(asciiString) =>
        ZIO.attempt {
          ctx.write(Unpooled.wrappedBuffer(asciiString.array()))
          false
        }
      case StreamBody(stream)           =>
        stream
          .runForeachChunk(chunk =>
            NettyFutureExecutor.executed(
              ctx.writeAndFlush(new DefaultHttpContent(Unpooled.wrappedBuffer(chunk.toArray))),
            ),
          )
          .flatMap { _ =>
            NettyFutureExecutor.executed(ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)).as(true)
          }
      case ChunkBody(data)              =>
        ZIO.succeed {
          ctx.write(Unpooled.wrappedBuffer(data.toArray))
          false
        }
      case EmptyBody                    => ZIO.succeed(false)
    }
}
