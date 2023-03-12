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

package zio.http.service

import zio._
import zio.test._

import zio.http._
import zio.http.model._
import zio.http.netty.client.NettyClientDriver

object DynamicAppTest extends ZIOSpecDefault {

  val httpApp1: App[Any] = Http
    .collect[Request] { case Method.GET -> !! / "good" =>
      Response.ok
    }
    .withDefaultErrorResponse

  val httpApp2: App[Any] = Http
    .collect[Request] { case Method.GET -> !! / "better" =>
      Response.status(Status.Created)
    }
    .withDefaultErrorResponse

  val layer =
    ZLayer.make[Client & Server](
      ClientConfig.default,
      NettyClientDriver.fromConfig,
      Client.live,
      ServerConfig.live,
      Server.live,
    )

  def spec = suite("Server")(
    test("Should allow dynamic changes to the installed app") {
      for {
        port            <- Server.install(httpApp1)
        okResponse      <- Client.request(s"http://localhost:$port/good")
        _               <- Server.install(httpApp2)
        createdResponse <- Client.request(s"http://localhost:$port/better")
      } yield assertTrue(
        okResponse.status == Status.Ok &&
          createdResponse.status == Status.Created,
      ) // fails here because the response is Status.NotFound
    }.provideLayer(layer),
  )
}
