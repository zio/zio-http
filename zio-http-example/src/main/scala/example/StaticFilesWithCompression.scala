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

//> using dep "dev.zio::zio-http:3.4.0"

package example

import zio._

import zio.http._

/**
 * Serves static files from resources with response compression enabled. The
 * default configuration supports gzip and deflate (see
 * `Server.Config.ResponseCompressionConfig.default`).
 *
 * Place files under `src/main/resources/static/` to serve them at `/static/`.
 *
 * Test with: curl -H "Accept-Encoding: gzip" http://localhost:8080/hello -v
 */
object StaticFilesWithCompression extends ZIOAppDefault {

  val routes = Routes(
    Method.GET / "hello" -> handler(Response.text("Hello, World!")),
  ) @@ Middleware.serveResources(Path.empty / "static")

  val config = ZLayer.succeed(
    Server.Config.default.responseCompression(),
  )

  override def run = Server.serve(routes).provide(Server.live, config)
}
