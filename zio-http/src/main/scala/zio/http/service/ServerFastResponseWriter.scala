package zio.http.service

import io.netty.handler.codec.http.FullHttpResponse
import zio.Unsafe
import zio.http.{HExit, Response}

/**
 * An executor that evaluates HExits that don't fail or require any side-effects
 * to be performed. The executor returns true if the response is completely
 * written on the channel.
 */
trait ServerFastResponseWriter[R] { self: ServerInboundHandler[R] =>
  import ServerInboundHandler.log

  def attemptFastWrite(exit: HExit[R, Throwable, Response])(implicit ctx: Ctx, unsafe: Unsafe): Boolean =
    exit match {
      case HExit.Success(response) =>
        response.attribute.encoded match {
          case Some((oResponse, jResponse: FullHttpResponse))
              if ServerInboundHandler.unsafe.hasChanged(response, oResponse) =>
            val djResponse = jResponse.retainedDuplicate()
            ServerInboundHandler.unsafe.setServerTime(time, response, djResponse)
            ctx.writeAndFlush(djResponse, ctx.voidPromise()): Unit
            log.debug("Fast write performed")
            true

          case _ => false
        }
      case _                       => false
    }

}
