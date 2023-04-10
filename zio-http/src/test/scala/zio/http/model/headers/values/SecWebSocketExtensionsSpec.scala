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

package zio.http.model.headers.values

import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Chunk, Scope}

import zio.http.Header.SecWebSocketExtensions
import zio.http.Header.SecWebSocketExtensions.{Extension, Token}

object SecWebSocketExtensionsSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("SecWebSocketExtensions suite")(
    test("SecWebSocketExtensions should be properly parsed for a valid string") {
      val probe = "permessage-deflate; client_max_window_bits"
      assertTrue(
        SecWebSocketExtensions.parse(probe) == Right(
          SecWebSocketExtensions.Extensions(
            Chunk(
              Token(Chunk(Extension.TokenParam("permessage-deflate"), Extension.TokenParam("client_max_window_bits"))),
            ),
          ),
        ),
      )
    },
    test("SecWebSocketExtensions should be properly parsed for an empty string") {
      val probe = ""
      assertTrue(SecWebSocketExtensions.parse(probe).isLeft)
    },
    test("SecWebSocketExtensions should properly render a valid string") {
      val probe = "permessage-deflate; client_max_window_bits"
      assertTrue(
        SecWebSocketExtensions.render(
          SecWebSocketExtensions.Extensions(
            Chunk(
              Token(Chunk(Extension.TokenParam("permessage-deflate"), Extension.TokenParam("client_max_window_bits"))),
            ),
          ),
        ) == probe,
      )
    },
    test("SecWebSocket should properly parse and render a complex extension") {
      val probe = "permessage-deflate; client_max_window_bits; server_max_window_bits=15, deflate-stream"
      assertTrue(
        SecWebSocketExtensions.render(
          SecWebSocketExtensions.parse(probe).toOption.get,
        ) == probe,
      )
    },
  )
}
