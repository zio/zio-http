package zio.http.netty.client

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import zio.http.netty._
import zio.http.{Request, Response}
import zio.{Promise, Trace, Unsafe}
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

final class ClientInboundStreamingHandler(
  val rtm: NettyRuntime,
  req: Request,
  promise: Promise[Throwable, Response],
)(implicit trace: Trace)
    extends SimpleChannelInboundHandler[HttpObject](false) {

  private implicit val unsafeClass: Unsafe = Unsafe.unsafe

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    writeRequest(req, ctx): Unit
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit = {
    msg match {
      case response: HttpResponse =>
        ctx.channel().config().setAutoRead(false)
        rtm.runUninterruptible(ctx) {
          promise
            .succeed(
              Response.unsafe.fromStreamingJResponse(
                ctx,
                response,
              ),
            )
        }(unsafeClass, trace)
      case content: HttpContent   =>
        ctx.fireChannelRead(content): Unit

      case err => throw new IllegalStateException(s"Client unexpected message type: ${err}")
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    rtm.run(ctx)(promise.fail(error).uninterruptible)(unsafeClass, trace)
  }

  private def encodeRequest(req: Request): HttpRequest = {
    val method   = req.method.toJava
    val jVersion = Versions.convertToZIOToNetty(req.version)

    // As per the spec, the path should contain only the relative path.
    // Host and port information should be in the headers.
    val path = req.url.relative.encode

    val encodedReqHeaders = req.headers.encode

    val headers = req.url.host match {
      case Some(value) => encodedReqHeaders.set(HttpHeaderNames.HOST, value)
      case None        => encodedReqHeaders
    }

    val h = headers
      .add(HttpHeaderNames.TRANSFER_ENCODING, "chunked")
      .add(HttpHeaderNames.USER_AGENT, "zhttp-client")

    new DefaultHttpRequest(jVersion, method, path, h)

  }

  private def writeRequest(msg: Request, ctx: ChannelHandlerContext): Unit = {
    ctx.write(encodeRequest(msg))
    rtm.run(ctx)(msg.body.write(ctx).unit)(Unsafe.unsafe, trace)
    ctx.flush(): Unit
  }

}
