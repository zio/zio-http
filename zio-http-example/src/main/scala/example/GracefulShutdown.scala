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

//> using dep "dev.zio::zio-http:3.4.1"

package example

import zio._

import zio.http._

object GracefulShutdown extends ZIOAppDefault {

  val routes: Routes[Any, Response] = Handler
    .fromFunctionZIO[Request] { _ =>
      ZIO.sleep(10.seconds).debug("request handler delay done").as(Response.text("done"))
    }
    .sandbox
    .toRoutes

  override def run: ZIO[Any, Throwable, Unit] =
    (for {
      started  <- Promise.make[Nothing, Unit]
      fiber    <- Server
        .install(routes)
        .zipRight(started.succeed(()))
        .zipRight(ZIO.never)
        .provide(
          Server.live,
          ZLayer.succeed(Server.Config.default.port(8080)),
        )
        .fork
      _        <- started.await
      _        <- fiber.interrupt.delay(2.seconds).fork
      response <- ZClient.batched(Request.get(url"http://localhost:8080/test"))
      body     <- response.body.asString
      _        <- Console.printLine(response.status)
      _        <- Console.printLine(body)
    } yield ()).provide(Client.default)
}
