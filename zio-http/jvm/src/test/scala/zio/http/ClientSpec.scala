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

package zio.http

import java.net.ConnectException

import scala.annotation.nowarn

import zio._
import zio.test.Assertion._
import zio.test.TestAspect.{flaky, sequential, timeout, withLiveClock}
import zio.test._

import zio.stream.ZStream

import zio.http.internal.{DynamicServer, RoutesRunnableSpec, serverTestLayer}

object ClientSpec extends RoutesRunnableSpec {

  def clientSpec = suite("ClientSpec")(
    test("respond Ok") {
      val app = Handler.ok.toRoutes.deploy(Request()).map(_.status)
      assertZIO(app)(equalTo(Status.Ok))
    },
    test("non empty content") {
      val app             = Handler.text("abc").toRoutes
      val responseContent = app.deploy(Request()).flatMap(_.body.asChunk)
      assertZIO(responseContent)(isNonEmpty)
    },
    test("echo POST request content") {
      val app = Handler.fromFunctionZIO[Request] { req => req.body.asString.map(Response.text(_)) }.sandbox.toRoutes
      val res = app.deploy(Request(method = Method.POST, body = Body.fromString("ZIO user"))).flatMap(_.body.asString)
      assertZIO(res)(equalTo("ZIO user"))
    },
    test("empty content") {
      val app             = Routes.empty
      val responseContent = app.deploy(Request()).flatMap(_.body.asString.map(_.length))
      assertZIO(responseContent)(equalTo(0))
    },
    test("text content") {
      val app             = Handler.text("zio user does not exist").toRoutes
      val responseContent = app.deploy(Request()).flatMap(_.body.asString)
      assertZIO(responseContent)(containsString("user"))
    },
    test("handle connection failure") {
      val url = URL.decode("http://localhost:1").toOption.get

      val res = ZClient.batched(Request.get(url)).either
      assertZIO(res)(isLeft(isSubtype[ConnectException](anything)))
    },
    test("streaming content to server") {
      val app    = Handler.fromFunctionZIO[Request] { req => req.body.asString.map(Response.text(_)) }.sandbox.toRoutes
      val stream = ZStream.fromIterable(List("a", "b", "c"), chunkSize = 1)
      val res    = app
        .deploy(Request(method = Method.POST, body = Body.fromCharSequenceStreamChunked(stream)))
        .flatMap(_.body.asString)
      assertZIO(res)(equalTo("abc"))
    },
    test("no trailing slash for empty path") {
      for {
        baseURL   <- DynamicServer.httpURL
        _         <- Handler.ok.toRoutes
          .deployAndRequest(c => (c @@ ZClientAspect.requestLogging()).batched.get(""))
          .runZIO(())
        loggedUrl <- ZTestLogger.logOutput.map(_.collectFirst { case m => m.annotations("url") }.mkString)
      } yield assertTrue(loggedUrl == baseURL)
    },
    test("trailing slash for explicit slash") {
      for {
        baseURL   <- DynamicServer.httpURL
        _         <- Handler.ok.toRoutes
          .deployAndRequest(c => (c @@ ZClientAspect.requestLogging()).batched.get("/"))
          .runZIO(())
        loggedUrl <- ZTestLogger.logOutput.map(_.collectFirst { case m => m.annotations("url") }.mkString)
      } yield assertTrue(loggedUrl == s"$baseURL/"): @nowarn
    },
    test("reading of unfinished body must fail") {
      val app         = Handler.fromStreamChunked(ZStream.never).sandbox.toRoutes
      val requestCode = (client: Client) =>
        (for {
          response <- ZIO.scoped(client(Request()))
          _        <- response.body.asStream.runForeach { _ => ZIO.succeed(0) }
            .timeout(60.second) // timeout just in case it hangs
        } yield ()).fold(success = _ => false, failure = _ => true)

      val effect = app.deployAndRequest(requestCode).runZIO(())
      assertZIO(effect)(isTrue)
    },
    test("request can be timed out manually while awaiting connection") {
      // Unfortunately we have to use a real URL here, as we can't really simulate a long connection time
      val url  = URL.decode("https://test.com").toOption.get
      val resp = ZClient.batched(Request.get(url)).timeout(500.millis)
      assertZIO(resp)(isNone)
    } @@ timeout(5.seconds) @@ flaky(20) @@ TestAspect.ignore, // annoying in CI
    test("authorization header without scheme") {
      val app             =
        Handler
          .fromFunction[Request] { req =>
            req.headers.get(Header.Authorization) match {
              case Some(h) => Response.text(h.renderedValue)
              case None    => Response.unauthorized("missing auth")
            }
          }
          .toRoutes
      val responseContent =
        app.deploy(Request(headers = Headers(Header.Authorization.Unparsed("", "my-token")))).flatMap(_.body.asString)
      assertZIO(responseContent)(equalTo("my-token"))
    } @@ timeout(5.seconds),
    test("URL and path manipulation on client level") {
      for {
        baseURL   <- DynamicServer.httpURL
        _         <-
          Handler.ok.toRoutes.deployAndRequest { c =>
            (c.updatePath(_ / "my-service") @@ ZClientAspect.requestLogging()).batched.get("/hello")
          }.runZIO(())
        loggedUrl <- ZTestLogger.logOutput.map(_.collectFirst { case m => m.annotations("url") }.mkString)
      } yield assertTrue(loggedUrl == baseURL + "/my-service/hello")
    },
    test("client should timeout when server sends headers but never sends body") {
      // This test reproduces the issue from https://github.com/zio/zio-http/issues/2383
      // We create a raw Netty server that sends HTTP response headers but then hangs
      // without sending the body data - similar to what broken servers like heyhttp do
      import io.netty.bootstrap.ServerBootstrap
      import io.netty.channel._
      import io.netty.channel.nio.NioIoHandler
      import io.netty.channel.socket.SocketChannel
      import io.netty.channel.socket.nio.NioServerSocketChannel
      import io.netty.handler.codec.http._

      val test = for {
        // Create a broken HTTP server using raw Netty
        bossGroup   <- ZIO.succeed(new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory()))
        workerGroup <- ZIO.succeed(new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory()))
        bootstrap = new ServerBootstrap()
          .group(bossGroup, workerGroup)
          .channel(classOf[NioServerSocketChannel])
          .childHandler(new ChannelInitializer[SocketChannel] {
            override def initChannel(ch: SocketChannel): Unit = {
              ch.pipeline().addLast(new HttpServerCodec()): Unit
              ch.pipeline()
                .addLast(new SimpleChannelInboundHandler[HttpRequest](false) {
                  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpRequest): Unit = {
                    // Send response headers with Content-Length but never send the body
                    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
                    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 1000): Unit
                    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain"): Unit
                    ctx.writeAndFlush(response): Unit
                    // Intentionally don't send body data and keep connection open - this is the bug scenario
                  }
                }): Unit
            }
          })

        channel <- ZIO.attemptBlocking(bootstrap.bind(0).sync().channel())
        port = channel.localAddress().asInstanceOf[java.net.InetSocketAddress].getPort

        // Create a client with shorter timeout for testing
        client <- ZIO.service[Client].map(_.url(URL.decode(s"http://localhost:$port").toOption.get))

        // Try to make a request and read the body - should timeout
        result <- (for {
          response <- ZIO.scoped(client(Request()))
          _        <- response.body.asString // This should timeout
        } yield false).timeout(3.seconds).map(_.getOrElse(true)).either

        // Cleanup
        _ <- ZIO.attemptBlocking {
          channel.close().sync()
          bossGroup.shutdownGracefully()
          workerGroup.shutdownGracefully()
        }
      } yield result match {
        case Left(_)      => true  // Failed with error (good)
        case Right(true)  => true  // Timed out (good)
        case Right(false) => false // Completed successfully (bad)
      }

      assertZIO(test)(isTrue)
    } @@ timeout(10.seconds), // Reproducer for #2383 - should pass with timeout implementation
    test("client should not exhaust connection pool with broken servers") {
      // This test verifies that multiple concurrent requests to broken servers
      // don't exhaust the connection pool
      import io.netty.bootstrap.ServerBootstrap
      import io.netty.channel._
      import io.netty.channel.nio.NioIoHandler
      import io.netty.channel.socket.SocketChannel
      import io.netty.channel.socket.nio.NioServerSocketChannel
      import io.netty.handler.codec.http._

      val test = for {
        // Create a broken HTTP server
        bossGroup   <- ZIO.succeed(new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory()))
        workerGroup <- ZIO.succeed(new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory()))
        bootstrap = new ServerBootstrap()
          .group(bossGroup, workerGroup)
          .channel(classOf[NioServerSocketChannel])
          .childHandler(new ChannelInitializer[SocketChannel] {
            override def initChannel(ch: SocketChannel): Unit = {
              ch.pipeline().addLast(new HttpServerCodec()): Unit
              ch.pipeline()
                .addLast(new SimpleChannelInboundHandler[HttpRequest](false) {
                  override def channelRead0(ctx: ChannelHandlerContext, msg: HttpRequest): Unit = {
                    val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
                    response.headers().set(HttpHeaderNames.CONTENT_LENGTH, 1000): Unit
                    ctx.writeAndFlush(response): Unit
                    // Don't send body - simulate broken server
                  }
                }): Unit
            }
          })

        channel <- ZIO.attemptBlocking(bootstrap.bind(0).sync().channel())
        port = channel.localAddress().asInstanceOf[java.net.InetSocketAddress].getPort
        client <- ZIO.service[Client].map(_.url(URL.decode(s"http://localhost:$port").toOption.get))

        // Make multiple concurrent requests - they should all timeout, not hang forever
        results <- ZIO.foreachPar((1 to 3).toList) { _ =>
          (for {
            response <- ZIO.scoped(client(Request()))
            _        <- response.body.asString
          } yield ()).timeout(2.seconds).as(false).catchAll(_ => ZIO.succeed(true))
        }

        // Cleanup
        _ <- ZIO.attemptBlocking {
          channel.close().sync()
          bossGroup.shutdownGracefully()
          workerGroup.shutdownGracefully()
        }
      } yield results.forall(identity) // All should have failed or timed out

      assertZIO(test)(isTrue)
    } @@ timeout(15.seconds),                                  // Reproducer for #2383 - connection pool exhaustion
  )

  override def spec = {
    suite("Client") {
      serve.as(List(clientSpec))
    }.provideShared(Scope.default, DynamicServer.live, serverTestLayer, Client.default) @@ sequential @@ withLiveClock
  }
}
