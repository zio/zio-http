package zio.http
package netty

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import io.netty.util.AttributeKey
import zio._
import zio.http._
import zio.logging.Logger

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

@Sharable
private[zio] final case class ServerInboundHandler(
  appRef: AtomicReference[HttpApp[Any, Throwable]],
  runtime: NettyRuntime,
  config: ServerConfig,
  time: service.ServerTime,
) extends SimpleChannelInboundHandler[HttpObject](false) { self =>
  import ServerInboundHandler.{Unsafe, log}

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit = {
    log.debug(s"Message: [${msg.getClass.getName}]")
    implicit val iCtx: ChannelHandlerContext = ctx
    msg match {
      case jReq: FullHttpRequest =>
        log.debug(s"FullHttpRequest: [${jReq.method()} ${jReq.uri()}]")
        val req  = Request.fromFullHttpRequest(jReq)
        val exit = appRef.get.execute(req)

        if (self.attemptFastWrite(exit, ctx)) {
          Unsafe.releaseRequest(jReq)
        } else
          runtime.unsafeRun(ctx) {
            self.attemptFullWrite(exit, jReq, ctx) ensuring ZIO.succeed { Unsafe.releaseRequest(jReq) }
          }

      case jReq: HttpRequest =>
        log.debug(s"HttpRequest: [${jReq.method()} ${jReq.uri()}]")
        val req  = Request.fromHttpRequest(jReq)
        val exit = appRef.get.execute(req)

        if (!self.attemptFastWrite(exit, ctx)) {
          if (Unsafe.canHaveBody(jReq)) Unsafe.setAutoRead(false, ctx)
          runtime.unsafeRun(ctx) {
            self.attemptFullWrite(exit, jReq, ctx) ensuring ZIO.succeed(Unsafe.setAutoRead(true, ctx))
          }
        }

      case msg: HttpContent => ctx.fireChannelRead(msg): Unit

      case _ =>
        throw new IllegalStateException(s"Unexpected message type: ${msg.getClass.getName}")
    }

  }

  private def attemptFastWrite(exit: HExit[Any, Throwable, Response], ctx: ChannelHandlerContext): Boolean = {
    exit match {
      case HExit.Success(response) =>
        response.attribute.encoded match {
          case Some((oResponse, jResponse: FullHttpResponse)) if Unsafe.hasChanged(response, oResponse) =>
            val djResponse = jResponse.retainedDuplicate()
            Unsafe.setServerTime(time, response, djResponse)
            ctx.writeAndFlush(djResponse, ctx.voidPromise()): Unit
            log.debug("Fast write performed")
            true

          case _ => false
        }
      case _                       => false
    }
  }

  private def attemptFullWrite(
    exit: HExit[Any, Throwable, Response],
    jRequest: HttpRequest,
    ctx: ChannelHandlerContext,
  ): ZIO[Any, Throwable, Unit] = {
    for {
      response <- exit.toZIO.unrefine { case error => Option(error) }.catchAll {
        case None        => ZIO.succeed(HttpError.NotFound(jRequest.uri()).toResponse)
        case Some(error) => ZIO.succeed(HttpError.InternalServerError(cause = Some(error)).toResponse)
      }
      _        <-
        if (response.isWebSocket) ZIO.attempt(self.upgradeToWebSocket(jRequest, response, ctx))
        else
          for {
            jResponse <- response.encode()
            _         <- ZIO.attempt(Unsafe.setServerTime(self.time, response, jResponse))
            _         <- ZIO.attempt(ctx.writeAndFlush(jResponse))
            flushed   <- if (!jResponse.isInstanceOf[FullHttpResponse]) response.body.write(ctx) else ZIO.succeed(true)
            _         <- ZIO.attempt(ctx.flush()).when(!flushed)
          } yield ()

      _ <- ZIO.attempt(Unsafe.setContentReadAttr(false, ctx))
    } yield log.debug("Full write performed")
  }

  /**
   * Checks if the response requires to switch protocol to websocket. Returns
   * true if it can, otherwise returns false
   */
  @tailrec
  private def upgradeToWebSocket(jReq: HttpRequest, res: Response, ctx: ChannelHandlerContext): Unit = {
    val app = res.attribute.socketApp
    jReq match {
      case jReq: FullHttpRequest =>
        log.debug(s"Upgrading to WebSocket: [${jReq.uri()}]")
        log.debug(s"SocketApp: [${app.orNull}]")
        ctx
          .channel()
          .pipeline()
          .addLast(new WebSocketServerProtocolHandler(app.get.protocol.serverBuilder.build()))
          .addLast(Names.WebSocketHandler, new WebSocketAppHandler(runtime, app.get, false))

        val retained = jReq.retainedDuplicate()
        ctx.channel().eventLoop().submit { () => ctx.fireChannelRead(retained) }: Unit

      case jReq: HttpRequest =>
        val fullRequest = new DefaultFullHttpRequest(jReq.protocolVersion(), jReq.method(), jReq.uri())
        fullRequest.headers().setAll(jReq.headers())
        self.upgradeToWebSocket(fullRequest, res, ctx)
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    // TODO: need a different way to provide the error handler

    // config.error.fold(super.exceptionCaught(ctx, cause))(f => runtime.unsafeRun(f(cause))(ctx))
  }
}

object ServerInboundHandler {
  val log: Logger = service.Log.withTags("Server", "Request")

  def layer = ZLayer.fromZIO {
    for {
      appRef <- ZIO.service[AtomicReference[HttpApp[Any, Throwable]]]
      rtm    <- ZIO.service[NettyRuntime]
      config <- ZIO.service[ServerConfig]
      time   <- ZIO.service[service.ServerTime]

    } yield ServerInboundHandler(appRef, rtm, config, time)
  }

  object Unsafe {
    private val isReadKey = AttributeKey.newInstance[Boolean]("IS_READ_KEY")

    def addAsyncBodyHandler(async: Body.UnsafeAsync, ctx: ChannelHandlerContext): Unit = {
      if (Unsafe.contentIsRead(ctx)) throw new RuntimeException("Content is already read")
      ctx
        .channel()
        .pipeline()
        .addAfter(Names.HttpRequestHandler, Names.HttpContentHandler, new ServerAsyncBodyHandler(async)): Unit
      Unsafe.setContentReadAttr(true, ctx)
    }

    /**
     * Enables auto-read if possible. Also performs the first read.
     */
    def attemptAutoRead[R, E](config: ServerConfig, ctx: ChannelHandlerContext): Unit = {
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

    def contentIsRead(ctx: ChannelHandlerContext): Boolean =
      ctx.channel().attr(isReadKey).get()

    def hasChanged(r1: Response, r2: Response): Boolean =
      (r1.status eq r2.status) && (r1.body eq r2.body) && (r1.headers eq r2.headers)

    def releaseRequest(jReq: FullHttpRequest, cnt: Int = 1): Unit = {
      if (jReq.refCnt() > 0 && cnt > 0) {
        jReq.release(cnt): Unit
      }
    }

    def setAutoRead(cond: Boolean, ctx: ChannelHandlerContext): Unit = {
      log.debug(s"Setting channel auto-read to: [${cond}]")
      ctx.channel().config().setAutoRead(cond): Unit
    }

    def setContentReadAttr(flag: Boolean, ctx: ChannelHandlerContext): Unit = {
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
