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

import zio.Config.Secret
import zio.test.Assertion._
import zio.test.TestAspect.{sequential, withLiveClock}
import zio.test._
import zio.{ZIO, ZLayer}

import zio.http.internal.{DynamicServer, HttpRunnableSpec, serverTestLayer}
import zio.http.netty.NettyConfig
import zio.http.netty.client.NettyClientDriver

object ClientProxySpec extends HttpRunnableSpec {

  def clientProxySpec = suite("ClientProxySpec")(
    test("handle proxy connection failure") {
      val res =
        for {
          validServerPort <- ZIO.environmentWithZIO[DynamicServer](_.get.port)
          serverUrl       <- ZIO.fromEither(URL.decode(s"http://localhost:$validServerPort"))
          proxyUrl        <- ZIO.fromEither(URL.decode("http://localhost:0001"))
          out             <- ZClient
            .batched(Request.get(url = serverUrl))
            .provide(
              Client.customized,
              ZLayer.succeed(ZClient.Config.default.proxy(Proxy(proxyUrl))),
              NettyClientDriver.live,
              DnsResolver.default,
              ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
            )
        } yield out
      assertZIO(res.either)(isLeft(isSubtype[ConnectException](anything)))
    },
    test("ZClient proxy respond Ok") {
      val res =
        for {
          port <- ZIO.environmentWithZIO[DynamicServer](_.get.port)
          url  <- ZIO.fromEither(URL.decode(s"http://localhost:$port"))
          id   <- DynamicServer.deploy(Handler.ok.toRoutes)
          proxy = Proxy.empty.url(url).headers(Headers(DynamicServer.APP_ID, id))
          zclient <- ZIO.serviceWith[Client](_.proxy(proxy))
          out     <- zclient.batched(Request.get(url = url))
        } yield out
      assertZIO(res.either)(isRight)
    }.provideSome[DynamicServer](
      Client.default,
    ),
    test("proxy respond Ok") {
      val res =
        for {
          port <- ZIO.environmentWithZIO[DynamicServer](_.get.port)
          url  <- ZIO.fromEither(URL.decode(s"http://localhost:$port"))
          id   <- DynamicServer.deploy(Handler.ok.toRoutes)
          proxy = Proxy.empty.url(url).headers(Headers(DynamicServer.APP_ID, id))
          out <- Client
            .batched(Request.get(url = url))
            .provide(
              Client.customized,
              ZLayer.succeed(ZClient.Config.default.proxy(proxy)),
              NettyClientDriver.live,
              DnsResolver.default,
              ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
            )
        } yield out
      assertZIO(res.either)(isRight)
    },
    test("proxy respond Ok for auth server") {
      val proxyAuthApp = Handler
        .fromFunction[Request] { req =>
          if (req.hasHeader(Header.ProxyAuthorization))
            Response.ok
          else
            Response.status(Status.Forbidden)
        }
        .toRoutes

      val res =
        for {
          port <- ZIO.environmentWithZIO[DynamicServer](_.get.port)
          url  <- ZIO.fromEither(URL.decode(s"http://localhost:$port"))
          id   <- DynamicServer.deploy(proxyAuthApp)
          proxy = Proxy.empty
            .url(url)
            .headers(Headers(DynamicServer.APP_ID, id))
            .credentials(Credentials("test", Secret("test")))
          out <- Client
            .batched(Request.get(url = url))
            .provide(
              Client.customized,
              ZLayer.succeed(ZClient.Config.default.proxy(proxy)),
              NettyClientDriver.live,
              DnsResolver.default,
              ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
            )
        } yield out
      assertZIO(res.either)(isRight)
    },
  )

  override def spec: Spec[TestEnvironment, Any] = suite("ClientProxy") {
    serve.as(List(clientProxySpec))
  }.provideShared(DynamicServer.live, serverTestLayer) @@ sequential @@ withLiveClock
}
