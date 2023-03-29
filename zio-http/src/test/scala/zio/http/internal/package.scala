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

import zio.ZLayer

import zio.http.netty.NettyConfig
import zio.http.netty.NettyConfig.LeakDetectionLevel

package object internal {

  val testServerConfig: ZLayer[Any, Nothing, Server.Config] =
    ZLayer.succeed(Server.Config.default.port(0))

  val testNettyServerConfig: ZLayer[Any, Nothing, NettyConfig] =
    ZLayer.succeed(NettyConfig.default.leakDetection(LeakDetectionLevel.PARANOID))

  val severTestLayer: ZLayer[Any, Throwable, Server.Config with Server] =
    ZLayer.make[Server.Config with Server](
      testServerConfig,
      testNettyServerConfig,
      Server.customized,
    )
}
