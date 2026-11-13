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

import zio._
import zio.test._

import zio.http.netty.NettyConfig
import zio.http.netty.client.NettyClientDriver

object SSLGenerateSpec extends ZIOHttpSpec {

  private val config = Server.Config.default.port(0).ssl(SSLConfig.generate).logWarningOnFatalError(false)

  override def spec =
    suite("SSLConfig.generate")(
      test("serves HTTPS with a generated self-signed certificate") {
        for {
          port     <- Server.installRoutes(Routes(Method.GET / "success" -> handler(Response.ok)))
          url      <- ZIO.fromEither(URL.decode(s"https://localhost:$port/success"))
          response <- Client.batched(Request.get(url))
        } yield assertTrue(response.status == Status.Ok)
      }.provide(
        Server.customized,
        ZLayer.succeed(config),
        Client.customized,
        ZLayer.succeed(ZClient.Config.default.ssl(ClientSSLConfig.Default)),
        NettyClientDriver.live,
        DnsResolver.default,
        ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
      ),
    )
}
