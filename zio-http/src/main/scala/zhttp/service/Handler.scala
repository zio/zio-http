package zhttp.service

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.HttpVersion._
import io.netty.handler.codec.http._
import zhttp.http._
import zhttp.service.server.{ServerTimeGenerator, WebSocketUpgrade}
import zio.stream.ZStream
import zio.{UIO, ZIO}

import java.net.InetSocketAddress

private[zhttp] final case class Handler[R](
  app: HttpApp[R, Throwable],
  runtime: HttpRuntime[R],
  config: Server.Config[R, Throwable],
  serverTime: ServerTimeGenerator,
) extends SimpleChannelInboundHandler[FullHttpRequest](AUTO_RELEASE_REQUEST)
    with WebSocketUpgrade[R] { self =>

  override def channelRead0(ctx: ChannelHandlerContext, msg: FullHttpRequest): Unit = {
    implicit val iCtx: ChannelHandlerContext = ctx
    (for {
      url <- URL.fromString(msg.uri())
      method        = Method.fromHttpMethod(msg.method())
      headers       = Header.make(msg.headers())
      data          = HttpData.fromByteBuf(msg.content())
      remoteAddress = {
        if (ctx != null && ctx.channel().remoteAddress().isInstanceOf[InetSocketAddress])
          Some(ctx.channel().remoteAddress().asInstanceOf[InetSocketAddress].getAddress)
        else
          None
      }
    } yield Request(method, url, headers, remoteAddress, data)) match {
      case Left(err)  => unsafeWriteAndFlushErrorResponse(err)
      case Right(req) => unsafeRun(app, req, msg)
    }
  }

  private val notFoundResponse: HttpResponse = {
    val response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, false)
    response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0)
    response
  }

  private def decodeResponse(res: Response[_, _]): HttpResponse = {
    if (res.attribute.memoize) decodeResponseCached(res) else decodeResponseFresh(res)
  }

  private def decodeResponseCached(res: Response[_, _]): HttpResponse = {
    val cachedResponse = res.cache
    // Update cache if it doesn't exist OR has become stale
    // TODO: add unit tests for server-time
    if (cachedResponse == null || (res.attribute.serverTime && serverTime.canUpdate())) {
      val jRes = decodeResponseFresh(res)
      res.cache = jRes
      jRes
    } else cachedResponse
  }

  private def decodeResponseFresh(res: Response[_, _]): HttpResponse = {
    val jHeaders = Header.disassemble(res.getHeaders)
    if (res.attribute.serverTime) jHeaders.set(HttpHeaderNames.DATE, serverTime.refreshAndGet())
    new DefaultHttpResponse(HttpVersion.HTTP_1_1, res.status.asJava, jHeaders)
  }

  private def serverErrorResponse(cause: Throwable): HttpResponse = {
    val content  = cause.toString
    val response = new DefaultFullHttpResponse(
      HTTP_1_1,
      INTERNAL_SERVER_ERROR,
      Unpooled.copiedBuffer(content, HTTP_CHARSET),
    )
    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.length)
    response
  }

  /**
   * Executes http apps
   */
  private def unsafeRun(
    http: Http[R, Throwable, Request, Response[R, Throwable]],
    a: Request,
    msg: FullHttpRequest,
  )(implicit ctx: ChannelHandlerContext): Unit = {
    http.execute(a).evaluate match {
      case HExit.Effect(resM) =>
        unsafeRunZIO {
          resM.foldM(
            {
              case Some(cause) => UIO(unsafeWriteAndFlushErrorResponse(cause))
              case None        => UIO(unsafeWriteAndFlushEmptyResponse())
            },
            res =>
              if (self.canSwitchProtocol(res))
                UIO(self.initializeSwitch(ctx, res)) *> UIO(self.switchProtocol(ctx, msg))
              else {
                for {
                  _ <- UIO(unsafeWriteAnyResponse(res))
                  _ <- res.data match {
                    case HttpData.Empty =>
                      UIO(unsafeWriteAndFlushLastEmptyContent())

                    case data @ HttpData.Text(_, _) =>
                      UIO(unsafeWriteLastContent(data.encodeAndCache(res.attribute.memoize)))

                    case HttpData.BinaryByteBuf(data) => UIO(unsafeWriteLastContent(data))

                    case data @ HttpData.BinaryChunk(_) =>
                      UIO(unsafeWriteLastContent(data.encodeAndCache(res.attribute.memoize)))

                    case HttpData.BinaryStream(stream) =>
                      writeStreamContent(stream)
                  }
                } yield ()
              },
          )
        }

      case HExit.Success(res) =>
        if (self.canSwitchProtocol(res)) {
          self.initializeSwitch(ctx, res)
          self.switchProtocol(ctx, msg)
        } else {
          unsafeWriteAnyResponse(res)

          res.data match {
            case HttpData.Empty =>
              unsafeWriteAndFlushLastEmptyContent()

            case data @ HttpData.Text(_, _) =>
              unsafeWriteLastContent(data.encodeAndCache(res.attribute.memoize))

            case HttpData.BinaryByteBuf(data) =>
              unsafeWriteLastContent(data)

            case data @ HttpData.BinaryChunk(_) =>
              unsafeWriteLastContent(data.encodeAndCache(res.attribute.memoize))

            case HttpData.BinaryStream(stream) =>
              unsafeRunZIO(writeStreamContent(stream))
          }
        }

      case HExit.Failure(e) => unsafeWriteAndFlushErrorResponse(e)
      case HExit.Empty      => unsafeWriteAndFlushEmptyResponse()
    }
  }

  /**
   * Executes program
   */
  private def unsafeRunZIO(program: ZIO[R, Throwable, Any])(implicit ctx: ChannelHandlerContext): Unit =
    runtime.unsafeRun(ctx) {
      program
    }

  /**
   * Writes not found error response to the Channel
   */
  private def unsafeWriteAndFlushEmptyResponse()(implicit ctx: ChannelHandlerContext): Unit = {
    ctx.writeAndFlush(notFoundResponse, ctx.voidPromise()): Unit
  }

  /**
   * Writes error response to the Channel
   */
  private def unsafeWriteAndFlushErrorResponse(cause: Throwable)(implicit ctx: ChannelHandlerContext): Unit = {
    ctx.writeAndFlush(serverErrorResponse(cause), ctx.voidPromise()): Unit
  }

  /**
   * Writes last empty content to the Channel
   */
  private def unsafeWriteAndFlushLastEmptyContent()(implicit ctx: ChannelHandlerContext): Unit = {
    ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT, ctx.voidPromise()): Unit
  }

  /**
   * Writes any response to the Channel
   */
  private def unsafeWriteAnyResponse[A](res: Response[R, Throwable])(implicit ctx: ChannelHandlerContext): Unit = {
    ctx.write(decodeResponse(res), ctx.voidPromise()): Unit
  }

  /**
   * Writes ByteBuf data to the Channel
   */
  private def unsafeWriteLastContent[A](data: ByteBuf)(implicit ctx: ChannelHandlerContext): Unit = {
    ctx.writeAndFlush(new DefaultLastHttpContent(data), ctx.voidPromise()): Unit
  }

  /**
   * Writes Binary Stream data to the Channel
   */
  private def writeStreamContent[A](stream: ZStream[R, Throwable, Byte])(implicit ctx: ChannelHandlerContext) = {
    for {
      _ <- stream.foreachChunk(c =>
        ChannelFuture.unit((ctx.writeAndFlush(new DefaultHttpContent(Unpooled.wrappedBuffer(c.toArray))))),
      )
      _ <- ChannelFuture.unit(ctx.writeAndFlush(LastHttpContent.EMPTY_LAST_CONTENT))
    } yield ()
  }

}
