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

package zio.http.netty

import zio.test.Assertion.equalTo
import zio.test._
import zio.{Chunk, Scope}

import zio.http.Charsets

import io.netty.channel.embedded.EmbeddedChannel
import io.netty.util.AsciiString

object NettyBodySpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("NettyBody")(
      suite("fromAsync")(
        test("success") {
          val ctx     = new EmbeddedChannel()
          val message = Chunk.fromArray("Hello World".getBytes(Charsets.Http))
          val chunk   = NettyBody.fromAsync(async => async(ctx, message, isLast = true)).asChunk
          assertZIO(chunk)(equalTo(message))
        },
        test("fail") {
          val exception = new RuntimeException("Some Error")
          val error     = NettyBody.fromAsync(_ => throw exception).asChunk.flip
          assertZIO(error)(equalTo(exception))
        },
      ),
      test("FromASCIIString: toHttp") {
        check(Gen.asciiString) { payload =>
          val res = NettyBody.fromAsciiString(AsciiString.cached(payload)).asString(Charsets.Http)
          assertZIO(res)(equalTo(payload))
        }
      },
    )
}
