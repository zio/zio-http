package zhttp.service

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import zhttp.http._
import zhttp.service.server.WebSocketUpgrade
import zio.{Task, UIO, ZIO}

import java.net.{InetAddress, InetSocketAddress}

@Sharable
private[zhttp] final case class Handler[R](
  app: HttpApp[R, Throwable],
  runtime: HttpRuntime[R],
  config: Server.Config[R, Throwable],
) extends SimpleChannelInboundHandler[FullHttpRequest](false)
    with WebSocketUpgrade[R] { self =>

  type Ctx = ChannelHandlerContext

  override def channelRead0(ctx: Ctx, jReq: FullHttpRequest): Unit = {
    jReq.touch("server.Handler-channelRead0")
    implicit val iCtx: ChannelHandlerContext = ctx
    unsafeRun(
      jReq,
      app,
      new Request {
        override def method: Method = Method.fromHttpMethod(jReq.method())

        override def url: URL = URL.fromString(jReq.uri()).getOrElse(null)

        override def getHeaders: Headers = Headers.make(jReq.headers())

        override private[zhttp] def getBodyAsByteBuf: Task[ByteBuf] = Task(jReq.content())

        override def remoteAddress: Option[InetAddress] = {
          ctx.channel().remoteAddress() match {
            case m: InetSocketAddress => Some(m.getAddress)
            case _                    => None
          }
        }
      },
    )
  }

////<<<<<<< HEAD
//  override def exceptionCaught(ctx: Ctx, cause: Throwable): Unit = {
//    config.error.fold(super.exceptionCaught(ctx, cause))(f => runtime.unsafeRun(ctx)(f(cause)))
//  }
//
//  /**
//   * Checks if an encoded version of the response exists, uses it if it does. Otherwise, it will return a fresh
//   * response. It will also set the server time if requested by the client.
//   */
//  private def encodeResponse(res: Response): HttpResponse = {
//
//    val jResponse =
//      if (res.attribute.encoded.isEmpty) res.unsafeEncode()
//      else
//        res.attribute.encoded match {
//          // Check if the encoded response exists and/or was modified.
//          case Some((oRes, jResponse)) if oRes eq res =>
//            jResponse match {
//
//              // Duplicate the response without allocating much memory
//              case response: FullHttpResponse => response.retainedDuplicate()
//              case response                   => response
//            }
//          case _                                      => res.unsafeEncode()
//        }
//
//    // Identify if the server time should be set and update if required.
//    if (res.attribute.serverTime) jResponse.headers().set(HttpHeaderNames.DATE, serverTime.refreshAndGet())
//    jResponse
//  }
//
//  private def notFoundResponse: HttpResponse = {
//    val response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND, false)
//    response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, 0)
//    response
//  }
//
//  /**
//   * Releases the FullHttpRequest safely.
//   */
//  private def releaseRequest(jReq: FullHttpRequest): Unit = {
//    if (jReq.refCnt() > 0) {
//      jReq.release(jReq.refCnt()): Unit
//    }
//  }
//
//  private def serverErrorResponse(cause: Throwable): HttpResponse = {
//    val content  = Util.prettyPrintHtml(cause)
//    val response = new DefaultFullHttpResponse(
//      HttpVersion.HTTP_1_1,
//      HttpResponseStatus.INTERNAL_SERVER_ERROR,
//      Unpooled.copiedBuffer(content, HTTP_CHARSET),
//    )
//    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.length)
//    response
//  }
//
//=======
//>>>>>>> dfd11acb853e6f5daf068d191193cde7dd8d146e
  /**
   * Executes http apps
   */
  private def unsafeRun[A](
    jReq: FullHttpRequest,
    http: Http[R, Throwable, A, Response],
    a: A,
  )(implicit ctx: Ctx): Unit = {
    http.execute(a) match {
      case HExit.Effect(resM) =>
        unsafeRunZIO {
          resM.foldM(
            {
              case Some(cause) =>
                UIO {
                  ctx.fireChannelRead(
                    (Response.fromHttpError(HttpError.InternalServerError(cause = Some(cause))), jReq),
                  )
                }
              case None        =>
                UIO {
                  ctx.fireChannelRead((Response.status(Status.NOT_FOUND), jReq))
                }
            },
            res =>
              if (self.isWebSocket(res)) UIO(self.upgradeToWebSocket(ctx, jReq, res))
              else {
                for {
                  _ <- UIO {
                    ctx.fireChannelRead((res, jReq))
                  }
                } yield ()
              },
          )
        }

      case HExit.Success(res) =>
        if (self.isWebSocket(res)) {
          self.upgradeToWebSocket(ctx, jReq, res)
        } else {
          ctx.fireChannelRead((res, jReq)): Unit
        }

      case HExit.Failure(e) =>
        ctx.fireChannelRead((e, jReq)): Unit
      case HExit.Empty      =>
        ctx.fireChannelRead((Response.status(Status.NOT_FOUND), jReq)): Unit
    }

  }

  /**
   * Executes program
   */
  private def unsafeRunZIO(program: ZIO[R, Throwable, Any])(implicit ctx: Ctx): Unit =
    runtime.unsafeRun(ctx) {
      program
    }
}
