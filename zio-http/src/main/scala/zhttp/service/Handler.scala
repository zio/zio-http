package zhttp.service

import io.netty.buffer.{ByteBuf, Unpooled}
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import zhttp.http._
import zhttp.service.server.{ServerTimeGenerator, WebSocketUpgrade}
import zio.stream.ZStream
import zio.{Task, UIO, ZIO}

import java.net.{InetAddress, InetSocketAddress}

@Sharable
private[zhttp] final case class Handler[R](
  app: HttpApp[R, Throwable],
  runtime: HttpRuntime[R],
  config: Server.Config[R, Throwable],
  serverTime: ServerTimeGenerator,
) extends SimpleChannelInboundHandler[FullHttpRequest](false)
    with WebSocketUpgrade[R] { self =>

  override def channelRead0(ctx: ChannelHandlerContext, jReq: FullHttpRequest): Unit = {
    implicit val iCtx: ChannelHandlerContext = ctx
    val headers                              = Headers.make(jReq.headers())
    val content                              = Unpooled.copiedBuffer(jReq.content())
    unsafeRun(
      jReq,
      app,
      new Request {
        override def method: Method = Method.fromHttpMethod(jReq.method())

        override def url: URL = URL.fromString(jReq.uri()).getOrElse(null)

        override def getHeaders: Headers = headers

        override private[zhttp] def getBodyAsByteBuf: Task[ByteBuf] = Task(content)

        override def remoteAddress: Option[InetAddress] = {
          ctx.channel().remoteAddress() match {
            case m: InetSocketAddress => Some(m.getAddress())
            case _                    => None
          }
        }
      },
    )
  }

  /**
   * Releases the FullHttpRequest safely.
   */
  private def releaseRequest(jReq: FullHttpRequest): Unit = {
    if (jReq.refCnt() > 0) {
      jReq.release(jReq.refCnt()): Unit
    }
  }

  /**
   * Executes http apps
   */
  private def unsafeRun[A](
    jReq: FullHttpRequest,
    http: Http[R, Throwable, A, Response[R, Throwable]],
    a: A,
  )(implicit ctx: ChannelHandlerContext): Unit = {
    http.execute(a) match {
      case HExit.Effect(resM) =>
        unsafeRunZIO {
          resM.foldM(
            {
              case Some(cause) =>
                UIO {
                  unsafeWriteAndFlushErrorResponse(cause)
                  releaseRequest(jReq)
                }
              case None        =>
                UIO {
                  unsafeWriteAndFlushEmptyResponse()
                  releaseRequest(jReq)
                }
            },
            res =>
              if (self.isWebSocket(res)) UIO(self.upgradeToWebSocket(ctx, jReq, res))
              else {
                for {
                  _ <- UIO { unsafeWriteAnyResponse(res) }
                  _ <- res.data match {
                    case HttpData.Empty =>
                      UIO {
                        unsafeWriteAndFlushLastEmptyContent()
                      }

                    case data @ HttpData.Text(_, _) =>
                      UIO {
                        unsafeWriteLastContent(data.encodeAndCache(res.attribute.memoize))
                      }

                    case HttpData.BinaryByteBuf(data) =>
                      UIO {
                        unsafeWriteLastContent(data)
                      }

                    case data @ HttpData.BinaryChunk(_) =>
                      UIO {
                        unsafeWriteLastContent(data.encodeAndCache(res.attribute.memoize))
                      }

                    case HttpData.BinaryStream(data) =>
                      UIO {
                        unsafeWriteStreamContent(data)
                      }
                  }
                  _ <- Task(releaseRequest(jReq))
                } yield ()
              },
          )
        }

      case HExit.Success(res) =>
        if (self.isWebSocket(res)) {
          self.upgradeToWebSocket(ctx, jReq, res)
        } else {
          unsafeWriteAnyResponse(res)

          res.data match {
            case HttpData.Empty =>
              unsafeWriteAndFlushLastEmptyContent()

            case data @ HttpData.Text(_, _) =>
              unsafeWriteLastContent(data.encodeAndCache(res.attribute.memoize))

            case HttpData.BinaryByteBuf(data) =>
              unsafeWriteLastContent(data)

            case data @ HttpData.BinaryChunk(_) =>
              unsafeWriteLastContent(data.encodeAndCache(res.attribute.memoize))

            case HttpData.BinaryStream(stream) =>
              unsafeWriteStreamContent(stream)

          }
          releaseRequest(jReq)
        }

      case HExit.Failure(e) =>
        unsafeWriteAndFlushErrorResponse(e)
        releaseRequest(jReq)
      case HExit.Empty      =>
        unsafeWriteAndFlushEmptyResponse()
        releaseRequest(jReq)
    }

  }

  /**
   * Executes program
   */
  private def unsafeRunZIO(program: ZIO[R, Throwable, Any])(implicit ctx: ChannelHandlerContext): Unit =
    runtime.unsafeRun(ctx) {
      program
    }

  /**
   * Writes not found error response to the Channel
   */
  private def unsafeWriteAndFlushEmptyResponse()(implicit ctx: ChannelHandlerContext): Unit = {
    val response = Response(Status.NOT_FOUND)
    ctx.fireChannelRead(Right(response)): Unit
  }

  /**
   * Writes error response to the Channel
   */
  private def unsafeWriteAndFlushErrorResponse(cause: Throwable)(implicit ctx: ChannelHandlerContext): Unit = {
    val response = Left(cause)
    ctx.fireChannelRead(response): Unit
  }

  /**
   * Writes last empty content to the Channel
   */
  private def unsafeWriteAndFlushLastEmptyContent()(implicit ctx: ChannelHandlerContext): Unit = {
    val response = Response(Status.OK)
    ctx.fireChannelRead(Right(response)): Unit
  }

  /**
   * Writes any response to the Channel
   */
  private def unsafeWriteAnyResponse[A](res: Response[R, Throwable])(implicit ctx: ChannelHandlerContext): Unit = {
    ctx.fireChannelRead(Right(res)): Unit
  }

  /**
   * Writes ByteBuf data to the Channel
   */
  private def unsafeWriteLastContent[A](data: ByteBuf)(implicit ctx: ChannelHandlerContext): Unit = {
    val response = Response(status = Status.OK, data = HttpData.BinaryByteBuf(Unpooled.copiedBuffer(data)))
    ctx.fireChannelRead(Right(response)): Unit
  }

  private def unsafeWriteStreamContent[A](
    stream: ZStream[R, Throwable, ByteBuf],
  )(implicit ctx: ChannelHandlerContext): Unit = {
    val response = Response(status = Status.OK, data = HttpData.fromByteBufStream(stream))
    ctx.fireChannelRead(Right(response)): Unit
  }

}
