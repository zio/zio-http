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
import zio.test.TestAspect.withLiveClock
import zio.test._

import zio.http.netty.NettyConfig
import zio.http.netty.client.NettyClientDriver

object DynamicAppTest extends ZIOSpecDefault {
  def extractStatus(response: Response): Status = response.status

  val httpApp1: HttpApp2[Any] =
    Routes(
      Method.GET / "good" -> Handler.ok,
    ).ignore.toApp

  val httpApp2: HttpApp2[Any] =
    Routes(
      Method.GET / "better" -> handler(Response.status(Status.Created)),
    ).ignore.toApp

  val layer =
    ZLayer.make[Client & Server & Scope](
      ZLayer.succeed(ZClient.Config.default),
      NettyClientDriver.live,
      Client.customized,
      ZLayer.succeed(Server.Config.default.onAnyOpenPort),
      Server.live,
      DnsResolver.default,
      ZLayer.succeed(NettyConfig.default),
      Scope.default,
    )

  def spec = suite("Server")(
    test("Should allow dynamic changes to the installed app") {
      for {
        port <- Server.install(httpApp1)
        good   = URL.decode(s"http://localhost:$port/good").toOption.get
        better = URL.decode(s"http://localhost:$port/better").toOption.get
        okResponse      <- Client.request(Request.get(good))
        _               <- Server.install(httpApp2)
        createdResponse <- Client.request(Request.get(better))
      } yield assertTrue(
        extractStatus(okResponse) == Status.Ok &&
          extractStatus(createdResponse) == Status.Created,
      ) // fails here because the response is Status.NotFound
    }.provideLayer(layer) @@ withLiveClock,
  )
}
