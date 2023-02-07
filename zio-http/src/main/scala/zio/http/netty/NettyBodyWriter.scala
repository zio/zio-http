package zio.http.netty

import io.netty.buffer.{ByteBuf, ByteBufAllocator, Unpooled}
import io.netty.channel._
import io.netty.handler.codec.http.{DefaultHttpContent, HttpChunkedInput, HttpContent, LastHttpContent}
import io.netty.handler.stream.{ChunkedInput, ChunkedWriteHandler}
import zio._
import zio.http.Body
import zio.http.Body._
import zio.stream.Take

object NettyBodyWriter {

  def write(body: Body, ctx: ChannelHandlerContext): ZIO[Scope, Throwable, Boolean] =
    body match {
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
        val addChunkedWriter =
          ZIO
            .attempt(
              ctx.pipeline().addBefore(Names.HttpRequestHandler, Names.ChunkedWriter, new ChunkedWriteHandler()),
            )
            .zipRight(Scope.addFinalizer(ZIO.attempt(ctx.pipeline().remove(Names.ChunkedWriter)).orDie))
            .uninterruptible

        implicit val unsafe: Unsafe = Unsafe.unsafe
        for {
          _     <- addChunkedWriter
          queue <- Queue.bounded[Take[Throwable, ByteBuf]](1)
          _     <- stream.chunks
            .mapZIO(chunk => ZIO.succeedNow(Unpooled.wrappedBuffer(chunk.toArray)))
            .runIntoQueue(queue)
            .forkDaemon
            .onExecutor(Runtime.defaultExecutor)
          runtime = Runtime.default
          finished <- Ref.make(false)
          chunkedInput = new ChunkedInput[HttpContent] {
            override def isEndOfInput: Boolean =
              runtime.unsafe.run(finished.get).getOrThrowFiberFailure()

            override def readChunk(allocator: ByteBufAllocator): HttpContent = {
              val r = runtime.unsafe
                .run(
                  queue.take.flatMap(
                    _.foldZIO[Any, Throwable, HttpContent](
                      finished.set(true).as(LastHttpContent.EMPTY_LAST_CONTENT),
                      ZIO.failCause(_),
                      {
                        case Chunk(buf) => ZIO.succeedNow(new DefaultHttpContent(buf))
                        case _          => throw new IllegalStateException("Chunks should contain single ByteBufs")
                      },
                    ),
                  ),
                )
                .getOrThrowFiberFailure()
              r
            }

            override def close(): Unit = ()

            override def readChunk(ctx: ChannelHandlerContext): HttpContent =
              this.readChunk(ctx.alloc())

            override def length(): Long = -1

            override def progress(): Long = -1
          }
          _ <- ZIO.succeedNow(
            ctx.writeAndFlush(chunkedInput),
          )
        } yield true

      case ChunkBody(data) =>
        ZIO.succeedNow {
          ctx.write(Unpooled.wrappedBuffer(data.toArray))
          false
        }
      case EmptyBody       => ZIO.succeedNow(false)
    }
}
