package zhttp.service

import io.netty.buffer.ByteBuf
import io.netty.channel.{ChannelHandlerContext, DefaultFileRegion}
import io.netty.handler.codec.http._
import zhttp.http._
import zhttp.logging.Logger
import zhttp.service.ServerResponseWriter.log
import zhttp.service.server.ServerTime
import zio.stream.ZStream
import zio.{UIO, ZIO}

import java.io.File

private[zhttp] final class ServerResponseWriter[R](
  runtime: HttpRuntime[R],
  config: Server.Config[R, Throwable],
  serverTime: ServerTime,
) { self =>

  /**
   * Enables auto-read if possible. Also performs the first read.
   */
  private def attemptAutoRead()(implicit ctx: Ctx): Unit = {
    if (!config.useAggregator && !ctx.channel().config().isAutoRead) {
      ctx.channel().config().setAutoRead(true)
      ctx.read(): Unit
    }
  }

  /**
   * Checks if an encoded version of the response exists, uses it if it does.
   * Otherwise, it will return a fresh response. It will also set the server
   * time if requested by the client.
   */
  private def encodeResponse(res: Response): HttpResponse = {

    val jResponse = res.attribute.encoded match {

      // Check if the encoded response exists and/or was modified.
      case Some((oRes, jResponse)) if oRes eq res =>
        jResponse match {
          // Duplicate the response without allocating much memory
          case response: FullHttpResponse => response.retainedDuplicate()

          case response => response
        }

      case _ => res.unsafeEncode()
    }
    // Identify if the server time should be set and update if required.
    if (res.attribute.serverTime) jResponse.headers().set(HttpHeaderNames.DATE, serverTime.refreshAndGet())
    jResponse
  }

  private def flushReleaseAndRead(jReq: HttpRequest)(implicit ctx: Ctx): Unit = {
    ctx.flush()
    releaseAndRead(jReq)
  }

  private def releaseAndRead(jReq: HttpRequest)(implicit ctx: Ctx): Unit = {
    releaseRequest(jReq)
    attemptAutoRead()
  }

  /**
   * Releases the FullHttpRequest safely.
   */
  private def releaseRequest(jReq: HttpRequest)(implicit ctx: Ctx): Unit = {
    jReq match {
      case jReq: FullHttpRequest if jReq.refCnt() > 0 => jReq.release(jReq.refCnt()): Unit
      case _                                          => ()
    }
  }

  /**
   * Writes file content to the Channel. Does not use Chunked transfer encoding
   */
  private def unsafeWriteFileContent(file: File)(implicit ctx: ChannelHandlerContext): Unit = {
    // Write the content.
    ctx.write(new DefaultFileRegion(file, 0, file.length()))
    // Write the end marker.
    ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT): Unit
  }

  /**
   * Writes data on the channel
   */
  private def writeData(data: HttpData, jReq: HttpRequest)(implicit ctx: Ctx): Unit = {
    log.debug(s"WriteData: ${data.getClass.getSimpleName}")
    data match {

      case _: HttpData.FromAsciiString => flushReleaseAndRead(jReq)

      case _: HttpData.BinaryChunk => flushReleaseAndRead(jReq)

      case _: HttpData.BinaryByteBuf => flushReleaseAndRead(jReq)

      case HttpData.Empty => flushReleaseAndRead(jReq)

      case HttpData.BinaryStream(stream) =>
        runtime.unsafeRun(ctx) {
          writeStreamContent(stream).ensuring(UIO(releaseAndRead(jReq)))
        }

      case HttpData.JavaFile(unsafeGet) =>
        unsafeWriteFileContent(unsafeGet())
        releaseAndRead(jReq)

      case HttpData.UnsafeAsync(unsafeRun) =>
        unsafeRun { _ => msg =>
          ctx.writeAndFlush(msg)
          if (!msg.isInstanceOf[LastHttpContent]) ctx.read(): Unit
        }
    }
  }

  /**
   * Writes Binary Stream data to the Channel
   */
  private def writeStreamContent[A](
    stream: ZStream[R, Throwable, ByteBuf],
  )(implicit ctx: Ctx): ZIO[R, Throwable, Unit] = {
    for {
      _ <- stream.foreach(c => UIO(ctx.writeAndFlush(c)))
      _ <- ChannelFuture.unit(ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT))
    } yield ()
  }

  def write(msg: Throwable, jReq: HttpRequest)(implicit ctx: Ctx): Unit = {
    val response = Response
      .fromHttpError(HttpError.InternalServerError(cause = Some(msg)))
      .withConnection(HeaderValues.close)
    self.write(response, jReq)
  }

  def write(msg: Response, jReq: HttpRequest)(implicit ctx: Ctx): Unit = {
    ctx.write(encodeResponse(msg))
    writeData(msg.data, jReq)
    ()
  }

  def write(msg: HttpError, jReq: HttpRequest)(implicit ctx: Ctx): Unit = {
    val response = Response.fromHttpError(msg)
    self.write(response, jReq)
  }

  def write(msg: Status, jReq: HttpRequest)(implicit ctx: Ctx): Unit = {
    val response = Response.status(msg)
    self.write(response, jReq)
  }

  def writeNotFound(jReq: HttpRequest)(implicit ctx: Ctx): Unit = {
    val error = HttpError.NotFound(Path.decode(jReq.uri()))
    self.write(error, jReq)
  }
}

object ServerResponseWriter {
  val log: Logger = Log.withTags("Server", "Response")
}
