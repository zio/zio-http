package zhttp.service

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import io.netty.util.AttributeKey
import zhttp.http._
import zhttp.logging.Logger
import zhttp.service.Handler.{Unsafe, log}
import zhttp.service.server.{ServerTime, WebSocketUpgrade}
import zio.ZIO

@Sharable
private[zhttp] final case class Handler[R](
  http: HttpApp[R, Throwable],
  runtime: HttpRuntime[R],
  config: Server.Config[R, Throwable],
  time: ServerTime,
) extends SimpleChannelInboundHandler[HttpObject](false)
    with WebSocketUpgrade[R]
    with FullPassWriter[R]
    with FastPassWriter[R] { self =>

  override def channelRead0(ctx: Ctx, msg: HttpObject): Unit = {
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
          runtime.unsafeRun {
            self.attemptFullWrite(exit, jReq) ensuring ZIO.succeed { Unsafe.releaseRequest(jReq) }
          }

      case jReq: HttpRequest =>
        log.debug(s"HttpRequest: [${jReq.method()} ${jReq.uri()}]")
        val req  = Request.fromHttpRequest(jReq)
        val exit = http.execute(req)

        if (!self.attemptFastWrite(exit)) {
          if (Unsafe.canHaveBody(jReq)) Unsafe.setAutoRead(false)
          runtime.unsafeRun {
            self.attemptFullWrite(exit, jReq) ensuring ZIO.succeed(Unsafe.setAutoRead(true))
          }
        }

      case msg: HttpContent => ctx.fireChannelRead(msg): Unit

      case _ =>
        throw new IllegalStateException(s"Unexpected message type: ${msg.getClass.getName}")
    }

  }

  override def exceptionCaught(ctx: Ctx, cause: Throwable): Unit = {
    config.error.fold(super.exceptionCaught(ctx, cause))(f => runtime.unsafeRun(f(cause))(ctx))
  }
}

object Handler {
  val log: Logger = Log.withTags("Server", "Request")

  object Unsafe {
    private val isReadKey = AttributeKey.newInstance[Boolean]("IS_READ_KEY")

    def addContentHandler(async: Body.UnsafeAsync)(implicit ctx: Ctx): Unit = {
      if (Unsafe.contentIsRead) throw new RuntimeException("Content is already read")
      ctx.channel().pipeline().addAfter(HTTP_REQUEST_HANDLER, HTTP_CONTENT_HANDLER, new ContentHandler(async)): Unit
      Unsafe.setContentReadAttr(true)(ctx)
    }

    /**
     * Enables auto-read if possible. Also performs the first read.
     */
    def attemptAutoRead[R, E](config: Server.Config[R, E])(implicit ctx: Ctx): Unit = {
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

    def contentIsRead(implicit ctx: Ctx): Boolean =
      ctx.channel().attr(isReadKey).get()

    def hasChanged(r1: Response, r2: Response): Boolean =
      (r1.status eq r2.status) && (r1.body eq r2.body) && (r1.headers eq r2.headers)

    def releaseRequest(jReq: FullHttpRequest, cnt: Int = 1): Unit = {
      if (jReq.refCnt() > 0 && cnt > 0) {
        jReq.release(cnt): Unit
      }
    }

    def setAutoRead(cond: Boolean)(implicit ctx: Ctx): Unit = {
      log.debug(s"Setting channel auto-read to: [${cond}]")
      ctx.channel().config().setAutoRead(cond): Unit
    }

    def setContentReadAttr(flag: Boolean)(implicit ctx: Ctx): Unit = {
      ctx.channel().attr(isReadKey).set(flag)
    }

    /**
     * Sets the server time on the response if required
     */
    def setServerTime(time: ServerTime, response: Response, jResponse: HttpResponse): Unit = {
      if (response.attribute.serverTime)
        jResponse.headers().set(HttpHeaderNames.DATE, time.refreshAndGet()): Unit
    }

  }

}
