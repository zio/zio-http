package zio.http.netty.client

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import zio.http.netty.NettyRuntime
import zio.http.{Request, Response}
import zio.{Promise, Unsafe}

final class ClientInboundStreamingHandler(
  val rtm: NettyRuntime,
  req: Request,
  onResponse: Promise[Throwable, Response],
  onComplete: Promise[Throwable, Unit],
) extends SimpleChannelInboundHandler[HttpObject](false) {

  private implicit val unsafeClass: Unsafe = Unsafe.unsafe

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    writeRequest(req, ctx): Unit
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit = {
    msg match {
      case response: HttpResponse =>
        ctx.channel().config().setAutoRead(false)
        rtm.runUninterruptible(ctx) {
          onResponse
            .succeed(
              Response.unsafe.fromStreamingJResponse(
                ctx,
                response,
                rtm,
                onComplete,
                HttpUtil.isKeepAlive(response),
              ),
            )
        }(unsafeClass)
      case content: HttpContent   =>
        ctx.fireChannelRead(content): Unit

      case err => throw new IllegalStateException(s"Client unexpected message type: ${err}")
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    rtm.runUninterruptible(ctx)(
      onResponse.fail(error) *> onComplete.fail(error),
    )(unsafeClass)
  }

  private def encodeRequest(req: Request): HttpRequest = {
    val method   = req.method.toJava
    val jVersion = req.version.toJava

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
    rtm.run(ctx)(msg.body.write(ctx).unit)(Unsafe.unsafe)
  }

}
