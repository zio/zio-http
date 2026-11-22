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

import java.nio.charset.StandardCharsets
import java.util.concurrent.{CompletableFuture, TimeUnit}

import zio._
import zio.test.TestAspect._
import zio.test._

import zio.http._

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.{ByteBufUtil, Unpooled}
import io.netty.channel._
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioSocketChannel
import io.netty.handler.codec.http._
import io.netty.handler.ssl.util.InsecureTrustManagerFactory
import io.netty.handler.ssl.{SslCloseCompletionEvent, SslContextBuilder}

/**
 * Closing a connection whose request body is still being discarded must also
 * end the TLS session properly: the client is told the response is complete
 * with a `close_notify`, not by a bare TCP FIN, which would cut the TLS stream
 * short from its point of view.
 *
 * The test client is a Netty client with an `SslHandler`, so that the event it
 * observes is an actual `SslCloseCompletionEvent`: `isSuccess` is true only
 * when a `close_notify` was received, as opposed to the failure event Netty
 * raises when the channel closes without one.
 */
object ServerUnconsumedRequestBodyTlsSpec extends ZIOHttpSpec {

  private val responseBody = "rejected"

  private val bodySize = 64 * 1024

  private val awaitMillis = 15.seconds.toMillis

  private val sslConfig = SSLConfig.fromResource("server.crt", "server.key")

  private val config =
    Server.Config.default.onAnyOpenPort.enableRequestStreaming
      .maxDiscardedRequestBodySize(1024L)
      .ssl(sslConfig)
      .logWarningOnFatalError(false)

  private val routes: Routes[Any, Response] = Routes(
    Method.POST / "reject" -> handler(
      Response(status = Status.BadRequest, body = Body.fromString(responseBody)),
    ),
  )

  private def startServer: ZIO[Scope, Throwable, Int] =
    for {
      server <- (ZLayer.succeed(config) >>> Server.live).build
      port   <- Server.installRoutes(routes).provideEnvironment(server)
    } yield port

  private final case class TlsResponse(status: Int, contentLength: Option[Int], body: Array[Byte])

  private final class TlsClientHandler(
    response: CompletableFuture[TlsResponse],
    close: CompletableFuture[java.lang.Boolean],
  ) extends SimpleChannelInboundHandler[FullHttpResponse]() {

    override def channelRead0(ctx: ChannelHandlerContext, msg: FullHttpResponse): Unit = {
      val contentLength = Option(msg.headers().get(HttpHeaderNames.CONTENT_LENGTH)).map(_.toInt)
      response.complete(TlsResponse(msg.status().code(), contentLength, ByteBufUtil.getBytes(msg.content())))
      ()
    }

    override def userEventTriggered(ctx: ChannelHandlerContext, evt: Any): Unit =
      evt match {
        case event: SslCloseCompletionEvent =>
          close.complete(event.isSuccess): Unit
        case _                              =>
          ctx.fireUserEventTriggered(evt): Unit
      }

    override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
      close.complete(false): Unit
      response.completeExceptionally(cause): Unit
      ctx.fireExceptionCaught(cause): Unit
    }
  }

  private final case class TlsClient(channel: io.netty.channel.Channel, group: EventLoopGroup)

  private def connect(port: Int, handler: TlsClientHandler): ZIO[Scope, Throwable, TlsClient] =
    ZIO.acquireRelease(
      ZIO.attemptBlocking {
        val group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory())
        try {
          val sslContext = SslContextBuilder
            .forClient()
            .trustManager(InsecureTrustManagerFactory.INSTANCE)
            .build()
          val bootstrap  = new Bootstrap()
            .group(group)
            .channel(classOf[NioSocketChannel])
            .handler(new ChannelInitializer[SocketChannel] {
              override def initChannel(channel: SocketChannel): Unit = {
                channel.pipeline().addLast(sslContext.newHandler(channel.alloc(), "localhost", port))
                channel.pipeline().addLast(new HttpClientCodec())
                channel.pipeline().addLast(new HttpObjectAggregator(64 * 1024))
                channel.pipeline().addLast(handler)
                ()
              }
            })
          val channel    = bootstrap.connect("localhost", port).sync().channel()
          TlsClient(channel, group)
        } catch {
          case t: Throwable =>
            group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS): Unit
            throw t
        }
      },
    )(client =>
      ZIO.attemptBlocking {
        client.channel.close().sync(): Unit
        client.group.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS).sync(): Unit
      }.orDie,
    )

  private final case class TlsExchange(response: TlsResponse, closeNotify: Boolean)

  /**
   * Sends the request without its body, reads the response, and only then sends
   * the body. The body is therefore still unsent when the response completes,
   * and arrives while the server is discarding it.
   */
  private def exchange(port: Int): ZIO[Scope, Throwable, TlsExchange] =
    for {
      responseF <- ZIO.succeed(new CompletableFuture[TlsResponse]())
      closeF    <- ZIO.succeed(new CompletableFuture[java.lang.Boolean]())
      client    <- connect(port, new TlsClientHandler(responseF, closeF))
      _         <- ZIO.attemptBlocking {
        val request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/reject")
        request.headers().set(HttpHeaderNames.HOST, "localhost")
        request.headers().set(HttpHeaderNames.CONTENT_LENGTH, bodySize.toString)
        client.channel.writeAndFlush(request): Unit
      }
      response  <- ZIO.attemptBlocking(responseF.get(awaitMillis, TimeUnit.MILLISECONDS))
      _         <- ZIO.attemptBlocking {
        val body = Array.fill[Byte](bodySize)('x'.toByte)
        client.channel.writeAndFlush(new DefaultLastHttpContent(Unpooled.wrappedBuffer(body))): Unit
      }
      close     <- ZIO.attemptBlocking(closeF.get(awaitMillis, TimeUnit.MILLISECONDS).booleanValue())
    } yield TlsExchange(response, close)

  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("ServerUnconsumedRequestBody over TLS")(
      test("an over-budget request is answered in full and closed with TLS close_notify") {
        for {
          port     <- startServer
          exchange <- exchange(port)
        } yield assertTrue(
          exchange.response.status == Status.BadRequest.code,
          exchange.response.contentLength.contains(exchange.response.body.length),
          new String(exchange.response.body, StandardCharsets.UTF_8) == responseBody,
          exchange.closeNotify,
        )
      },
    ) @@ withLiveClock @@ sequential @@ timeout(1.minute)
}
