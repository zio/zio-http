/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.netty.server

import java.io.IOException
import java.net.InetSocketAddress
import java.util.concurrent.atomic.LongAdder

import scala.util.control.NonFatal

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.Body.WebsocketBody
import zio.http._
import zio.http.netty._
import zio.http.netty.model.Conversions
import zio.http.netty.socket.NettySocketProtocol

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => JWebSocketFrame, WebSocketServerProtocolHandler}
import io.netty.handler.ssl.SslHandler
import io.netty.handler.timeout.ReadTimeoutException
import io.netty.util.ReferenceCountUtil

@Sharable
private[zio] final case class ServerInboundHandler(
  appRef: AppRef,
  config: Server.Config,
)(implicit trace: Trace)
    extends SimpleChannelInboundHandler[HttpObject](false) { self =>

  implicit private val unsafe: Unsafe = Unsafe.unsafe

  private var app: Routes[Any, Response] = _
  private var runtime: NettyRuntime      = _

  val inFlightRequests: LongAdder = new LongAdder()
  private val readClientCert      = config.sslConfig.exists(_.includeClientCert)
  private val avoidCtxSwitching   = config.avoidContextSwitching

  def refreshApp(): Unit = {
    val pair = appRef.get()

    this.app = pair._1
    this.runtime = new NettyRuntime(pair._2)
  }

  private def ensureHasApp(): Unit = {
    if (runtime eq null) {
      refreshApp()
    }
  }

  private val releaseRequest = () => inFlightRequests.decrement()

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit = {

    msg match {
      case jReq: HttpRequest =>
        inFlightRequests.increment()
        ensureHasApp()

        try {
          if (jReq.decoderResult().isFailure) {
            val throwable = jReq.decoderResult().cause()
            attemptFastWrite(
              ctx,
              Response.fromThrowable(throwable, runtime.getRef(ErrorResponseConfig.configRef)),
            )
            releaseRequest()
          } else {
            val req  = makeZioRequest(ctx, jReq)
            val exit = app(req)
            if (attemptImmediateWrite(ctx, exit)) {
              releaseRequest()
            } else {
              writeResponse(ctx, runtime, exit, req)(releaseRequest)
            }
          }
        } finally {
          ReferenceCountUtil.safeRelease(jReq)
        }

      case msg: HttpContent =>
        val _ = ctx.fireChannelRead(msg)

      case _ =>
        throw new IllegalStateException(s"Unexpected message type: ${msg.getClass.getName}")
    }

  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit =
    cause match {
      case ioe: IOException if {
            val msg = ioe.getMessage
            (msg ne null) && msg.contains("Connection reset")
          } =>
      case t =>
        if ((runtime ne null) && config.logWarningOnFatalError) {
          runtime.unsafeRunSync {
            // We cannot return the generated response from here, but still calling the handler for its side effect
            // for example logging.
            ZIO.logWarningCause(s"Fatal exception in Netty", Cause.die(t))
          }
        }
        cause match {
          case _: ReadTimeoutException =>
            ctx.close(): Unit
          case _                       =>
            super.exceptionCaught(ctx, t)
        }
    }

  private def addAsyncBodyHandler(ctx: ChannelHandlerContext): AsyncBodyReader = {
    val handler = new ServerAsyncBodyHandler
    ctx
      .channel()
      .pipeline()
      .addAfter(Names.HttpRequestHandler, Names.HttpContentHandler, handler)
    handler
  }

  private def attemptFastWrite(
    ctx: ChannelHandlerContext,
    response: Response,
  ): Boolean = {

    def fastEncode(response: Response, bytes: Array[Byte]) = {
      val jResponse  = NettyResponseEncoder.fastEncode(response, bytes)
      val djResponse = jResponse.retainedDuplicate()
      ctx.writeAndFlush(djResponse, ctx.voidPromise())
      true
    }

    response.body match {
      case body: Body.UnsafeBytes =>
        try {
          fastEncode(response, body.unsafeAsArray)
        } catch {
          case NonFatal(e) => fastEncode(withDefaultErrorResponse(e), Array.emptyByteArray)
        }
      case _                      => false
    }

  }

  private def attemptFullWrite(
    ctx: ChannelHandlerContext,
    runtime: NettyRuntime,
    response: Response,
    request: Request,
  ): Task[Option[Task[Unit]]] = {
    response.body match {
      case WebsocketBody(socketApp) if response.status == Status.SwitchingProtocols =>
        upgradeToWebSocket(ctx, request, socketApp, runtime).as(None)
      case _                                                                        =>
        ZIO.attempt {
          val jResponse = NettyResponseEncoder.encode(response)

          if (!jResponse.isInstanceOf[FullHttpResponse]) {

            // We MUST get the content length from the headers BEFORE we call writeAndFlush otherwise netty will mutate
            // the headers and remove `content-length` since there is no content
            val contentLength =
              jResponse.headers().get(HttpHeaderNames.CONTENT_LENGTH) match {
                case null  => None
                case value => Some(value.toLong)
              }

            ctx.writeAndFlush(jResponse)
            NettyBodyWriter.writeAndFlush(response.body, contentLength, ctx)
          } else {
            ctx.writeAndFlush(jResponse)
            None
          }
        }
    }
  }

  private def attemptImmediateWrite(
    ctx: ChannelHandlerContext,
    exit: ZIO[Any, Response, Response],
  ): Boolean = {
    exit match {
      case Exit.Success(response) if response ne null =>
        attemptFastWrite(ctx, response)
      case _                                          => false
    }
  }

  private def makeZioRequest(ctx: ChannelHandlerContext, nettyReq: HttpRequest): Request = {
    val nettyHttpVersion = nettyReq.protocolVersion()
    val protocolVersion  = nettyHttpVersion match {
      case HttpVersion.HTTP_1_0 => Version.Http_1_0
      case HttpVersion.HTTP_1_1 => Version.Http_1_1
      case _                    => throw new IllegalArgumentException(s"Unsupported HTTP version: $nettyHttpVersion")
    }
    val clientCert       = if (readClientCert) {
      val sslHandler = ctx.pipeline().get(classOf[SslHandler])
      sslHandler.engine().getSession().getPeerCertificates().headOption
    } else {
      None
    }
    val remoteAddress    = ctx.channel().remoteAddress() match {
      case m: InetSocketAddress => Option(m.getAddress)
      case _                    => None
    }

    val headers           = Conversions.headersFromNetty(nettyReq.headers())
    val contentTypeHeader = headers.get(Header.ContentType)

    nettyReq match {
      case nettyReq: FullHttpRequest =>
        Request(
          body = NettyBody.fromByteBuf(nettyReq.content(), contentTypeHeader),
          headers = headers,
          method = Conversions.methodFromNetty(nettyReq.method()),
          url = URL.decode(nettyReq.uri()).getOrElse(URL.empty),
          version = protocolVersion,
          remoteAddress = remoteAddress,
          remoteCertificate = clientCert,
        )
      case nettyReq: HttpRequest     =>
        val knownContentLength = headers.get(Header.ContentLength).map(_.length)
        val handler            = addAsyncBodyHandler(ctx)
        val body = NettyBody.fromAsync(async => handler.connect(async), knownContentLength, contentTypeHeader)

        Request(
          body = body,
          headers = headers,
          method = Conversions.methodFromNetty(nettyReq.method()),
          url = URL.decode(nettyReq.uri()).getOrElse(URL.empty),
          version = protocolVersion,
          remoteAddress = remoteAddress,
          remoteCertificate = clientCert,
        )
    }

  }

  /*
   * Checks if the response requires to switch protocol to websocket. Returns
   * true if it can, otherwise returns false
   */
  private def upgradeToWebSocket(
    ctx: ChannelHandlerContext,
    request: Request,
    webSocketApp: WebSocketApp[Any],
    runtime: NettyRuntime,
  ): Task[Unit] = {
    Queue
      .unbounded[WebSocketChannelEvent]
      .tap { queue =>
        ZIO.suspend {
          val nettyChannel     = NettyChannel.make[JWebSocketFrame](ctx.channel())
          val webSocketChannel = WebSocketChannel.make(nettyChannel, queue)
          webSocketApp.handler.runZIO(webSocketChannel).ignoreLogged.forkDaemon
        }
      }
      .flatMap { queue =>
        ZIO.attempt {
          ctx
            .channel()
            .pipeline()
            .addLast(
              new WebSocketServerProtocolHandler(
                NettySocketProtocol
                  .serverBuilder(webSocketApp.customConfig.getOrElse(config.webSocketConfig))
                  .build(),
              ),
            )
            .addLast(Names.WebSocketHandler, new WebSocketAppHandler(runtime, queue, None))

          val jReq = new DefaultFullHttpRequest(
            Conversions.versionToNetty(request.version),
            Conversions.methodToNetty(request.method),
            Conversions.urlToNetty(request.url),
          )
          jReq.headers().setAll(Conversions.headersToNetty(request.allHeaders))
          ctx.channel().eventLoop().submit { () => ctx.fireChannelRead(jReq) }: Unit
        }
      }
  }

  private def writeResponse(
    ctx: ChannelHandlerContext,
    runtime: NettyRuntime,
    exit: ZIO[Any, Response, Response],
    req: Request,
  )(ensured: () => Unit): Unit = {

    def closeChannel(): Task[Unit] =
      NettyFutureExecutor.executed(ctx.channel().close())

    def writeResponse(response: Response): Task[Unit] =
      if (attemptFastWrite(ctx, response)) {
        Exit.unit
      } else {
        attemptFullWrite(ctx, runtime, response, req).foldCauseZIO(
          cause => {
            attemptFastWrite(ctx, withDefaultErrorResponse(cause.squash))
            Exit.unit
          },
          {
            case None       => Exit.unit
            case Some(task) => task.orElse(closeChannel())
          },
        )
      }

    val program = exit.foldCauseZIO(
      _.failureOrCause match {
        case Left(resp)                      => writeResponse(resp)
        case Right(c) if c.isInterruptedOnly => closeChannel()
        case Right(c)                        => writeResponse(withDefaultErrorResponse(FiberFailure(c)))
      },
      writeResponse,
    )

    runtime.run(ctx, ensured, preferOnCurrentThread = avoidCtxSwitching)(program)
  }

  private def withDefaultErrorResponse(cause: Throwable): Response =
    Response.internalServerError(cause.getMessage)
}

object ServerInboundHandler {

  val live: ZLayer[
    AppRef & Server.Config,
    Nothing,
    ServerInboundHandler,
  ] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.fromZIO {
      for {
        appRef <- ZIO.service[AppRef]
        config <- ZIO.service[Server.Config]
      } yield ServerInboundHandler(appRef, config)
    }
  }

}
