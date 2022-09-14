package zio.http
package netty

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import io.netty.util.AttributeKey
import zio.ZIO
import zio.http._
import zio.http.service.ServerInboundHandler.{Unsafe, log}
import zio.logging.Logger

@Sharable
private[zio] final case class ServerInboundHandler[R](
  http: HttpApp[R, Throwable],
  runtime: NettyRuntime[R],
  config: Server.Config[R, Throwable],
  time: service.ServerTime,
) extends SimpleChannelInboundHandler[HttpObject](false)
    with ServerWebSocketUpgrade[R]
    with ServerFullResponseWriter[R]
    with ServerFastResponseWriter[R] { self =>

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit = {
    log.debug(s"Message: [${msg.getClass.getName}]")
    implicit val iCtx: ChannelHandlerContext = ctx
    msg match {
      case jReq: FullHttpRequest =>
        log.debug(s"FullHttpRequest: [${jReq.method()} ${jReq.uri()}]")
        val req  = Request.fromFullHttpRequest(jReq)
        val exit = http.execute(req)

        if (self.attemptFastWrite(exit)) {
          Unsafe.releaseRequest(jReq)
        } else
          runtime.unsafeRun(ctx) {
            self.attemptFullWrite(exit, jReq) ensuring ZIO.succeed { Unsafe.releaseRequest(jReq) }
          }

      case jReq: HttpRequest =>
        log.debug(s"HttpRequest: [${jReq.method()} ${jReq.uri()}]")
        val req  = Request.fromHttpRequest(jReq)
        val exit = http.execute(req)

        if (!self.attemptFastWrite(exit)) {
          if (Unsafe.canHaveBody(jReq)) Unsafe.setAutoRead(false)
          runtime.unsafeRun(ctx) {
            self.attemptFullWrite(exit, jReq) ensuring ZIO.succeed(Unsafe.setAutoRead(true))
          }
        }

      case msg: HttpContent => ctx.fireChannelRead(msg): Unit

      case _ =>
        throw new IllegalStateException(s"Unexpected message type: ${msg.getClass.getName}")
    }

  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    config.error.fold(super.exceptionCaught(ctx, cause))(f => runtime.unsafeRun(ctx)(f(cause)))
  }
}

private[zio] object ServerInboundHandler {
  val log: Logger = service.Log.withTags("Server", "Request")

  object Unsafe {
    private val isReadKey = AttributeKey.newInstance[Boolean]("IS_READ_KEY")

    def addAsyncBodyHandler(async: Body.UnsafeAsync)(implicit ctx: ChannelHandlerContext): Unit = {
      if (Unsafe.contentIsRead) throw new RuntimeException("Content is already read")
      ctx
        .channel()
        .pipeline()
        .addAfter(service.HTTP_REQUEST_HANDLER, service.HTTP_CONTENT_HANDLER, new ServerAsyncBodyHandler(async)): Unit
      Unsafe.setContentReadAttr(true)(ctx)
    }

    /**
     * Enables auto-read if possible. Also performs the first read.
     */
    def attemptAutoRead[R, E](config: Server.Config[R, E])(implicit ctx: ChannelHandlerContext): Unit = {
      if (!config.useAggregator && !ctx.channel().config().isAutoRead) {
        ctx.channel().config().setAutoRead(true)
        ctx.read(): Unit
      }
    }

    def canHaveBody(jReq: HttpRequest): Boolean = {
      jReq.method() == HttpMethod.TRACE ||
      jReq.headers().contains(HttpHeaderNames.CONTENT_LENGTH) ||
      jReq.headers().contains(HttpHeaderNames.TRANSFER_ENCODING)
    }

    def contentIsRead(implicit ctx: ChannelHandlerContext): Boolean =
      ctx.channel().attr(isReadKey).get()

    def hasChanged(r1: Response, r2: Response): Boolean =
      (r1.status eq r2.status) && (r1.body eq r2.body) && (r1.headers eq r2.headers)

    def releaseRequest(jReq: FullHttpRequest, cnt: Int = 1): Unit = {
      if (jReq.refCnt() > 0 && cnt > 0) {
        jReq.release(cnt): Unit
      }
    }

    def setAutoRead(cond: Boolean)(implicit ctx: ChannelHandlerContext): Unit = {
      log.debug(s"Setting channel auto-read to: [${cond}]")
      ctx.channel().config().setAutoRead(cond): Unit
    }

    def setContentReadAttr(flag: Boolean)(implicit ctx: ChannelHandlerContext): Unit = {
      ctx.channel().attr(isReadKey).set(flag)
    }

    /**
     * Sets the server time on the response if required
     */
    def setServerTime(time: service.ServerTime, response: Response, jResponse: HttpResponse): Unit = {
      if (response.attribute.serverTime)
        jResponse.headers().set(HttpHeaderNames.DATE, time.refreshAndGet()): Unit
    }

  }

}
