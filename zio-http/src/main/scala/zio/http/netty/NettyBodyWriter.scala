package zio.http.netty

import io.netty.buffer.Unpooled
import io.netty.channel._
import io.netty.handler.codec.http.LastHttpContent
import zio._
import zio.http.Body
import zio.http.Body._

object NettyBodyWriter {

  def write(body: Body, ctx: ChannelHandlerContext): Task[Boolean] = body match {
    case body: ByteBufBody            =>
      ZIO.succeedNow {
        ctx.write(body.byteBuf)
        false
      }
    case body: FileBody               =>
      ZIO.succeedNow {
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
      stream.runForeachChunk(c => ZIO.succeedNow(ctx.writeAndFlush(Unpooled.wrappedBuffer(c.toArray)))).flatMap { _ =>
        ZIO.succeedNow {
          ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
          true
        }
      }
    case ChunkBody(data)              =>
      ZIO.succeedNow {
        ctx.write(Unpooled.wrappedBuffer(data.toArray))
        false
      }
    case EmptyBody                    => ZIO.succeedNow(false)
  }

  def unsafeWrite(body: Body.UnsafeWriteable, ctx: ChannelHandlerContext): Boolean =
    body match {
      case body: ByteBufBody            =>
        ctx.write(body.byteBuf)
        false
      case ChunkBody(data)              =>
        ctx.write(Unpooled.wrappedBuffer(data.toArray))
        false
      case EmptyBody                    => false
      case AsyncBody(unsafeAsync)       =>
        unsafeAsync { (ctx, msg, isLast) =>
          ctx.writeAndFlush(msg)
          val _ =
            if (isLast) ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
            else ctx.read()
        }
        true
      case AsciiStringBody(asciiString) =>
        ctx.write(Unpooled.wrappedBuffer(asciiString.array()))
        false
      case body: FileBody               =>
        val file = body.file
        ctx.write(new DefaultFileRegion(file, 0, file.length()))
        // Write the end marker.
        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT)
        true
    }

}
