package zio.http.service

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import io.netty.util.AttributeKey
import zio.http._
import zio.http.service.ServerInboundHandler.{log, unsafe}
import zio.logging.Logger
import zio.{Unsafe, ZIO}

@Sharable
private[zio] final case class ServerInboundHandler[R](
  http: HttpApp[R, Throwable],
  runtime: HttpRuntime[R],
  config: Server.Config[R, Throwable],
  time: ServerTime,
) extends SimpleChannelInboundHandler[HttpObject](false)
    with ServerWebSocketUpgrade[R]
    with ServerFullResponseWriter[R]
    with ServerFastResponseWriter[R] { self =>

  implicit private val unsafeClass: Unsafe = Unsafe.unsafe

  override def channelRead0(ctx: Ctx, msg: HttpObject): Unit = {
    log.debug(s"Message: [${msg.getClass.getName}]")
    implicit val iCtx: ChannelHandlerContext = ctx
    msg match {
      case jReq: FullHttpRequest =>
        log.debug(s"FullHttpRequest: [${jReq.method()} ${jReq.uri()}]")
        val req  = Request.fromFullHttpRequest(jReq)
        val exit = http.execute(req)

        if (self.attemptFastWrite(exit)) {
          unsafe.releaseRequest(jReq)
        } else
          runtime.run {
            self.attemptFullWrite(exit, jReq) ensuring ZIO.succeed {
              unsafe.releaseRequest(jReq)(unsafeClass)
            }
          }

      case jReq: HttpRequest =>
        log.debug(s"HttpRequest: [${jReq.method()} ${jReq.uri()}]")
        val req  = Request.fromHttpRequest(jReq)
        val exit = http.execute(req)

        if (!self.attemptFastWrite(exit)) {
          if (unsafe.canHaveBody(jReq)) unsafe.setAutoRead(false)
          runtime.run {
            self.attemptFullWrite(exit, jReq) ensuring ZIO.succeed(unsafe.setAutoRead(true)(ctx, unsafeClass))
          }
        }

      case msg: HttpContent => ctx.fireChannelRead(msg): Unit

      case _ =>
        throw new IllegalStateException(s"Unexpected message type: ${msg.getClass.getName}")
    }

  }

  override def exceptionCaught(ctx: Ctx, cause: Throwable): Unit = {
    config.error.fold(super.exceptionCaught(ctx, cause))(f => runtime.run(f(cause))(ctx, unsafeClass))
  }
}

object ServerInboundHandler {
  val log: Logger = Log.withTags("Server", "Request")

  object unsafe {
    private val isReadKey = AttributeKey.newInstance[Boolean]("IS_READ_KEY")

    def addAsyncBodyHandler(async: Body.UnsafeAsync)(implicit ctx: Ctx, unsafe: Unsafe): Unit = {
      if (contentIsRead) throw new RuntimeException("Content is already read")
      ctx
        .channel()
        .pipeline()
        .addAfter(HTTP_REQUEST_HANDLER, HTTP_CONTENT_HANDLER, new ServerAsyncBodyHandler(async)): Unit
      setContentReadAttr(flag = true)
    }

    /**
     * Enables auto-read if possible. Also performs the first read.
     */
    def attemptAutoRead[R, E](config: Server.Config[R, E])(implicit ctx: Ctx, unsafe: Unsafe): Unit = {
      if (!config.useAggregator && !ctx.channel().config().isAutoRead) {
        ctx.channel().config().setAutoRead(true)
        ctx.read(): Unit
      }
    }

    def canHaveBody(jReq: HttpRequest)(implicit unsafe: Unsafe): Boolean = {
      jReq.method() == HttpMethod.TRACE ||
      jReq.headers().contains(HttpHeaderNames.CONTENT_LENGTH) ||
      jReq.headers().contains(HttpHeaderNames.TRANSFER_ENCODING)
    }

    def contentIsRead(implicit ctx: Ctx, unsafe: Unsafe): Boolean =
      ctx.channel().attr(isReadKey).get()

    def hasChanged(r1: Response, r2: Response)(implicit unsafe: Unsafe): Boolean =
      (r1.status eq r2.status) && (r1.body eq r2.body) && (r1.headers eq r2.headers)

    def releaseRequest(jReq: FullHttpRequest, cnt: Int = 1)(implicit unsafe: Unsafe): Unit = {
      if (jReq.refCnt() > 0 && cnt > 0) {
        jReq.release(cnt): Unit
      }
    }

    def setAutoRead(cond: Boolean)(implicit ctx: Ctx, unsafe: Unsafe): Unit = {
      log.debug(s"Setting channel auto-read to: [${cond}]")
      ctx.channel().config().setAutoRead(cond): Unit
    }

    def setContentReadAttr(flag: Boolean)(implicit ctx: Ctx, unsafe: Unsafe): Unit = {
      ctx.channel().attr(isReadKey).set(flag)
    }

    /**
     * Sets the server time on the response if required
     */
    def setServerTime(time: ServerTime, response: Response, jResponse: HttpResponse)(implicit unsafe: Unsafe): Unit = {
      if (response.attribute.serverTime)
        jResponse.headers().set(HttpHeaderNames.DATE, time.refreshAndGet()): Unit
    }

  }

}
