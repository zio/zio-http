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

  private lazy val (http, env) = appRef.get

  @inline
  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit = {

    log.debug(s"Message: [${msg.getClass.getName}]")
    msg match {
      case jReq: FullHttpRequest =>
        log.debug(s"FullHttpRequest: [${jReq.method()} ${jReq.uri()}]")
        val req  = makeZioRequest(ctx, jReq)
        val exit = http.execute(req)

        val releaseRequest = { () =>
          if (jReq.refCnt() > 0) {
            jReq.release(): Unit
          }
        }

        if (!attemptImmediateWrite(ctx, exit, time))
          writeResponse(ctx, env, exit, jReq)(releaseRequest)
        else
          releaseRequest()

      case jReq: HttpRequest =>
        log.debug(s"HttpRequest: [${jReq.method()} ${jReq.uri()}]")
        val req  = makeZioRequest(ctx, jReq)
        val exit = http.execute(req)

        if (!attemptImmediateWrite(ctx, exit, time)) {

          if (
            jReq.method() == HttpMethod.TRACE ||
            jReq.headers().contains(HttpHeaderNames.CONTENT_LENGTH) ||
            jReq.headers().contains(HttpHeaderNames.TRANSFER_ENCODING)
          )
            ctx.channel().config().setAutoRead(false)

          writeResponse(ctx, env, exit, jReq)(() => ctx.channel().config().setAutoRead(true): Unit)

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
      }(f => runtime.run(ctx, () => ())(f(cause)))
  }

  private def addAsyncBodyHandler(ctx: ChannelHandlerContext, async: Body.UnsafeAsync): Unit = {
    if (ctx.channel().attr(isReadKey).get()) throw new RuntimeException("Content is already read")
    ctx
      .channel()
      .pipeline()
      .addAfter(Names.HttpRequestHandler, Names.HttpContentHandler, new ServerAsyncBodyHandler(async))
    ctx.channel().attr(isReadKey).set(true)
  }

  private def attemptFastWrite(
    ctx: ChannelHandlerContext,
    response: Response,
    time: service.ServerTime,
  ): Boolean = {

    def doEncode(jResponse: HttpResponse) = jResponse match {
      case jResponse: FullHttpResponse =>
        val djResponse = jResponse.retainedDuplicate()
        setServerTime(time, response, djResponse)
        ctx.writeAndFlush(djResponse, ctx.voidPromise())
        true
      case jResponse                   =>
        throw new IllegalArgumentException(
          s"The ${jResponse.getClass.getName} is not supported as a Netty response encoder.",
        )
    }

    val resp = response.encodedResponse.get
    (response.frozen, resp) match {
      case (true, Some(NettyResponseEncoder.NettyEncodedResponse(jResponse: FullHttpResponse))) => doEncode(jResponse)
      case (true, None)                                                                         =>
        val encResponse = NettyResponseEncoder.encode(response)
        encResponse match {
          case NettyResponseEncoder.NettyEncodedResponse(jResponse) => doEncode(jResponse)
          case other                                                =>
            throw new IllegalArgumentException(
              s"The ${other.getClass.getName} is not supported as a Netty response encoder.",
            )
        }
      case _                                                                                    => false
    }
  }

  private def attemptFullWrite(
    ctx: ChannelHandlerContext,
    response: Response,
    jRequest: HttpRequest,
    time: service.ServerTime,
    runtime: NettyRuntime,
  ): Task[Unit] = {

    for {
      _ <-
        if (response.isWebSocket) ZIO.attempt(upgradeToWebSocket(ctx, jRequest, response, runtime))
        else
          for {
            jResponse <- ZIO.attempt {
              val jResponse = NettyResponseEncoder.encode(response).jResponse
              setServerTime(time, response, jResponse)
              ctx.writeAndFlush(jResponse)
              jResponse
            }
            flushed   <-
              if (!jResponse.isInstanceOf[FullHttpResponse]) NettyBodyWriter.write(response.body, ctx)
              else ZIO.succeed(true)
            _         <- ZIO.attempt(ctx.flush()).when(!flushed)
          } yield ()

      _ <- ZIO.attempt(ctx.channel().attr(isReadKey).set(false))
    } yield log.debug("Full write performed")
  }

  private def attemptImmediateWrite(
    ctx: ChannelHandlerContext,
    exit: HExit[Any, Throwable, Response],
    time: service.ServerTime,
  ): Boolean = {
    exit match {
      case HExit.Success(response) =>
        NettyResponseEncoder.encode(response) match {
          case NettyResponseEncoder.NettyEncodedResponse(jResponse: FullHttpResponse) =>
            val djResponse = jResponse.retainedDuplicate()
            setServerTime(time, response, djResponse)
            ctx.writeAndFlush(djResponse, ctx.voidPromise()): Unit
            true
          case _                                                                      => false
        }
      case _                       => false
    }
  }
  private def makeZioRequest(ctx: ChannelHandlerContext, nettyReq: HttpRequest): Request     = {
    val nettyHttpVersion = nettyReq.protocolVersion()
    val protocolVersion  = nettyHttpVersion match {
      case HttpVersion.HTTP_1_0 => Version.Http_1_0
      case HttpVersion.HTTP_1_1 => Version.Http_1_1
      case _                    => throw new IllegalArgumentException(s"Unsupported HTTP version: ${nettyHttpVersion}")
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
          addAsyncBodyHandler(ctx, async)
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

  private def setServerTime(time: service.ServerTime, response: Response, jResponse: HttpResponse): Unit = {
    if (response.attribute.serverTime)
      jResponse.headers().set(HttpHeaderNames.DATE, time.refreshAndGet()): Unit
  }

  /*
   * Checks if the response requires to switch protocol to websocket. Returns
   * true if it can, otherwise returns false
   */
  @tailrec
  private def upgradeToWebSocket(
    ctx: ChannelHandlerContext,
    jReq: HttpRequest,
    res: Response,
    runtime: NettyRuntime,
  ): Unit = {
    val app = res.attribute.socketApp
    jReq match {
      case jReq: FullHttpRequest =>
        log.debug(s"Upgrading to WebSocket: [${jReq.uri()}].  SocketApp: [${app.orNull}]")
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
        upgradeToWebSocket(ctx: ChannelHandlerContext, fullRequest, res, runtime)
    }
  }

  private def writeResponse(
    ctx: ChannelHandlerContext,
    env: ZEnvironment[Any],
    exit: HExit[Any, Throwable, Response],
    jReq: HttpRequest,
  )(ensured: () => Unit) = {
    runtime.run(ctx, ensured) {
      val pgm = for {
        response <- exit.toZIO.unrefine { case error => Option(error) }.catchAll {
          case None        => ZIO.succeed(HttpError.NotFound(jReq.uri()).toResponse)
          case Some(error) => ZIO.succeed(HttpError.InternalServerError(cause = Some(error)).toResponse)
        }
        done     <- ZIO.attempt(attemptFastWrite(ctx, response, time))
        result   <-
          if (done)
            ZIO.unit
          else
            attemptFullWrite(ctx, response, jReq, time, runtime)

      } yield ()

      pgm.provideEnvironment(env)
    }

  }
}

object ServerInboundHandler {

  private[zio] val isReadKey = AttributeKey.newInstance[Boolean]("IS_READ_KEY")

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
