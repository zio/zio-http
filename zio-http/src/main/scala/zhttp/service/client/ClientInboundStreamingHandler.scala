package zhttp.service.client
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import zhttp.http.{HttpData, Request, Response}
import zhttp.service.server.content.handlers.{ClientRequestHandler, ClientResponseStreamHandler}
import zhttp.service.HttpRuntime
import zio.Promise

final class ClientInboundStreamingHandler[R](
  val zExec: HttpRuntime[R],
  req: Request,
  promise: Promise[Throwable, Response],
) extends SimpleChannelInboundHandler[HttpObject](true)
    with ClientRequestHandler[R] {

  private val collector                                        = new ClientResponseStreamHandler
  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    writeRequest(req)(ctx)
    ()
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit = {
    msg match {
      case response: HttpResponse =>
        zExec.unsafeRun(ctx) {
          promise
            .succeed(
              Response.unsafeFromJResponse(
                response,
                HttpData.UnsafeAsync(callback => collector.init(ctx, callback)),
              ),
            )
            .uninterruptible

        }
      case content: HttpContent   =>
        ctx.pipeline().channel().config().setAutoRead(false)
        collector.update(content.retain())

      case err => throw new IllegalStateException(s"Client unexpected message type: ${err}")
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, error: Throwable): Unit = {
    zExec.unsafeRun(ctx)(promise.fail(error).uninterruptible)
  }

}
