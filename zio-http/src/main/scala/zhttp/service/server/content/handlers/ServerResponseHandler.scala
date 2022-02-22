package zhttp.service.server.content.handlers

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, DefaultFileRegion}
import io.netty.handler.codec.http._
import zhttp.http.{HttpData, Response}
import zhttp.service.server.ServerTime
import zhttp.service.{ChannelFuture, HttpRuntime}
import zio.stream.ZStream
import zio.{UIO, ZIO}

import java.io.RandomAccessFile

@Sharable
private[zhttp] trait ServerResponseHandler[R] {
  type Ctx = ChannelHandlerContext
  val rt: HttpRuntime[R]

  def serverTime: ServerTime

  def writeResponse(msg: Response, jReq: HttpRequest)(implicit ctx: Ctx): Unit = {

    ctx.write(encodeResponse(msg))
    writeData(msg.data, jReq)
    ()
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
  private def unsafeWriteFileContent(raf: RandomAccessFile)(implicit ctx: ChannelHandlerContext): Unit = {
    val fileLength = raf.length()
    // Write the content.
    ctx.write(new DefaultFileRegion(raf.getChannel, 0, fileLength))
    // Write the end marker.
    ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT): Unit
  }

  /**
   * Writes data on the channel
   */
  private def writeData(data: HttpData, jReq: HttpRequest)(implicit ctx: Ctx): Unit = {
    data match {
      case HttpData.Asynchronous(_) =>
        releaseRequest(jReq)
        throw new IllegalStateException("Cannot write data to response")
      case data: HttpData.Complete  =>
        data match {
          case HttpData.BinaryStream(stream)  =>
            rt.unsafeRun(ctx) {
              writeStreamContent(stream).ensuring(UIO {
                releaseRequest(jReq)
                if (!jReq.isInstanceOf[FullHttpRequest]) ctx.read(): Unit // read next request
              })
            }
          case HttpData.RandomAccessFile(raf) =>
            unsafeWriteFileContent(raf())
            releaseRequest(jReq)
            if (!jReq.isInstanceOf[FullHttpRequest]) ctx.read(): Unit // read next request
          case _                              =>
            ctx.flush()
            releaseRequest(jReq)
            if (!jReq.isInstanceOf[FullHttpRequest]) ctx.read(): Unit // read next request
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
}
