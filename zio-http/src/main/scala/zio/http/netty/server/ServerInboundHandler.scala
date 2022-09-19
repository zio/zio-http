package zio.http.netty.server

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.handler.codec.http._
import zio._
import zio.http._
import zio.http.netty.{NettyRuntime, _}
import zio.logging.Logger
import zio.stacktracer.TracingImplicits.disableAutoTrace

@Sharable
private[zio] final case class ServerInboundHandler(
  // driverCtx: Driver.Context,
  appRef: AppRef,
  config: ServerConfig,
  errCallbackRef: ErrorCallbackRef,
  runtime: NettyRuntime,
  time: service.ServerTime,
)(implicit trace: Trace)
    extends SimpleChannelInboundHandler[HttpObject](false) { self =>
  import ServerInboundHandler.log

  implicit private val unsafe: Unsafe = Unsafe.unsafe

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit = {

    def canHaveBody(jReq: HttpRequest): Boolean = {
      jReq.method() == HttpMethod.TRACE ||
      jReq.headers().contains(HttpHeaderNames.CONTENT_LENGTH) ||
      jReq.headers().contains(HttpHeaderNames.TRANSFER_ENCODING)
    }

    def releaseRequest(jReq: FullHttpRequest, cnt: Int = 1): Unit = {
      if (jReq.refCnt() > 0 && cnt > 0) {
        jReq.release(cnt): Unit
      }
    }

    log.debug(s"Message: [${msg.getClass.getName}]")
    msg match {
      case jReq: FullHttpRequest =>
        log.debug(s"FullHttpRequest: [${jReq.method()} ${jReq.uri()}]")
        val req  = Request.fromFullHttpRequest(jReq)(ctx)
        val exit = appRef.get.execute(req)

        if (ctx.attemptFastWrite(exit, time)) {
          releaseRequest(jReq)
        } else
          runtime.run(ctx) {
            ctx.attemptFullWrite(exit, jReq, time, runtime) ensuring ZIO.succeed { releaseRequest(jReq) }
          }

      case jReq: HttpRequest =>
        log.debug(s"HttpRequest: [${jReq.method()} ${jReq.uri()}]")
        val req  = Request.fromHttpRequest(jReq)(ctx)
        val exit = appRef.get.execute(req)

        if (!ctx.attemptFastWrite(exit, time)) {
          if (canHaveBody(jReq)) ctx.setAutoRead(false)
          runtime.run(ctx) {
            ctx.attemptFullWrite(exit, jReq, time, runtime) ensuring ZIO.succeed(ctx.setAutoRead(true)(unsafe))
          }
        }

      case msg: HttpContent => ctx.fireChannelRead(msg): Unit

      case _ =>
        throw new IllegalStateException(s"Unexpected message type: ${msg.getClass.getName}")
    }

  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    errCallbackRef.get().fold(super.exceptionCaught(ctx, cause))(f => runtime.run(ctx)(f(cause)))
  }
}

object ServerInboundHandler {
  val log: Logger = service.Log.withTags("Server", "Request")

  def layer(implicit trace: Trace) = ZLayer.fromZIO {
    for {
      appRef      <- ZIO.service[AppRef]
      errCallback <- ZIO.service[ErrorCallbackRef]
      rtm         <- ZIO.service[NettyRuntime]
      config      <- ZIO.service[ServerConfig]
      time        <- ZIO.service[service.ServerTime]

    } yield ServerInboundHandler(appRef, config, errCallback, rtm, time)
  }

}
