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

import scala.annotation.tailrec
import scala.util.control.NonFatal

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http._
import zio.http.netty._
import zio.http.netty.model.Conversions
import zio.http.netty.socket.NettySocketProtocol

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.websocketx.{WebSocketFrame => JWebSocketFrame, WebSocketServerProtocolHandler}
import io.netty.handler.timeout.ReadTimeoutException
@Sharable
private[zio] final case class ServerInboundHandler(
  appRef: AppRef,
  config: Server.Config,
  runtime: NettyRuntime,
  time: ServerTime,
)(implicit trace: Trace)
    extends SimpleChannelInboundHandler[HttpObject](false) { self =>

  implicit private val unsafe: Unsafe = Unsafe.unsafe

  private var app: HttpApp[Any]      = _
  private var env: ZEnvironment[Any] = _

  val inFlightRequests: LongAdder = new LongAdder()

  def refreshApp(): Unit = {
    val pair = appRef.get()

    this.app = pair._1
    this.env = pair._2
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
        inFlightRequests.increment()

        val releaseRequest = { () =>
          inFlightRequests.decrement()
          if (jReq.refCnt() > 0) {
            val _ = jReq.release()
          }
        }

        ensureHasApp()
        val exit =
          if (jReq.decoderResult().isFailure) {
            val throwable = jReq.decoderResult().cause()
            Exit.succeed(Response.fromThrowable(throwable))
          } else
            app(req)
        if (!attemptImmediateWrite(ctx, exit, time))
          writeResponse(ctx, env, exit, jReq)(releaseRequest)
        else
          releaseRequest()

      case jReq: HttpRequest =>
        val req = makeZioRequest(ctx, jReq)
        inFlightRequests.increment()

        val releaseRequest = { () =>
          inFlightRequests.decrement()
          ()
        }

        ensureHasApp()
        val exit =
          if (jReq.decoderResult().isFailure) {
            val throwable = jReq.decoderResult().cause()
            Exit.succeed(Response.fromThrowable(throwable))
          } else
            app(req)
        if (!attemptImmediateWrite(ctx, exit, time)) {
          writeResponse(ctx, env, exit, jReq)(releaseRequest)
        } else {
          releaseRequest()
        }

      case msg: HttpContent =>
        val _ = ctx.fireChannelRead(msg)

      case _ =>
        throw new IllegalStateException(s"Unexpected message type: ${msg.getClass.getName}")
    }

  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit =
    cause match {
      case ioe: IOException if ioe.getMessage.startsWith("Connection reset") =>
      case t                                                                 =>
        if (app ne null) {
          runtime.run(ctx, () => {}) {
            // We cannot return the generated response from here, but still calling the handler for its side effect
            // for example logging.
            ZIO.logWarningCause(s"Fatal exception in Netty", Cause.die(t)).when(config.logWarningOnFatalError)
          }
        }
        cause match {
          case _: ReadTimeoutException =>
            ctx.close()
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
    time: ServerTime,
  ): Boolean = {

    def fastEncode(response: Response, bytes: Array[Byte]) = {
      NettyResponseEncoder.fastEncode(response, bytes) match {
        case jResponse: FullHttpResponse if response.frozen =>
          val djResponse = jResponse.retainedDuplicate()
          setServerTime(time, response, djResponse)
          ctx.writeAndFlush(djResponse, ctx.voidPromise())
          true
        case _                                              => false
      }
    }

    response.body match {
      case body: Body.UnsafeBytes =>
        try {
          fastEncode(response, body.unsafeAsArray)
        } catch {
          case NonFatal(e) => fastEncode(withDefaultErrorResponse(null, Some(e)).freeze, Array.emptyByteArray)
        }
      case _                      => false
    }

  }

  private def attemptFullWrite(
    ctx: ChannelHandlerContext,
    response: Response,
    jRequest: HttpRequest,
    time: ServerTime,
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

    val headers     = Conversions.headersFromNetty(nettyReq.headers())
    val contentType = headers.header(Header.ContentType)

    nettyReq match {
      case nettyReq: FullHttpRequest =>
//        println(s"Got ready http request")
        Request(
          body = NettyBody.fromByteBuf(
            nettyReq.content(),
            contentType,
          ),
          headers = headers,
          method = Conversions.methodFromNetty(nettyReq.method()),
          url = URL.decode(nettyReq.uri()).getOrElse(URL.empty),
          version = protocolVersion,
          remoteAddress = remoteAddress,
        )
      case nettyReq: HttpRequest     =>
//        println(s"Got streaming http request")
        val handler = addAsyncBodyHandler(ctx)
        val body    = NettyBody.fromAsync(
          { async =>
            handler.connect(async)
          },
          contentType,
        )

        Request(
          body = body,
          headers = headers,
          method = Conversions.methodFromNetty(nettyReq.method()),
          url = URL.decode(nettyReq.uri()).getOrElse(URL.empty),
          version = protocolVersion,
          remoteAddress = remoteAddress,
        )
    }

  }

  private def setServerTime(time: ServerTime, response: Response, jResponse: HttpResponse): Unit = {
    val _ =
      if (response.addServerTime)
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
        val queue = runtime.runtime(ctx).unsafe.run(Queue.unbounded[WebSocketChannelEvent]).getOrThrowFiberFailure()
        val webSocketApp = app.getOrElse(WebSocketApp.unit)
        runtime.runtime(ctx).unsafe.run {
          val nettyChannel     = NettyChannel.make[JWebSocketFrame](ctx.channel())
          val webSocketChannel = WebSocketChannel.make(nettyChannel, queue)
          webSocketApp.handler.runZIO(webSocketChannel).ignoreLogged.forkDaemon
        }
        ctx
          .channel()
          .pipeline()
          .addLast(
            new WebSocketServerProtocolHandler(
              NettySocketProtocol.serverBuilder(webSocketApp.customConfig.getOrElse(config.webSocketConfig)).build(),
            ),
          )
          .addLast(Names.WebSocketHandler, new WebSocketAppHandler(runtime, queue, None))

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
        response <- ZIO.succeed(Response.notFound(jReq.uri()))
        done     <- ZIO.attempt(attemptFastWrite(ctx, response, time))
        _        <- attemptFullWrite(ctx, response, jReq, time).unless(done)
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
                  ZIO.succeed(withDefaultErrorResponse(null, Some(FiberFailure(cause))))
                },
            )
        }
        _        <-
          if (response ne null) {
            for {
              done <- ZIO.attempt(attemptFastWrite(ctx, response, time)).catchSomeCause { case cause =>
                ZIO.attempt(
                  attemptFastWrite(ctx, withDefaultErrorResponse(null, Some(cause.squash)).freeze, time),
                )
              }
              _    <- attemptFullWrite(ctx, response, jReq, time).catchSomeCause { case cause =>
                ZIO.attempt(
                  attemptFastWrite(ctx, withDefaultErrorResponse(null, Some(cause.squash)).freeze, time),
                )

              }
                .unless(done)
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

  private def withDefaultErrorResponse(responseOrNull: Response, cause: Option[Throwable]): Response =
    if (responseOrNull ne null) responseOrNull
    else Response.internalServerError(cause.map(_.getMessage()).getOrElse(""))
}

object ServerInboundHandler {

  val live: ZLayer[
    ServerTime with Server.Config with NettyRuntime with AppRef,
    Nothing,
    ServerInboundHandler,
  ] = {
    implicit val trace: Trace = Trace.empty
    ZLayer.fromZIO {
      for {
        appRef <- ZIO.service[AppRef]
        rtm    <- ZIO.service[NettyRuntime]
        config <- ZIO.service[Server.Config]
        time   <- ZIO.service[ServerTime]

      } yield ServerInboundHandler(appRef, config, rtm, time)
    }
  }

}

// package zio.http.netty.server

// import java.io.IOException
// import java.net.InetSocketAddress
// import java.util.concurrent.atomic.LongAdder

// import scala.annotation.tailrec
// import scala.util.control.NonFatal

// import zio._

// import zio.http._
// import zio.http.netty._
// import zio.http.netty.model.Conversions
// import zio.http.netty.socket.NettySocketProtocol

// import io.netty.channel.ChannelHandler.Sharable
// import io.netty.channel._
// import io.netty.handler.codec.http._
// import io.netty.handler.codec.http.websocketx.{WebSocketFrame => JWebSocketFrame, WebSocketServerProtocolHandler}
// import io.netty.handler.timeout.ReadTimeoutException

// @Sharable
// private[zio] final case class ServerInboundHandler(
//   appRef: AppRef,
//   config: Server.Config,
//   runtime: NettyRuntime,
//   time: ServerTime,
// )(implicit trace: Trace)
//     extends SimpleChannelInboundHandler[HttpObject](false) { self =>

//   implicit private val unsafe: Unsafe = Unsafe.unsafe

//   private var app: App[Any]          = _
//   private var env: ZEnvironment[Any] = _

//   val inFlightRequests: LongAdder = new LongAdder()

//   def refreshApp(): Unit = {
//     val pair = appRef.get()

//     this.app = pair._1
//     this.env = pair._2
//   }

//   private def ensureHasApp(): Unit = {
//     if (app eq null) {
//       refreshApp()
//     }
//   }

//   override def channelRead0(ctx: ChannelHandlerContext, msg: HttpObject): Unit = {

//     msg match {
//       case jReq: FullHttpRequest =>
//         val req = makeZioRequest(ctx, jReq)
//         inFlightRequests.increment()

//         val releaseRequest = { () =>
//           inFlightRequests.decrement()
//           if (jReq.refCnt() > 0) {
//             val _ = jReq.release()
//           }
//         }

//         ensureHasApp()
//         val exit =
//           if (jReq.decoderResult().isFailure) {
//             val throwable = jReq.decoderResult().cause()
//             app.runServerErrorOrNull(Cause.die(throwable)).as(withDefaultErrorResponse(null, Some(throwable)))
//           } else
//             app.runZIOOrNull(req)
//         if (!attemptImmediateWrite(ctx, exit, time))
//           writeResponse(ctx, env, exit, jReq)(releaseRequest)
//         else
//           releaseRequest()

//       case jReq: HttpRequest =>
//         val req = makeZioRequest(ctx, jReq)
//         inFlightRequests.increment()

//         val releaseRequest = { () =>
//           inFlightRequests.decrement()
//           ()
//         }

//         ensureHasApp()
//         val exit =
//           if (jReq.decoderResult().isFailure) {
//             val throwable = jReq.decoderResult().cause()
//             app.runServerErrorOrNull(Cause.die(throwable)).as(withDefaultErrorResponse(null, Some(throwable)))
//           } else
//             app.runZIOOrNull(req)
//         if (!attemptImmediateWrite(ctx, exit, time)) {
//           writeResponse(ctx, env, exit, jReq)(releaseRequest)
//         } else {
//           releaseRequest()
//         }

//       case msg: HttpContent =>
//         val _ = ctx.fireChannelRead(msg)

//       case _ =>
//         throw new IllegalStateException(s"Unexpected message type: ${msg.getClass.getName}")
//     }

//   }

//   override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit =
//     cause match {
//       case ioe: IOException if ioe.getMessage.contentEquals("Connection reset by peer") =>
//       case t                                                                            =>
//         if (app ne null) {
//           runtime.run(ctx, () => {}) {
//             // We cannot return the generated response from here, but still calling the handler for its side effect
//             // for example logging.
//             app
//               .runServerErrorOrNull(Cause.die(t))
//               .zipLeft(
//                 ZIO.logWarningCause(s"Fatal exception in Netty", Cause.die(t)).when(config.logWarningOnFatalError),
//               )
//           }
//         }
//         cause match {
//           case _: ReadTimeoutException =>
//             ctx.close()
//           case _                       =>
//             super.exceptionCaught(ctx, t)
//         }
//     }

//   private def addAsyncBodyHandler(ctx: ChannelHandlerContext): AsyncBodyReader = {
//     val handler = new ServerAsyncBodyHandler
//     ctx
//       .channel()
//       .pipeline()
//       .addAfter(Names.HttpRequestHandler, Names.HttpContentHandler, handler)
//     handler
//   }

//   private def attemptFastWrite(
//     ctx: ChannelHandlerContext,
//     response: Response,
//     time: ServerTime,
//   ): Boolean = {

//     def fastEncode(response: Response, bytes: Array[Byte]) = {
//       NettyResponseEncoder.fastEncode(response, bytes) match {
//         case jResponse: FullHttpResponse if response.frozen =>
//           val djResponse = jResponse.retainedDuplicate()
//           setServerTime(time, response, djResponse)
//           ctx.writeAndFlush(djResponse, ctx.voidPromise())
//           true
//         case _                                              => false
//       }
//     }

//     response.body match {
//       case body: Body.UnsafeBytes =>
//         try {
//           fastEncode(response, body.unsafeAsArray)
//         } catch {
//           case NonFatal(e) => fastEncode(withDefaultErrorResponse(null, Some(e)).freeze, Array.emptyByteArray)
//         }
//       case _                      => false
//     }

//   }

//   private def attemptFullWrite(
//     ctx: ChannelHandlerContext,
//     response: Response,
//     jRequest: HttpRequest,
//     time: ServerTime,
//   ): Task[Unit] = {

//     for {
//       _ <-
//         if (response.isWebSocket) ZIO.attempt(upgradeToWebSocket(ctx, jRequest, response, runtime))
//         else
//           for {
//             jResponse <- NettyResponseEncoder.encode(response)
//             _         <- ZIO.attempt {
//               setServerTime(time, response, jResponse)
//               ctx.writeAndFlush(jResponse)
//             }
//             flushed   <-
//               if (!jResponse.isInstanceOf[FullHttpResponse])
//                 NettyBodyWriter
//                   .write(response.body, ctx)
//               else
//                 ZIO.succeed(true)
//             _         <- ZIO.attempt(ctx.flush()).when(!flushed)
//           } yield ()
//     } yield ()
//   }

//   private def attemptImmediateWrite(
//     ctx: ChannelHandlerContext,
//     exit: ZIO[Any, Response, Response],
//     time: ServerTime,
//   ): Boolean = {
//     exit match {
//       case Exit.Success(response) if response ne null =>
//         attemptFastWrite(ctx, response, time)
//       case _                                          => false
//     }
//   }

//   private def makeZioRequest(ctx: ChannelHandlerContext, nettyReq: HttpRequest): Request = {
//     val nettyHttpVersion = nettyReq.protocolVersion()
//     val protocolVersion  = nettyHttpVersion match {
//       case HttpVersion.HTTP_1_0 => Version.Http_1_0
//       case HttpVersion.HTTP_1_1 => Version.Http_1_1
//       case _                    => throw new IllegalArgumentException(s"Unsupported HTTP version: $nettyHttpVersion")
//     }

//     val remoteAddress = ctx.channel().remoteAddress() match {
//       case m: InetSocketAddress => Option(m.getAddress)
//       case _                    => None
//     }

//     val headers     = Conversions.headersFromNetty(nettyReq.headers())
//     val contentType = headers.header(Header.ContentType)

//     nettyReq match {
//       case nettyReq: FullHttpRequest =>
// //        println(s"Got ready http request")
//         Request(
//           body = NettyBody.fromByteBuf(
//             nettyReq.content(),
//             contentType,
//           ),
//           headers = headers,
//           method = Conversions.methodFromNetty(nettyReq.method()),
//           url = URL.decode(nettyReq.uri()).getOrElse(URL.empty),
//           version = protocolVersion,
//           remoteAddress = remoteAddress,
//         )
//       case nettyReq: HttpRequest     =>
// //        println(s"Got streaming http request")
//         val handler = addAsyncBodyHandler(ctx)
//         val body    = NettyBody.fromAsync(
//           { async =>
//             handler.connect(async)
//           },
//           contentType,
//         )

//         Request(
//           body = body,
//           headers = headers,
//           method = Conversions.methodFromNetty(nettyReq.method()),
//           url = URL.decode(nettyReq.uri()).getOrElse(URL.empty),
//           version = protocolVersion,
//           remoteAddress = remoteAddress,
//         )
//     }

//   }

//   private def setServerTime(time: ServerTime, response: Response, jResponse: HttpResponse): Unit = {
//     val _ =
//       if (response.addServerTime)
//         jResponse.headers().set(HttpHeaderNames.DATE, time.refreshAndGet())
//   }

//   /*
//    * Checks if the response requires to switch protocol to websocket. Returns
//    * true if it can, otherwise returns false
//    */
//   @tailrec
//   private def upgradeToWebSocket(
//     ctx: ChannelHandlerContext,
//     jReq: HttpRequest,
//     res: Response,
//     runtime: NettyRuntime,
//   ): Unit = {
//     val app = res.socketApp
//     jReq match {
//       case jReq: FullHttpRequest =>
//         val queue = runtime.runtime(ctx).unsafe.run(Queue.unbounded[WebSocketChannelEvent]).getOrThrowFiberFailure()
//         runtime.runtime(ctx).unsafe.run {
//           val nettyChannel     = NettyChannel.make[JWebSocketFrame](ctx.channel())
//           val webSocketChannel = WebSocketChannel.make(nettyChannel, queue)
//           val webSocketApp     = app.getOrElse(Handler.unit)
//           webSocketApp.runZIO(webSocketChannel).ignoreLogged.forkDaemon
//         }
//         ctx
//           .channel()
//           .pipeline()
//           .addLast(
//             new WebSocketServerProtocolHandler(NettySocketProtocol.serverBuilder(config.webSocketConfig).build()),
//           )
//           .addLast(Names.WebSocketHandler, new WebSocketAppHandler(runtime, queue, None))

//         val retained = jReq.retainedDuplicate()
//         val _        = ctx.channel().eventLoop().submit { () => ctx.fireChannelRead(retained) }

//       case jReq: HttpRequest =>
//         val fullRequest = new DefaultFullHttpRequest(jReq.protocolVersion(), jReq.method(), jReq.uri())
//         fullRequest.headers().setAll(jReq.headers())
//         upgradeToWebSocket(ctx: ChannelHandlerContext, fullRequest, res, runtime)
//     }
//   }

//   private def writeNotFound(ctx: ChannelHandlerContext, jReq: HttpRequest)(ensured: () => Unit): Unit = {
//     // TODO: this can be done without ZIO
//     runtime.run(ctx, ensured) {
//       for {
//         response <- ZIO.succeed(HttpError.NotFound(jReq.uri()).toResponse)
//         done     <- ZIO.attempt(attemptFastWrite(ctx, response, time))
//         _        <- attemptFullWrite(ctx, response, jReq, time).unless(done)
//       } yield ()
//     }
//   }

//   private def writeResponse(
//     ctx: ChannelHandlerContext,
//     env: ZEnvironment[Any],
//     exit: ZIO[Any, Response, Response],
//     jReq: HttpRequest,
//   )(ensured: () => Unit): Unit = {
//     runtime.run(ctx, ensured) {
//       val pgm = for {
//         response <- exit.sandbox.catchAll { error =>
//           error.failureOrCause
//             .fold[UIO[Response]](
//               response => ZIO.succeed(response),
//               cause =>
//                 if (cause.isInterruptedOnly) {
//                   interrupted(ctx).as(null)
//                 } else {
//                   ZIO.succeed(withDefaultErrorResponse(null, Some(FiberFailure(cause))))
//                 },
//             )
//         }
//         _        <-
//           if (response ne null) {
//             for {
//               done <- ZIO.attempt(attemptFastWrite(ctx, response, time)).catchSomeCause { case cause =>
//                 ZIO.attempt(
//                   attemptFastWrite(ctx, withDefaultErrorResponse(null, Some(cause.squash)).freeze, time),
//                 )
//               }
//               _    <- attemptFullWrite(ctx, response, jReq, time).catchSomeCause { case cause =>
//                 ZIO.attempt(
//                   attemptFastWrite(ctx, withDefaultErrorResponse(null, Some(cause.squash)).freeze, time),
//                 )

//               }
//                 .unless(done)
//             } yield ()
//           } else {
//             ZIO.attempt(
//               if (ctx.channel().isOpen) {
//                 writeNotFound(ctx, jReq)(() => ())
//               },
//             )
//           }
//       } yield ()

//       pgm.provideEnvironment(env)
//     }
//   }

//   private def interrupted(ctx: ChannelHandlerContext): ZIO[Any, Nothing, Unit] =
//     ZIO.attempt {
//       ctx.channel().close()
//     }.unit.orDie

//   private def withDefaultErrorResponse(responseOrNull: Response, cause: Option[Throwable]): Response =
//     if (responseOrNull ne null) responseOrNull else HttpError.InternalServerError(cause = cause).toResponse
// }

// object ServerInboundHandler {

//   val live: ZLayer[
//     ServerTime with Server.Config with NettyRuntime with AppRef,
//     Nothing,
//     ServerInboundHandler,
//   ] = {
//     implicit val trace: Trace = Trace.empty
//     ZLayer.fromZIO {
//       for {
//         appRef <- ZIO.service[AppRef]
//         rtm    <- ZIO.service[NettyRuntime]
//         config <- ZIO.service[Server.Config]
//         time   <- ZIO.service[ServerTime]

//       } yield ServerInboundHandler(appRef, config, rtm, time)
//     }
//   }

// }
