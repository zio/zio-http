package zhttp.service.server.content.handlers
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}

object UnsafeRequestHandler {

  final case class UnsafeContent(content: HttpContent) extends AnyVal {
    def isLast: Boolean = content.isInstanceOf[LastHttpContent]
  }

  final case class UnsafeChannel(ctx: ChannelHandlerContext) extends AnyVal {

    def write(content: HttpContent): Unit         = ctx.write(content): Unit
    def writeAndFlush(content: HttpContent): Unit = ctx.writeAndFlush(content): Unit
    def read(): Unit                              = ctx.read(): Unit
    def close(): Unit                             = ctx.close(): Unit
    def flush(): Unit                             = ctx.flush(): Unit

  }

}
