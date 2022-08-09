package zhttp.service

import io.netty.handler.codec.http.FullHttpResponse
import zhttp.http.{HExit, Response}

/**
 * An executor that evaluates HExits that don't fail or require any side-effects
 * to be performed. The executor returns true if the response is completely
 * written on the channel.
 */
trait FastPassWriter[R] { self: Handler[R] =>
  import Handler.{Unsafe, log}

  def attemptFastWrite(exit: HExit[R, Throwable, Response])(implicit ctx: Ctx): Boolean = {
    exit match {
      case HExit.Success(response) =>
        response.attribute.encoded match {
          case Some((oResponse, jResponse: FullHttpResponse)) if Unsafe.hasChanged(response, oResponse) =>
            val djResponse = jResponse.retainedDuplicate()
            Unsafe.setServerTime(time, response, djResponse)
            ctx.writeAndFlush(djResponse): Unit
            log.debug("Fast write performed")
            true

          case _ => false
        }
      case _                       => false
    }
  }
}
