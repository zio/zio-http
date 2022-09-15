package zio.http
package netty
package server

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.handler.codec.http._
import zio._
import zio.http.Server.ErrorCallback
import zio.http._
import zio.logging.Logger

import java.util.concurrent.atomic.AtomicReference

@Sharable
private[zio] final case class ServerInboundHandler(
  appRef: AtomicReference[HttpApp[Any, Throwable]],
  runtime: NettyRuntime,
  config: ServerConfig,
  time: service.ServerTime,
  onError: AtomicReference[Option[ErrorCallback]],
) extends SimpleChannelInboundHandler[HttpObject](false) { self =>
  import ServerInboundHandler.{unsafeOps, log}

  implicit private val unsafe: Unsafe = Unsafe.unsafe

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit = {
    log.debug(s"Message: [${msg.getClass.getName}]")
    msg match {
      case jReq: FullHttpRequest =>
        log.debug(s"FullHttpRequest: [${jReq.method()} ${jReq.uri()}]")
        val req  = Request.fromFullHttpRequest(jReq)(ctx)
        val exit = appRef.get.execute(req)

        if (ctx.attemptFastWrite(exit, time)) {
          unsafeOps.releaseRequest(jReq)
        } else
          runtime.run(ctx) {
            ctx.attemptFullWrite(exit, jReq, time, runtime) ensuring ZIO.succeed { unsafeOps.releaseRequest(jReq) }
          }

      case jReq: HttpRequest =>
        log.debug(s"HttpRequest: [${jReq.method()} ${jReq.uri()}]")
        val req  = Request.fromHttpRequest(jReq)(ctx)
        val exit = appRef.get.execute(req)

        if (!ctx.attemptFastWrite(exit, time)) {
          if (unsafeOps.canHaveBody(jReq)) ctx.setAutoRead(false)
          runtime.run(ctx) {
            ctx.attemptFullWrite(exit, jReq, time, runtime) ensuring ZIO.succeed(ctx.setAutoRead(true))
          }
        }

      case msg: HttpContent => ctx.fireChannelRead(msg): Unit

      case _ =>
        throw new IllegalStateException(s"Unexpected message type: ${msg.getClass.getName}")
    }

  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    onError.get.fold(super.exceptionCaught(ctx, cause))(f => runtime.run(ctx)(f(cause)))
  }
}

object ServerInboundHandler {
  val log: Logger = service.Log.withTags("Server", "Request")

  def layer = ZLayer.fromZIO {
    for {
      appRef <- ZIO.service[AtomicReference[HttpApp[Any, Throwable]]]
      errRef <- ZIO.service[AtomicReference[Option[ErrorCallback]]]
      rtm    <- ZIO.service[NettyRuntime]
      config <- ZIO.service[ServerConfig]
      time   <- ZIO.service[service.ServerTime]

    } yield ServerInboundHandler(appRef, rtm, config, time, errRef)
  }

  object unsafeOps {

    def canHaveBody(jReq: HttpRequest)(implicit unsafe: Unsafe): Boolean = {
      jReq.method() == HttpMethod.TRACE ||
      jReq.headers().contains(HttpHeaderNames.CONTENT_LENGTH) ||
      jReq.headers().contains(HttpHeaderNames.TRANSFER_ENCODING)
    }

    def releaseRequest(jReq: FullHttpRequest, cnt: Int = 1)(implicit unsafe: Unsafe): Unit = {
      if (jReq.refCnt() > 0 && cnt > 0) {
        jReq.release(cnt): Unit
      }
    }

  }

}
