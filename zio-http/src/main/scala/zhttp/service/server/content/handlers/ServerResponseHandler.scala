package zhttp.service.server.content.handlers

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, DefaultFileRegion}
import io.netty.handler.codec.http._
import zhttp.http.{HttpData, Response}
import zhttp.service.server.ServerTime
import zhttp.service.{AGGREGATED_RESPONSE_COMPRESSION, ChannelFuture, HTTP_OBJECT_AGGREGATOR, HttpRuntime}
import zio.stream.ZStream
import zio.{UIO, ZIO}

import java.io.RandomAccessFile

@Sharable
private[zhttp] trait ServerResponseHandler[R] {
  def serverTime: ServerTime
  val rt: HttpRuntime[R]

  type Ctx = ChannelHandlerContext

  def writeResponse(msg: Response, jReq: FullHttpRequest)(implicit ctx: Ctx): Unit = {

    ctx.write(encodeResponse(msg, jReq, ctx))
    msg.data match {
      case HttpData.BinaryStream(stream)  =>
        rt.unsafeRun(ctx) {
          writeStreamContent(stream).ensuring(UIO(releaseRequest(jReq)))
        }
      case HttpData.RandomAccessFile(raf) =>
        unsafeWriteFileContent(raf())
        releaseRequest(jReq)
      case _                              =>
        ctx.flush()
        releaseRequest(jReq)
    }
    ()
  }

  /**
   * Checks if an encoded version of the response exists, uses it if it does.
   * Otherwise, it will return a fresh response. It will also set the server
   * time if requested by the client.
   */
  private def encodeResponse(res: Response, jReq: FullHttpRequest, ctx: Ctx): HttpResponse = {

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

    if (!ctx.pipeline().toMap.containsKey(AGGREGATED_RESPONSE_COMPRESSION)) {
      ctx
        .pipeline()
        .addBefore(
          HTTP_OBJECT_AGGREGATOR,
          AGGREGATED_RESPONSE_COMPRESSION,
          new AggregatesResponseCompressionHandler(res.attribute.compressionOptions.map(_.toJava)),
        )
    }

    if (jReq.headers().contains(HttpHeaderNames.ACCEPT_ENCODING))
      jResponse.headers().set(HttpHeaderNames.ACCEPT_ENCODING, jReq.headers().get(HttpHeaderNames.ACCEPT_ENCODING))

    // Identify if the server time should be set and update if required.
    if (res.attribute.serverTime) jResponse.headers().set(HttpHeaderNames.DATE, serverTime.refreshAndGet())
    jResponse
  }

  /**
   * Releases the FullHttpRequest safely.
   */
  private def releaseRequest(jReq: FullHttpRequest): Unit = {
    if (jReq.refCnt() > 0) {
      jReq.release(jReq.refCnt()): Unit
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

  /**
   * Writes file content to the Channel. Does not use Chunked transfer encoding
   */

  private def unsafeWriteFileContent(raf: RandomAccessFile)(implicit ctx: ChannelHandlerContext): Unit = {
    val fileLength = raf.length()
    // Write the content.
    ctx.write(new DefaultFileRegion(raf.getChannel, 0, fileLength))
    // Write the end marker.
    ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT): Unit
  }
}
