package zio.http.netty.server

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.handler.codec.http._
import zio._
import zio.http._
import zio.http.netty.{NettyRuntime, _}
import zio.logging.Logger
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;
import java.net.InetSocketAddress
import zio.http.model._

@Sharable
private[zio] final case class ServerInboundHandler(
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

    def makeZioRequest(nettyReq: HttpRequest): Request = {
      val protocolVersion = Versions.make(nettyReq.protocolVersion())

      val remoteAddress = ctx.channel().remoteAddress() match {
        case m: InetSocketAddress => Some(m.getAddress)
        case _                    => None
      }

      nettyReq match {
        case nettyReq: FullHttpRequest =>
          Request(
            Body.fromByteBuf(nettyReq.content()),
            Headers.make(nettyReq.headers()),
            Method.fromHttpMethod(nettyReq.method()),
            URL.fromString(nettyReq.uri()).getOrElse(URL.empty),
            protocolVersion,
            remoteAddress,
          )
        case nettyReq: HttpRequest     =>
          val body = Body.fromAsync { async =>
            ctx.addAsyncBodyHandler(async)
          }
          Request(
            body,
            Headers.make(nettyReq.headers()),
            Method.fromHttpMethod(nettyReq.method()),
            URL.fromString(nettyReq.uri()).getOrElse(URL.empty),
            protocolVersion,
            remoteAddress,
          )
      }

    }

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
        val req  = makeZioRequest(jReq)
        val exit = appRef.get.execute(req)

        if (ctx.attemptFastWrite(exit, time)) {
          releaseRequest(jReq)
        } else
          runtime.run(ctx) {
            ctx.attemptFullWrite(exit, jReq, time, runtime) ensuring ZIO.succeed { releaseRequest(jReq) }
          }

      case jReq: HttpRequest =>
        log.debug(s"HttpRequest: [${jReq.method()} ${jReq.uri()}]")
        val req  = makeZioRequest(jReq)
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

  val layer = {
    implicit val trace: Trace = Trace.empty
    ZLayer.fromZIO {
      for {
        appRef      <- ZIO.service[AppRef]
        errCallback <- ZIO.service[ErrorCallbackRef]
        rtm         <- ZIO.service[NettyRuntime]
        config      <- ZIO.service[ServerConfig]
        time        <- ZIO.service[service.ServerTime]

      } yield ServerInboundHandler(appRef, config, errCallback, rtm, time)
    }
  }

}
