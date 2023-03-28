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

import zio.test.Assertion.equalTo
import zio.test.TestAspect.{sequential, timeout}
import zio.test.{Spec, TestEnvironment, assertZIO}
import zio.{Scope, durationInt}

import zio.stream.ZStream

import zio.http.internal.{DynamicServer, HttpRunnableSpec, severTestLayer}
import zio.http.model.Method
import zio.http.netty.client.NettyClientDriver

object ClientStreamingSpec extends HttpRunnableSpec {

  def clientStreamingSpec = suite("ClientStreamingSpec")(
    test("streaming content from server - extended") {
      val app    = Http.collect[Request] { case req => Response(body = Body.fromStream(req.body.asStream)) }
      val stream = ZStream.fromIterable(List("This ", "is ", "a ", "longer ", "text."), chunkSize = 1)
      val res    = app.deployChunked.body
        .run(method = Method.POST, body = Body.fromStream(stream))
        .flatMap(_.asString)
      assertZIO(res)(equalTo("This is a longer text."))
    },
  )

  override def spec: Spec[TestEnvironment with Scope, Any] = suite("ClientProxy") {
    serve(DynamicServer.app).as(List(clientStreamingSpec))
  }.provideShared(
    DynamicServer.live,
    severTestLayer,
    Client.live,
    ClientConfig.live(ClientConfig.empty.useObjectAggregator(false)),
    NettyClientDriver.fromConfig,
    DnsResolver.default,
  ) @@
    timeout(5 seconds) @@ sequential
}
