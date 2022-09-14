package zio.http
package netty

import io.netty.handler.codec.http.FullHttpResponse
import zio.http.{HExit, Response}
import io.netty.channel.ChannelHandlerContext

/**
 * An executor that evaluates HExits that don't fail or require any side-effects
 * to be performed. The executor returns true if the response is completely
 * written on the channel.
 */
private[zio] trait ServerFastResponseWriter[R] { self: ServerInboundHandler[R] =>
  import ServerInboundHandler.{log, Unsafe}

  def attemptFastWrite(exit: HExit[R, Throwable, Response])(implicit ctx: ChannelHandlerContext): Boolean = {
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
}
