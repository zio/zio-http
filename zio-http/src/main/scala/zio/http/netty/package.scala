package zio.http

import io.netty.channel._
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import io.netty.util.AttributeKey
import zio._
import zio.http.model._

import scala.annotation.tailrec
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

package object netty {
  import server._

  private[zio] val AutoReleaseRequest = false

  private[zio] object Names {
    val ServerCodecHandler             = "SERVER_CODEC"
    val HttpObjectAggregator           = "HTTP_OBJECT_AGGREGATOR"
    val HttpRequestHandler             = "HTTP_REQUEST"
    val HttpResponseHandler            = "HTTP_RESPONSE"
    val HttpKeepAliveHandler           = "HTTP_KEEPALIVE"
    val FlowControlHandler             = "FLOW_CONTROL_HANDLER"
    val WebSocketHandler               = "WEB_SOCKET_HANDLER"
    val SSLHandler                     = "SSL_HANDLER"
    val HttpOnHttpsHandler             = "HTTP_ON_HTTPS_HANDLER"
    val HttpServerCodec                = "HTTP_SERVER_CODEC"
    val HttpClientCodec                = "HTTP_CLIENT_CODEC"
    val HttpServerExpectContinue       = "HTTP_SERVER_EXPECT_CONTINUE"
    val HttpServerFlushConsolidation   = "HTTP_SERVER_FLUSH_CONSOLIDATION"
    val ClientInboundHandler           = "CLIENT_INBOUND_HANDLER"
    val WebSocketClientProtocolHandler = "WEB_SOCKET_CLIENT_PROTOCOL_HANDLER"
    val HttpRequestDecompression       = "HTTP_REQUEST_DECOMPRESSION"
    val HttpResponseCompression        = "HTTP_RESPONSE_COMPRESSION"
    val LowLevelLogging                = "LOW_LEVEL_LOGGING"
    val ProxyHandler                   = "PROXY_HANDLER"
    val HttpContentHandler             = "HTTP_CONTENT_HANDLER"
  }

  private val isReadKey = AttributeKey.newInstance[Boolean]("IS_READ_KEY")

  implicit final class ChannelHandlerContextOps(val ctx: ChannelHandlerContext) extends AnyVal {

    def addAsyncBodyHandler(async: Body.UnsafeAsync)(implicit unsafe: Unsafe): Unit = {
      if (contentIsRead) throw new RuntimeException("Content is already read")
      ctx
        .channel()
        .pipeline()
        .addAfter(Names.HttpRequestHandler, Names.HttpContentHandler, new ServerAsyncBodyHandler(async)): Unit
      setContentReadAttr(flag = true)
    }

    /**
     * Enables auto-read if possible. Also performs the first read.
     */
    def attemptAutoRead[R, E](config: ServerConfig)(implicit unsafe: Unsafe): Unit = {
      if (!config.useAggregator && !ctx.channel().config().isAutoRead) {
        ctx.channel().config().setAutoRead(true)
        ctx.read(): Unit
      }
    }

    def contentIsRead(implicit unsafe: Unsafe): Boolean =
      ctx.channel().attr(isReadKey).get()

    def setAutoRead(cond: Boolean)(implicit unsafe: Unsafe): Unit = {
      //   log.debug(s"Setting channel auto-read to: [${cond}]")
      ctx.channel().config().setAutoRead(cond): Unit
    }

    def setContentReadAttr(flag: Boolean)(implicit unsafe: Unsafe): Unit = {
      ctx.channel().attr(isReadKey).set(flag)
    }

    /**
     * Checks if the response requires to switch protocol to websocket. Returns
     * true if it can, otherwise returns false
     */
    @tailrec
    def upgradeToWebSocket(jReq: HttpRequest, res: Response, runtime: NettyRuntime): Unit = {
      val app = res.attribute.socketApp
      jReq match {
        case jReq: FullHttpRequest =>
          // log.debug(s"Upgrading to WebSocket: [${jReq.uri()}]")
          // log.debug(s"SocketApp: [${app.orNull}]")
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

    /**
     * Sets the server time on the response if required
     */
    def setServerTime(time: service.ServerTime, response: Response, jResponse: HttpResponse)(implicit
      unsafe: Unsafe,
    ): Unit = {
      if (response.attribute.serverTime)
        jResponse.headers().set(HttpHeaderNames.DATE, time.refreshAndGet()): Unit
    }

    def attemptFastWrite(exit: HExit[Any, Throwable, Response], time: service.ServerTime)(implicit
      unsafe: Unsafe,
    ): Boolean = {
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
    )(implicit trace: Trace): ZIO[Any, Throwable, Unit] = {

      for {
        response <- exit.toZIO.unrefine { case error => Option(error) }.catchAll {
          case None        => ZIO.succeed(HttpError.NotFound(jRequest.uri()).toResponse)
          case Some(error) => ZIO.succeed(HttpError.InternalServerError(cause = Some(error)).toResponse)
        }
        _        <-
          if (response.isWebSocket) ZIO.attempt(ctx.upgradeToWebSocket(jRequest, response, runtime))
          else
            for {
              jResponse <- response.encode()
              _         <- ZIO.attemptUnsafe(implicit u => setServerTime(time, response, jResponse))
              _         <- ZIO.attempt(ctx.writeAndFlush(jResponse))
              flushed <- if (!jResponse.isInstanceOf[FullHttpResponse]) response.body.write(ctx) else ZIO.succeed(true)
              _       <- ZIO.attempt(ctx.flush()).when(!flushed)
            } yield ()

        _ <- ZIO.attemptUnsafe(implicit u => ctx.setContentReadAttr(false))
      } yield () // log.debug("Full write performed")
    }
  }

  def hasChanged(r1: Response, r2: Response)(implicit unsafe: Unsafe): Boolean =
    (r1.status eq r2.status) && (r1.body eq r2.body) && (r1.headers eq r2.headers)

}
