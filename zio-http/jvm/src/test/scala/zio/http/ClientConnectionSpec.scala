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

import java.net.{InetAddress, UnknownHostException}

import zio._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

import zio.http.internal.{DynamicServer, HttpRunnableSpec, serverTestLayer}
import zio.http.netty.NettyConfig

object ClientConnectionSpec extends HttpRunnableSpec {

  private def tests =
    List(
      test("tries a different IP address when the connection fails") {
        val app = Handler.ok.toRoutes.deploy(Request()).map(_.status)
        assertZIO(app)(equalTo(Status.Ok))
      } @@ nonFlaky(10),
    )

  override def spec = {
    suite("ClientConnectionSpec") {
      serve.as(tests)
    }.provideSome[DynamicServer & Server & Client](Scope.default)
      .provideShared(
        DynamicServer.live,
        serverTestLayer,
        Client.live,
        ZLayer.succeed(Client.Config.default.connectionTimeout(10.millis)),
        ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
        ZLayer.succeed(TestResolver),
      ) @@ sequential @@ withLiveClock @@ withLiveRandom
  }

  private object TestResolver extends DnsResolver {
    import scala.collection.compat._

    override def resolve(host: String)(implicit trace: Trace): ZIO[Any, UnknownHostException, Chunk[InetAddress]] = {
      ZIO.succeed {
        Chunk.from((0 to 10).map { i =>
          InetAddress.getByAddress(Array(127, 0, 0, i.toByte))
        })
      }
    }
  }
}
