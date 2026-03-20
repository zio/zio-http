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
 * Demonstrates graceful shutdown: the server waits for in-flight requests to
 * complete before stopping.
 *
 * Run this app, then in another terminal execute:
 *
 * curl http://localhost:8080/slow
 *
 * While the request is in progress, press Ctrl+C in the app's terminal — the
 * response will still be delivered before the server shuts down.
 */
object GracefulShutdown extends ZIOAppDefault {

  val routes = Routes(
    Method.GET / "hello" -> handler(Response.text("Hello, World!")),
    Method.GET / "slow"  -> handler {
      ZIO.sleep(5.seconds).as(Response.text("Done after 5 seconds!"))
    },
  )

  override def run =
    Server
      .serve(routes)
      .provide(
        Server.live,
        ZLayer.succeed(
          Server.Config.default
            .port(8080)
            .gracefulShutdownTimeout(10.seconds),
        ),
      )
}
