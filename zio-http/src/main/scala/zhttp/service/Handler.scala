package zhttp.service

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import zhttp.http._
import zhttp.service.server.WebSocketUpgrade
import zhttp.service.server.content.handlers.ServerResponseHandler
import zio.{UIO, ZIO}

import java.net.{InetAddress, InetSocketAddress}

@Sharable
private[zhttp] final case class Handler[R](
  app: HttpApp[R, Throwable],
  handler: ServerResponseHandler[R],
) extends SimpleChannelInboundHandler[HttpObject](false)
    with WebSocketUpgrade[R] { self =>

  override def channelRead0(ctx: handler.Ctx, msg: HttpObject): Unit = {

    implicit val iCtx: ChannelHandlerContext = ctx
    msg match {
      case jReq: FullHttpRequest =>
        jReq.touch("server.Handler-channelRead0")
        try
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

              override def data: HttpData   = HttpData.fromByteBuf(jReq.content())
              override def version: Version = Version.unsafeFromJava(jReq.protocolVersion())

              /**
               * Gets the HttpRequest
               */
              override def unsafeEncode = jReq

            },
          )
        catch {
          case throwable: Throwable =>
            handler.writeResponse(
              Response
                .fromHttpError(HttpError.InternalServerError(cause = Some(throwable)))
                .withConnection(HeaderValues.close),
              jReq,
            ): Unit
        }
      case jReq: HttpRequest     =>
        if (canHaveBody(jReq)) {
          ctx.channel().config().setAutoRead(false): Unit
        }
        try
          unsafeRun(
            jReq,
            app,
            new Request {
              override def data: HttpData = HttpData.UnsafeAsync(callback =>
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

              override def url: URL         = URL.fromString(jReq.uri()).getOrElse(null)
              override def version: Version = Version.unsafeFromJava(jReq.protocolVersion())

              /**
               * Gets the HttpRequest
               */
              override def unsafeEncode = jReq
            },
          )
        catch {
          case throwable: Throwable =>
            handler.writeResponse(
              Response
                .fromHttpError(HttpError.InternalServerError(cause = Some(throwable)))
                .withConnection(HeaderValues.close),
              jReq,
            ): Unit
        }

      case msg: HttpContent =>
        ctx.fireChannelRead(msg): Unit

      case _ =>
        throw new IllegalStateException(s"Unexpected message type: ${msg.getClass.getName}")

    }

  }

  private def canHaveBody(req: HttpRequest): Boolean = req.method() match {
    case HttpMethod.GET | HttpMethod.HEAD | HttpMethod.OPTIONS | HttpMethod.TRACE => false
    case _                                                                        => true
  }

  /**
   * Executes http apps
   */
  private def unsafeRun[A](
    jReq: HttpRequest,
    http: Http[R, Throwable, A, Response],
    a: A,
  )(implicit ctx: handler.Ctx): Unit = {
    http.execute(a) match {
      case HExit.Effect(resM) =>
        unsafeRunZIO {
          resM.foldCauseM(
            cause =>
              cause.failureOrCause match {
                case Left(Some(cause)) =>
                  UIO {
                    handler.writeResponse(
                      Response.fromHttpError(HttpError.InternalServerError(cause = Some(cause))),
                      jReq,
                    )
                  }
                case Left(None)        =>
                  UIO {
                    handler.writeResponse(Response.status(Status.NotFound), jReq)
                  }
                case Right(other)      =>
                  other.dieOption match {
                    case Some(defect) =>
                      UIO {
                        handler.writeResponse(
                          Response.fromHttpError(HttpError.InternalServerError(cause = Some(defect))),
                          jReq,
                        )
                      }
                    case None         =>
                      ZIO.halt(other)
                  }
              },
            res =>
              if (self.isWebSocket(res)) UIO(self.upgradeToWebSocket(jReq, res))
              else {
                for {
                  _ <- ZIO {
                    handler.writeResponse(res, jReq)
                  }
                } yield ()
              },
          )
        }

      case HExit.Success(res) =>
        if (self.isWebSocket(res)) {
          self.upgradeToWebSocket(jReq, res)
        } else {
          handler.writeResponse(res, jReq): Unit
        }

      case HExit.Failure(e) =>
        handler.writeResponse(Response.fromHttpError(HttpError.InternalServerError(cause = Some(e))), jReq): Unit

      case HExit.Die(e) =>
        handler.writeResponse(Response.fromHttpError(HttpError.InternalServerError(cause = Some(e))), jReq): Unit

      case HExit.Empty =>
        handler.writeResponse(Response.fromHttpError(HttpError.NotFound(Path(jReq.uri()))), jReq): Unit

    }
  }

  /**
   * Executes program
   */
  private def unsafeRunZIO(program: ZIO[R, Throwable, Any])(implicit ctx: handler.Ctx): Unit =
    handler.rt.unsafeRun(ctx) {
      program
    }

  override val runtime: HttpRuntime[R] = handler.rt

  override def exceptionCaught(ctx: handler.Ctx, cause: Throwable): Unit = {
    handler.config.error.fold(super.exceptionCaught(ctx, cause))(f => runtime.unsafeRun(ctx)(f(cause)))
  }
}
