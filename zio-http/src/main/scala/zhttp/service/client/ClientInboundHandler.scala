package zhttp.service.client

import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import io.netty.util.ReferenceCountUtil
import zhttp.http.{HTTP_CHARSET, HttpData, Request, Response}
import zhttp.service.server.content.handlers.{ClientRequestHandler, ClientResponseHandler}
import zhttp.service.{CLIENT_INBOUND_HANDLER, Client, HTTP_CLIENT_CONTENT_HANDLER, HttpRuntime}
import zio.Promise

/**
 * Handles HTTP response
 */
final class ClientInboundHandler[R](
  val rt: HttpRuntime[R],
  req: Request,
  promise: Promise[Throwable, Response],
  isWebSocket: Boolean,
  val config: Client.Config,
) extends SimpleChannelInboundHandler[HttpObject](true)
    with ClientRequestHandler[R] {

  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    if (isWebSocket) {
      ctx.fireChannelActive(): Unit
    } else {
      writeRequest(req)(ctx)
      ()
    }
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit = {

    msg match {
      case response: FullHttpResponse =>
        response.touch("handlers.ClientInboundHandler-channelRead0")

        rt.unsafeRun(ctx)(promise.succeed(Response.unsafeFromJResponse(response)))
        if (isWebSocket) {
          ctx.fireChannelRead(response.retain())
          ctx.pipeline().remove(ctx.name()): Unit
        }
      case response: HttpResponse     =>
        ctx.channel().config().setAutoRead(false): Unit
        val data = HttpData.Incoming(callback =>
          ctx
            .pipeline()
            .addAfter(CLIENT_INBOUND_HANDLER, HTTP_CLIENT_CONTENT_HANDLER, new ClientResponseHandler(callback)): Unit,
        )
        rt.unsafeRun(ctx) {

          promise.succeed(
            Response.unsafeFromJResponse(
              response,
              data,
            ),
          )

        }
        ctx.fireChannelRead(LastHttpContent.EMPTY_LAST_CONTENT): Unit
      case content: HttpContent       =>
        val c = ReferenceCountUtil.retain(content)
        println(c.content().toString(HTTP_CHARSET))
        ctx.fireChannelRead(c): Unit
      // ctx.fireChannelRead(ReferenceCountUtil.retain(content)): Unit

      case err => throw new IllegalStateException(s"Client unexpected message type: ${err}")
    }

  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    rt.unsafeRun(ctx)(promise.fail(error))
  }

}
