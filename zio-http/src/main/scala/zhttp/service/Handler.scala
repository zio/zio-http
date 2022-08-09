package zhttp.service

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import zhttp.http._
import zhttp.logging.Logger
import zhttp.service.Handler.{FastPassWriter, FullPassWriter, Unsafe, log}
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
            self.attemptFullWrite(exit, jReq) ensuring ZIO.succeed {
              Unsafe.releaseRequest(jReq)
              log.debug("Full write performed")
            }
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

  /**
   * An executor that evaluates HExits that don't fail or require a side-effect
   * to be performed. The executor returns true if the response is completely
   * written on the channel.
   */
  trait FastPassWriter[R] {
    self: Handler[R] =>
    def attemptFastWrite(exit: HExit[R, Throwable, Response])(implicit ctx: Ctx): Boolean = {

      exit match {
        case HExit.Success(response) =>
          response.attribute.encoded match {
            case Some((oResponse, jResponse: FullHttpResponse)) if Unsafe.hasChanged(response, oResponse) =>
              Unsafe.setServerTime(time, response, jResponse)
              ctx.writeAndFlush(jResponse.retainedDuplicate()): Unit
              log.debug("Fast write performed")
              true

            case _ => false
          }
        case _                       => false
      }
    }
  }

  trait FullPassWriter[R] { self: Handler[R] =>
    def attemptFullWrite[R1 >: R](exit: HExit[R1, Throwable, Response], jRequest: HttpRequest)(implicit
      ctx: Ctx,
    ): ZIO[R, Throwable, Unit] = {
      for {
        response <- exit.toZIO.unrefine { case error => Option(error) }.catchAll {
          case None        => ZIO.succeed(HttpError.NotFound(jRequest.uri()).toResponse)
          case Some(error) => ZIO.succeed(HttpError.InternalServerError(cause = Some(error)).toResponse)
        }
        _        <-
          if (self.isWebSocket(response)) ZIO.attempt(self.upgradeToWebSocket(jRequest, response))
          else
            for {
              jResponse <- response.encode()
              _         <- ZIO.attempt(Unsafe.setServerTime(self.time, response, jResponse))
              _         <- ZIO.attempt(ctx.writeAndFlush(jResponse))
              flushed <- if (!jResponse.isInstanceOf[FullHttpResponse]) response.body.write(ctx) else ZIO.succeed(true)
              _       <- ZIO.attempt(ctx.flush()).when(!flushed)
            } yield ()
      } yield ()

    }
  }

  object Unsafe {

    def addContentHandler(async: Body.UnsafeAsync, ctx: Ctx): Unit =
      ctx.channel().pipeline.addAfter(HTTP_REQUEST_HANDLER, HTTP_CONTENT_HANDLER, new ContentHandler(async)): Unit

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

    def hasChanged(r1: Response, r2: Response): Boolean =
      (r1.status eq r2.status) && (r1.body eq r2.body) && (r1.headers eq r2.headers)

    def releaseRequest(jReq: FullHttpRequest): Unit = {
      if (jReq.refCnt() > 0) {
        jReq.release(jReq.refCnt()): Unit
      }
    }

    def setAutoRead(cond: Boolean)(implicit ctx: Ctx): Unit = {
      log.debug(s"Setting channel auto-read to: [${cond}]")
      ctx.channel().config().setAutoRead(cond): Unit
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
