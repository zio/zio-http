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

import scala.annotation.tailrec

import zio._

import zio.http._
import zio.http.model._
import zio.http.netty._
import zio.http.netty.model.Conversions
import zio.http.netty.server.ServerInboundHandler.isReadKey
import zio.http.netty.socket.NettySocketProtocol

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler
import io.netty.util.AttributeKey

@Sharable
private[zio] final case class ServerInboundHandler(
  appRef: AppRef,
  config: ServerConfig,
  errCallbackRef: ErrorCallbackRef,
  runtime: NettyRuntime,
  time: ServerTime,
)(implicit trace: Trace)
    extends SimpleChannelInboundHandler[HttpObject](false) { self =>

  implicit private val unsafe: Unsafe = Unsafe.unsafe

  private var app: App[Any]                     = _
  private var env: ZEnvironment[Any]            = _
  private var errCallback: Server.ErrorCallback = _

  def refreshApp(): Unit = {
    val pair     = appRef.get()
    val callback = errCallbackRef.get()

    this.app = pair._1
    this.env = pair._2
    this.errCallback = callback.orNull
  }

  private def ensureHasApp(): Unit = {
    if (app eq null) {
      refreshApp()
    }
  }

  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit = {

    msg match {
      case jReq: FullHttpRequest =>
        val req = makeZioRequest(ctx, jReq)

        val releaseRequest = { () =>
          if (jReq.refCnt() > 0) {
            val _ = jReq.release()
          }
        }

        ensureHasApp()
        val exit = app.runZIOOrNull(req)
        if (!attemptImmediateWrite(ctx, exit, time))
          writeResponse(ctx, env, exit, jReq)(releaseRequest)
        else
          releaseRequest()

      case jReq: HttpRequest =>
        val req = makeZioRequest(ctx, jReq)

        ensureHasApp()
        val exit = app.runZIOOrNull(req)
        if (!attemptImmediateWrite(ctx, exit, time)) {

          if (
            jReq.method() == HttpMethod.TRACE ||
            jReq.headers().contains(HttpHeaderNames.CONTENT_LENGTH) ||
            jReq.headers().contains(HttpHeaderNames.TRANSFER_ENCODING)
          )
            ctx.channel().config().setAutoRead(false)

          writeResponse(ctx, env, exit, jReq) { () =>
            val _ = ctx.channel().config().setAutoRead(true)
          }
        }

      case msg: HttpContent =>
        val _ = ctx.fireChannelRead(msg)

      case _ =>
        throw new IllegalStateException(s"Unexpected message type: ${msg.getClass.getName}")
    }

  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    if (errCallback != null) {
      runtime.run(ctx, NettyRuntime.noopEnsuring)(errCallback(Cause.die(cause)))
    } else {
      cause match {
        case ioe: IOException if ioe.getMessage.contentEquals("Connection reset by peer") =>
        case t => super.exceptionCaught(ctx, t)
      }
    }
  }

  private def addAsyncBodyHandler(ctx: ChannelHandlerContext, async: NettyBody.UnsafeAsync): Unit = {
    if (ctx.channel().attr(isReadKey).get())
      throw new RuntimeException("Unable to add the async body handler as the content has already been read.")

    ctx
      .channel()
      .pipeline()
      .addAfter(Names.HttpRequestHandler, Names.HttpContentHandler, new ServerAsyncBodyHandler(async))
    ctx.channel().attr(isReadKey).set(true)
  }

  private def attemptFastWrite(
    ctx: ChannelHandlerContext,
    response: Response,
    time: ServerTime,
  ): Boolean = {

    response.body match {
      case body: Body.UnsafeBytes =>
        NettyResponseEncoder.fastEncode(response, body.unsafeAsArray) match {
          case jResponse: FullHttpResponse if response.frozen =>
            val djResponse = jResponse.retainedDuplicate()
            setServerTime(time, response, djResponse)
            ctx.writeAndFlush(djResponse, ctx.voidPromise())
            true
          case jResponse if response.frozen                   =>
            throw new IllegalArgumentException(
              s"The ${jResponse.getClass.getName} was marked as 'frozen'.  However, zio-http only supports frozen responses when the response is of type 'FullHttpResponse'.",
            )
          case _                                              => false
        }
      case _                      => false
    }

  }

  private def attemptFullWrite(
    ctx: ChannelHandlerContext,
    response: Response,
    jRequest: HttpRequest,
    time: ServerTime,
    runtime: NettyRuntime,
  ): Task[Unit] = {

    for {
      _ <-
        if (response.isWebSocket) ZIO.attempt(upgradeToWebSocket(ctx, jRequest, response, runtime))
        else
          for {
            jResponse <- NettyResponseEncoder.encode(response)
            _         <- ZIO.attempt {
              setServerTime(time, response, jResponse)
              ctx.writeAndFlush(jResponse)
            }
            flushed   <-
              if (!jResponse.isInstanceOf[FullHttpResponse])
                NettyBodyWriter
                  .write(response.body, ctx)
              else
                ZIO.succeed(true)
            _         <- ZIO.attempt(ctx.flush()).when(!flushed)
          } yield ()

      _ <- ZIO.attempt(ctx.channel().attr(isReadKey).set(false))
    } yield ()
  }

  private def attemptImmediateWrite(
    ctx: ChannelHandlerContext,
    exit: ZIO[Any, Response, Response],
    time: ServerTime,
  ): Boolean = {
    exit match {
      case Exit.Success(response) if response ne null =>
        attemptFastWrite(ctx, response, time)
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

    val remoteAddress = ctx.channel().remoteAddress() match {
      case m: InetSocketAddress => Option(m.getAddress)
      case _                    => None
    }

    nettyReq match {
      case nettyReq: FullHttpRequest =>
        Request(
          NettyBody.fromByteBuf(nettyReq.content()),
          Conversions.headersFromNetty(nettyReq.headers()),
          Conversions.methodFromNetty(nettyReq.method()),
          URL.fromString(nettyReq.uri()).getOrElse(URL.empty),
          protocolVersion,
          remoteAddress,
        )
      case nettyReq: HttpRequest     =>
        val body = NettyBody.fromAsync { async =>
          addAsyncBodyHandler(ctx, async)
        }

        Request(
          body,
          Conversions.headersFromNetty(nettyReq.headers()),
          Conversions.methodFromNetty(nettyReq.method()),
          URL.fromString(nettyReq.uri()).getOrElse(URL.empty),
          protocolVersion,
          remoteAddress,
        )
    }

  }

  private def setServerTime(time: ServerTime, response: Response, jResponse: HttpResponse): Unit = {
    val _ =
      if (response.serverTime)
        jResponse.headers().set(HttpHeaderNames.DATE, time.refreshAndGet())
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
    val app = res.socketApp
    jReq match {
      case jReq: FullHttpRequest =>
        ctx
          .channel()
          .pipeline()
          .addLast(new WebSocketServerProtocolHandler(NettySocketProtocol.serverBuilder(app.get.protocol).build()))
          .addLast(Names.WebSocketHandler, new WebSocketAppHandler(runtime, app.get))

        val retained = jReq.retainedDuplicate()
        val _        = ctx.channel().eventLoop().submit { () => ctx.fireChannelRead(retained) }

      case jReq: HttpRequest =>
        val fullRequest = new DefaultFullHttpRequest(jReq.protocolVersion(), jReq.method(), jReq.uri())
        fullRequest.headers().setAll(jReq.headers())
        upgradeToWebSocket(ctx: ChannelHandlerContext, fullRequest, res, runtime)
    }
  }

  private def writeNotFound(ctx: ChannelHandlerContext, jReq: HttpRequest)(ensured: () => Unit): Unit = {
    // TODO: this can be done without ZIO
    runtime.run(ctx, ensured) {
      for {
        response <- ZIO.succeed(HttpError.NotFound(jReq.uri()).toResponse)
        done     <- ZIO.attempt(attemptFastWrite(ctx, response, time))
        _        <- attemptFullWrite(ctx, response, jReq, time, runtime).unless(done)
      } yield ()
    }
  }

  private def writeResponse(
    ctx: ChannelHandlerContext,
    env: ZEnvironment[Any],
    exit: ZIO[Any, Response, Response],
    jReq: HttpRequest,
  )(ensured: () => Unit): Unit = {
    runtime.run(ctx, ensured) {
      val pgm = for {
        response <- exit.sandbox.catchAll { error =>
          error.failureOrCause
            .fold[UIO[Response]](
              response => ZIO.succeed(response),
              cause =>
                if (cause.isInterruptedOnly) {
                  interrupted(ctx).as(null)
                } else {
                  (if (errCallback ne null) errCallback(cause) else ZIO.unit).as(
                    HttpError.InternalServerError(cause = Some(FiberFailure(cause))).toResponse,
                  )
                },
            )
        }
        _        <-
          if (response ne null) {
            for {
              done <- ZIO.attempt(attemptFastWrite(ctx, response, time))
              _    <- attemptFullWrite(ctx, response, jReq, time, runtime).unless(done)
            } yield ()
          } else {
            ZIO.attempt(
              if (ctx.channel().isOpen) {
                writeNotFound(ctx, jReq)(() => ())
              },
            )
          }
      } yield ()

      pgm.provideEnvironment(env)
    }
  }

  private def interrupted(ctx: ChannelHandlerContext): ZIO[Any, Nothing, Unit] =
    ZIO.attempt {
      ctx.channel().close()
    }.unit.orDie
}

object ServerInboundHandler {

  private[zio] val isReadKey = AttributeKey.newInstance[Boolean]("IS_READ_KEY")

  val layer: ZLayer[
    ServerTime with ServerConfig with NettyRuntime with ErrorCallbackRef with AppRef,
    Nothing,
    ServerInboundHandler,
  ] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.fromZIO {
      for {
        appRef      <- ZIO.service[AppRef]
        errCallback <- ZIO.service[ErrorCallbackRef]
        rtm         <- ZIO.service[NettyRuntime]
        config      <- ZIO.service[ServerConfig]
        time        <- ZIO.service[ServerTime]

      } yield ServerInboundHandler(appRef, config, errCallback, rtm, time)
    }
  }

}
