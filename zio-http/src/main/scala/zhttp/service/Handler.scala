package zhttp.service

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.{ChannelHandlerContext, SimpleChannelInboundHandler}
import io.netty.handler.codec.http._
import zhttp.http._
import zhttp.service.server.WebSocketUpgrade
import zio.{UIO, ZIO}

import java.net.{InetAddress, InetSocketAddress}

@Sharable
private[zhttp] final case class Handler[R](
  app: HttpApp[R, Throwable],
  runtime: HttpRuntime[R],
  config: Server.Config[R, Throwable],
  resWriter: ServerResponseWriter[R],
) extends SimpleChannelInboundHandler[HttpObject](false)
    with WebSocketUpgrade[R] { self =>

  override def channelRead0(ctx: Ctx, msg: HttpObject): Unit = {

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

              override def remoteAddress: Option[InetAddress] = getRemoteAddress

              override def data: HttpData   = HttpData.fromByteBuf(jReq.content())
              override def version: Version = Version.unsafeFromJava(jReq.protocolVersion())

              /**
               * Gets the HttpRequest
               */
              override def unsafeEncode: HttpRequest = jReq

            },
          )
        catch {
          case throwable: Throwable => resWriter.write(throwable, jReq)
        }
      case jReq: HttpRequest     =>
        val hasBody = canHaveBody(jReq)
        if (hasBody) ctx.channel().config().setAutoRead(false): Unit
        try
          unsafeRun(
            jReq,
            app,
            new Request {
              override def data: HttpData = if (hasBody) asyncData else HttpData.empty
              private final def asyncData =
                HttpData.UnsafeAsync(callback =>
                  ctx
                    .pipeline()
                    .addAfter(HTTP_REQUEST_HANDLER, HTTP_CONTENT_HANDLER, new RequestBodyHandler(callback)): Unit,
                )

              override def headers: Headers = Headers.make(jReq.headers())

              override def method: Method = Method.fromHttpMethod(jReq.method())

              override def remoteAddress: Option[InetAddress] = getRemoteAddress(ctx)

              override def url: URL         = URL.fromString(jReq.uri()).getOrElse(null)
              override def version: Version = Version.unsafeFromJava(jReq.protocolVersion())

              /**
               * Gets the HttpRequest
               */
              override def unsafeEncode: HttpRequest = jReq
            },
          )
        catch {
          case throwable: Throwable => resWriter.write(throwable, jReq)
        }

      case msg: HttpContent =>
        ctx.fireChannelRead(msg): Unit

      case _ =>
        throw new IllegalStateException(s"Unexpected message type: ${msg.getClass.getName}")

    }

  }

  private def getRemoteAddress(implicit ctx: Ctx) = {
    ctx.channel().remoteAddress() match {
      case m: InetSocketAddress => Some(m.getAddress)
      case _                    => None
    }
  }

  private def canHaveBody(req: HttpRequest): Boolean = {
    req.method() == HttpMethod.TRACE ||
    req.headers().get(HttpHeaderNames.CONTENT_LENGTH) != null ||
    req.headers().get(HttpHeaderNames.TRANSFER_ENCODING) != null
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
          resM.foldCauseM(
            cause =>
              cause.failureOrCause match {
                case Left(Some(cause)) =>
                  UIO { resWriter.write(cause, jReq) }
                case Left(None)        =>
                  UIO { resWriter.writeNotFound(jReq) }
                case Right(other)      =>
                  other.dieOption match {
                    case Some(defect) =>
                      UIO { resWriter.write(defect, jReq) }
                    case None         =>
                      ZIO.halt(other)
                  }
              },
            res =>
              if (self.isWebSocket(res)) UIO(self.upgradeToWebSocket(jReq, res))
              else {
                for {
                  _ <- ZIO {
                    resWriter.write(res, jReq)
                  }
                } yield ()
              },
          )
        }

      case HExit.Success(res) =>
        if (self.isWebSocket(res)) {
          self.upgradeToWebSocket(jReq, res)
        } else {
          resWriter.write(res, jReq)
        }

      case HExit.Failure(e) => resWriter.write(e, jReq)

      case HExit.Die(e) => resWriter.write(e, jReq)

      case HExit.Empty => resWriter.writeNotFound(jReq)

    }
  }

  /**
   * Executes program
   */
  private def unsafeRunZIO(program: ZIO[R, Throwable, Any])(implicit ctx: Ctx): Unit =
    runtime.unsafeRun(ctx) {
      program
    }

  override def exceptionCaught(ctx: Ctx, cause: Throwable): Unit = {
    config.error.fold(super.exceptionCaught(ctx, cause))(f => runtime.unsafeRun(ctx)(f(cause)))
  }

}
