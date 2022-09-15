package zio.http.service

import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http._
import zio.Unsafe
import zio.http.Request

private[http] trait ClientRequestHandler[R] {
  type Ctx = ChannelHandlerContext
  val zExec: HttpRuntime[R]

  def writeRequest(msg: Request)(implicit ctx: Ctx): Unit = {
    ctx.write(encodeRequest(msg))
    zExec.run(msg.body.write(ctx).unit)(ctx, Unsafe.unsafe)
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
}
