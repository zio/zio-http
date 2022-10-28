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
      ZIO.attempt(ctx.write(body.byteBufData): Unit).as(false)
    case body: FileBody               =>
      ZIO.attempt {
        val file = body.fileData
        // Write the content.
        ctx.write(new DefaultFileRegion(file, 0, file.length()))

        // Write the end marker.
        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT): Unit
        true
      }
    case AsyncBody(async)             =>
      ZIO
        .attempt(async { (ctx, msg, isLast) =>
          ctx.writeAndFlush(msg)
          if (isLast) ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT): Unit
          else ctx.read(): Unit
        })
        .as(true)
    case AsciiStringBody(asciiString) =>
      ZIO.attempt(ctx.write(Unpooled.wrappedBuffer(asciiString.array())): Unit).as(false)
    case StreamBody(stream)           =>
      for {
        _ <- stream.runForeachChunk(c => ZIO.succeed(ctx.writeAndFlush(Unpooled.wrappedBuffer(c.toArray))))
        _ <- ZIO.attempt(ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT))
      } yield true
    case ChunkBody(data)              =>
      ZIO.attempt(ctx.write(Unpooled.wrappedBuffer(data.toArray))).as(false)
    case EmptyBody                    => ZIO.succeed(false)
  }

  def unsafeWrite(body: Body.UnsafeWriteable, ctx: ChannelHandlerContext): Boolean =
    body match {
      case body: ByteBufBody            =>
        ctx.write(body.byteBufData)
        false
      case ChunkBody(data)              =>
        ctx.write(Unpooled.wrappedBuffer(data.toArray))
        false
      case EmptyBody                    => false
      case AsyncBody(unsafeAsync)       =>
        unsafeAsync { (ctx, msg, isLast) =>
          ctx.writeAndFlush(msg)
          if (isLast) ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT): Unit
          else ctx.read(): Unit
        }
        true
      case AsciiStringBody(asciiString) =>
        ctx.write(Unpooled.wrappedBuffer(asciiString.array()))
        false
      case body: FileBody               =>
        val file = body.fileData
        ctx.write(new DefaultFileRegion(file, 0, file.length()))
        // Write the end marker.
        ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT): Unit
        true
    }

}
