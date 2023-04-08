package zio.http.netty

import zio.{Chunk, ChunkBuilder, Trace, Unsafe}

import zio.http.netty.AsyncBodyReader.State
import zio.http.netty.NettyBody.UnsafeAsync

import io.netty.buffer.ByteBufUtil
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http.{HttpContent, LastHttpContent}

abstract class AsyncBodyReader(implicit trace: Trace) extends SimpleChannelInboundHandler[HttpContent](true) {

  protected val unsafeClass: Unsafe = Unsafe.unsafe

  private var state: State                                 = State.Buffering
  private val buffer: ChunkBuilder[(Chunk[Byte], Boolean)] = ChunkBuilder.make[(Chunk[Byte], Boolean)]()
  private var previousAutoRead: Boolean                    = false
  private var ctx: ChannelHandlerContext                   = _

  def connect(callback: UnsafeAsync): Unit = {
    this.synchronized {
      state match {
        case State.Buffering =>
          state = State.Direct(callback)
          buffer.result().foreach { case (chunk, isLast) =>
            callback(null, chunk, isLast)
          }
          ctx.read()
        case State.Direct(_) =>
          throw new IllegalStateException("Cannot connect twice")
      }
    }
  }

  override def handlerAdded(ctx: ChannelHandlerContext): Unit = {
    previousAutoRead = ctx.channel().config().isAutoRead
    ctx.channel().config().setAutoRead(false)
    this.ctx = ctx
  }

  override def handlerRemoved(ctx: ChannelHandlerContext): Unit = {
    ctx.channel().config().setAutoRead(previousAutoRead)
  }

  override def channelRead0(
    ctx: ChannelHandlerContext,
    msg: HttpContent,
  ): Unit = {
    val isLast = msg.isInstanceOf[LastHttpContent]
    val chunk  = Chunk.fromArray(ByteBufUtil.getBytes(msg.content()))

    this.synchronized {
      state match {
        case State.Buffering        =>
          buffer += ((chunk, isLast))
        case State.Direct(callback) =>
          callback(ctx.channel(), chunk, isLast)
          ctx.read()
      }
    }

    if (isLast) {
      ctx.channel().pipeline().remove(this)
    }: Unit
  }
}

object AsyncBodyReader {
  sealed trait State

  object State {
    case object Buffering extends State

    final case class Direct(callback: UnsafeAsync) extends State
  }
}
