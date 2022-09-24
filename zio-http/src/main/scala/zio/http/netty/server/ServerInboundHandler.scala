package zio.http.netty.server

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.handler.codec.http._
import zio._
import zio.http._
import zio.http.netty.{NettyRuntime, _}
import zio.logging.Logger
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;
import zio.http.model._
import io.netty.util.AttributeKey
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import scala.annotation.tailrec
import ServerInboundHandler.isReadKey
import java.io.IOException

@Sharable
private[zio] final case class ServerInboundHandler(
  appRef: AppRef,
  config: ServerConfig,
  errCallbackRef: ErrorCallbackRef,
  runtime: NettyRuntime,
  time: service.ServerTime,
)(implicit trace: Trace)
    extends SimpleChannelInboundHandler[HttpObject](false) { self =>
  import ServerInboundHandler.log

  implicit private val unsafe: Unsafe = Unsafe.unsafe

  @inline
  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit = {

    def addAsyncBodyHandler(async: Body.UnsafeAsync): Unit = {
      if (contentIsRead) throw new RuntimeException("Content is already read")
      ctx
        .channel()
        .pipeline()
        .addAfter(Names.HttpRequestHandler, Names.HttpContentHandler, new ServerAsyncBodyHandler(async)): Unit
      setContentReadAttr(flag = true)
    }

    def attemptFastWrite(exit: HExit[Any, Throwable, Response], time: service.ServerTime): Boolean = {
      exit match {
        case HExit.Success(response) =>
          response.attribute.encoded match {
            case Some((oResponse, jResponse: FullHttpResponse)) if hasChanged(response, oResponse) =>
              val djResponse = jResponse.retainedDuplicate()
              setServerTime(time, response, djResponse)
              ctx.writeAndFlush(djResponse, ctx.voidPromise()): Unit
              // log.debug("Fast write performed")
              true

            case _ => false
          }
        case _                       => false
      }
    }

    def attemptFullWrite(
      exit: HExit[Any, Throwable, Response],
      jRequest: HttpRequest,
      time: service.ServerTime,
      runtime: NettyRuntime,
    ): ZIO[Any, Throwable, Unit] = {

      for {
        response <- exit.toZIO.unrefine { case error => Option(error) }.catchAll {
          case None        => ZIO.succeed(HttpError.NotFound(jRequest.uri()).toResponse)
          case Some(error) => ZIO.succeed(HttpError.InternalServerError(cause = Some(error)).toResponse)
        }
        _        <-
          if (response.isWebSocket) ZIO.attempt(upgradeToWebSocket(jRequest, response, runtime))
          else
            for {
              jResponse <- response.encode()
              _         <- ZIO.attemptUnsafe(implicit u => setServerTime(time, response, jResponse))
              _         <- ZIO.attempt(ctx.writeAndFlush(jResponse))
              flushed <- if (!jResponse.isInstanceOf[FullHttpResponse]) response.body.write(ctx) else ZIO.succeed(true)
              _       <- ZIO.attempt(ctx.flush()).when(!flushed)
            } yield ()

        _ <- ZIO.attemptUnsafe(implicit u => setContentReadAttr(false))
      } yield log.debug("Full write performed")
    }

    def canHaveBody(jReq: HttpRequest): Boolean = {
      jReq.method() == HttpMethod.TRACE ||
      jReq.headers().contains(HttpHeaderNames.CONTENT_LENGTH) ||
      jReq.headers().contains(HttpHeaderNames.TRANSFER_ENCODING)
    }

    def contentIsRead: Boolean =
      ctx.channel().attr(isReadKey).get()

    def hasChanged(r1: Response, r2: Response): Boolean =
      (r1.status eq r2.status) && (r1.body eq r2.body) && (r1.headers eq r2.headers)

    def makeZioRequest(nettyReq: HttpRequest): Request = {
      val nettyHttpVersion = nettyReq.protocolVersion()
      val protocolVersion  = nettyHttpVersion match {
        case HttpVersion.HTTP_1_0 => Version.Http_1_0
        case HttpVersion.HTTP_1_1 => Version.Http_1_1
        case _ => throw new IllegalArgumentException(s"Unsupported HTTP version: ${nettyHttpVersion}")
      }

      // TODO: We need to bring this back, probably not part of Request.
      // val remoteAddress = ctx.channel().remoteAddress() match {
      //   case m: InetSocketAddress => Some(m.getAddress)
      //   case _                    => None
      // }

      nettyReq match {
        case nettyReq: FullHttpRequest =>
          Request(
            Body.fromByteBuf(nettyReq.content()),
            Headers.make(nettyReq.headers()),
            Method.fromHttpMethod(nettyReq.method()),
            URL.fromString(nettyReq.uri()).getOrElse(URL.empty),
            protocolVersion,
            None,
          )
        case nettyReq: HttpRequest     =>
          val body = Body.fromAsync { async =>
            addAsyncBodyHandler(async)
          }
          Request(
            body,
            Headers.make(nettyReq.headers()),
            Method.fromHttpMethod(nettyReq.method()),
            URL.fromString(nettyReq.uri()).getOrElse(URL.empty),
            protocolVersion,
            None,
          )
      }

    }

    def releaseRequest(jReq: FullHttpRequest, cnt: Int = 1): Unit = {
      if (jReq.refCnt() > 0 && cnt > 0) {
        jReq.release(cnt): Unit
      }
    }

    def setAutoRead(cond: Boolean): Unit = {
      log.debug(s"Setting channel auto-read to: [${cond}]")
      ctx.channel().config().setAutoRead(cond): Unit
    }

    def setContentReadAttr(flag: Boolean): Unit = {
      ctx.channel().attr(isReadKey).set(flag)
    }

    def setServerTime(time: service.ServerTime, response: Response, jResponse: HttpResponse): Unit = {
      if (response.attribute.serverTime)
        jResponse.headers().set(HttpHeaderNames.DATE, time.refreshAndGet()): Unit
    }

    /*
     * Checks if the response requires to switch protocol to websocket. Returns
     * true if it can, otherwise returns false
     */
    @tailrec
    def upgradeToWebSocket(jReq: HttpRequest, res: Response, runtime: NettyRuntime): Unit = {
      val app = res.attribute.socketApp
      jReq match {
        case jReq: FullHttpRequest =>
          log.debug(s"Upgrading to WebSocket: [${jReq.uri()}]")
          log.debug(s"SocketApp: [${app.orNull}]")
          ctx
            .channel()
            .pipeline()
            .addLast(new WebSocketServerProtocolHandler(app.get.protocol.serverBuilder.build()))
            .addLast(Names.WebSocketHandler, new WebSocketAppHandler(runtime, app.get, false))

          val retained = jReq.retainedDuplicate()
          ctx.channel().eventLoop().submit { () => ctx.fireChannelRead(retained) }: Unit

        case jReq: HttpRequest =>
          val fullRequest = new DefaultFullHttpRequest(jReq.protocolVersion(), jReq.method(), jReq.uri())
          fullRequest.headers().setAll(jReq.headers())
          upgradeToWebSocket(fullRequest, res, runtime)
      }
    }

    log.debug(s"Message: [${msg.getClass.getName}]")
    msg match {
      case jReq: FullHttpRequest =>
        log.debug(s"FullHttpRequest: [${jReq.method()} ${jReq.uri()}]")
        val req  = makeZioRequest(jReq)
        val exit = appRef.get.execute(req)

        if (attemptFastWrite(exit, time)) {
          releaseRequest(jReq)
        } else
          runtime.run(ctx) {
            attemptFullWrite(exit, jReq, time, runtime) ensuring ZIO.succeed { releaseRequest(jReq) }
          }

      case jReq: HttpRequest =>
        log.debug(s"HttpRequest: [${jReq.method()} ${jReq.uri()}]")
        val req  = makeZioRequest(jReq)
        val exit = appRef.get.execute(req)

        if (!attemptFastWrite(exit, time)) {
          if (canHaveBody(jReq)) setAutoRead(false)
          runtime.run(ctx) {
            attemptFullWrite(exit, jReq, time, runtime) ensuring ZIO.succeed(setAutoRead(true))
          }
        }

      case msg: HttpContent =>
        ctx.fireChannelRead(msg): Unit

      case _ =>
        throw new IllegalStateException(s"Unexpected message type: ${msg.getClass.getName}")
    }

  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    errCallbackRef
      .get()
      .fold {
        cause match {
          case ioe: IOException if ioe.getMessage.contentEquals("Connection reset by peer") =>
            log.info("Connection reset by peer")
          case t => super.exceptionCaught(ctx, t)
        }
      }(f => runtime.run(ctx)(f(cause)))
  }
}

object ServerInboundHandler {

  private val isReadKey = AttributeKey.newInstance[Boolean]("IS_READ_KEY")

  val log: Logger = service.Log.withTags("Server", "Request")

  val layer = {
    implicit val trace: Trace = Trace.empty
    ZLayer.fromZIO {
      for {
        appRef      <- ZIO.service[AppRef]
        errCallback <- ZIO.service[ErrorCallbackRef]
        rtm         <- ZIO.service[NettyRuntime]
        config      <- ZIO.service[ServerConfig]
        time        <- ZIO.service[service.ServerTime]

      } yield ServerInboundHandler(appRef, config, errCallback, rtm, time)
    }
  }

}
