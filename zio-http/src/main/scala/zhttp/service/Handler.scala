package zhttp.service

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, ChannelInboundHandlerAdapter}
import io.netty.handler.codec.http._
import zhttp.http._
import zhttp.service.server.content.handlers.ServerResponseHandler
import zhttp.service.server.{ServerTime, WebSocketUpgrade}
import zio.{UIO, ZIO}

import java.net.{InetAddress, InetSocketAddress}

@Sharable
private[zhttp] final case class Handler[R](
  app: HttpApp[R, Throwable],
  runtime: HttpRuntime[R],
  config: Server.Config[R, Throwable],
  serverTimeGenerator: ServerTime,
) extends ChannelInboundHandlerAdapter
    with WebSocketUpgrade[R]
    with ServerResponseHandler[R] { self =>

  override def handlerAdded(ctx: Ctx): Unit = {
    if (config.objectAggregator < 0) {
      ctx.channel().config().setAutoRead(false): Unit
      ctx.read(): Unit
    }
  }

  override def channelRead(ctx: Ctx, msg: Any): Unit = {

    implicit val iCtx: ChannelHandlerContext = ctx
    msg match {
      case jReq: FullHttpRequest =>
        jReq.touch("server.Handler-channelRead0")
        unsafeRun(
          jReq,
          app,
          new Request {
            override def method: Method = Method.fromHttpMethod(jReq.method())

            override def url: URL = URL.fromString(jReq.uri()).getOrElse(null)

            override def headers: Headers = Headers.make(jReq.headers())

            override def remoteAddress: Option[InetAddress] = {
              ctx.channel().remoteAddress() match {
                case m: InetSocketAddress => Some(m.getAddress)
                case _                    => None
              }
            }

            override def data: HttpData = HttpData.fromByteBuf(jReq.content())
          },
        )
      case jReq: HttpRequest     =>
        unsafeRun(
          jReq,
          app,
          new Request {
            override def data: HttpData = HttpData.Asynchronous(callback =>
              ctx
                .pipeline()
                .addAfter(HTTP_REQUEST_HANDLER, HTTP_CONTENT_HANDLER, new RequestBodyHandler(callback)): Unit,
            )

            override def headers: Headers = Headers.make(jReq.headers())

            override def method: Method = Method.fromHttpMethod(jReq.method())

            override def remoteAddress: Option[InetAddress] = {
              ctx.channel().remoteAddress() match {
                case m: InetSocketAddress => Some(m.getAddress)
                case _                    => None
              }
            }

            override def url: URL = URL.fromString(jReq.uri()).getOrElse(null)
          },
        )

      case msg: HttpContent => ctx.fireChannelRead(msg): Unit

      case _ =>
        ctx.writeAndFlush(new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_ACCEPTABLE)): Unit

    }

  }
  private def upgradeWebsocket(req: HttpRequest, response: Response)(implicit ctx: Ctx): Unit = {
    req match {
      case request: FullHttpRequest => self.upgradeToWebSocket(ctx, request, response)
      case jReq: HttpRequest        =>
        val fullRequest = new DefaultFullHttpRequest(jReq.protocolVersion(), jReq.method(), jReq.uri())
        fullRequest.headers().setAll(jReq.headers())
        self.upgradeToWebSocket(ctx, fullRequest, response)
    }
  }

  /**
   * Executes http apps
   */
  private def unsafeRun[A](
    jReq: HttpRequest,
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
                  writeResponse(
                    Response.fromHttpError(HttpError.InternalServerError(cause = Some(cause))),
                    jReq,
                  )
                }
              case None        =>
                UIO {
                  writeResponse(Response.status(Status.NOT_FOUND), jReq)
                }

            },
            res =>
              if (self.isWebSocket(res)) UIO(upgradeWebsocket(jReq, res))
              else {
                for {
                  _ <- ZIO {
                    writeResponse(res, jReq)
                  }
                } yield ()
              },
          )
        }

      case HExit.Success(res) =>
        if (self.isWebSocket(res)) {
          upgradeWebsocket(jReq, res)
        } else {
          writeResponse(res, jReq): Unit
        }

      case HExit.Failure(e) =>
        writeResponse(Response.fromHttpError(HttpError.InternalServerError(cause = Some(e))), jReq): Unit

      case HExit.Empty =>
        writeResponse(Response.fromHttpError(HttpError.NotFound(Path(jReq.uri()))), jReq): Unit

    }
  }

  /**
   * Executes program
   */
  private def unsafeRunZIO(program: ZIO[R, Throwable, Any])(implicit ctx: Ctx): Unit =
    rt.unsafeRun(ctx) {
      program
    }

  override def serverTime: ServerTime = serverTimeGenerator

  override val rt: HttpRuntime[R] = runtime

  override def exceptionCaught(ctx: Ctx, cause: Throwable): Unit = {
    config.error.fold(super.exceptionCaught(ctx, cause))(f => runtime.unsafeRun(ctx)(f(cause)))
  }
}
