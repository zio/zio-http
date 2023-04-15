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

package zio.http.netty.model

import zio.Scope
import zio.test._

import zio.http.{Header, Headers}

import io.netty.handler.codec.http.websocketx.WebSocketScheme
import io.netty.handler.codec.http.{DefaultHttpHeaders, HttpHeaders, HttpScheme}

object ConversionsSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Netty conversions")(
      suite("headers")(
        test("should encode multiple cookie headers as two separate headers") {
          val cookieHeaders = Headers(Header.SetCookie.name, "x1") ++ Headers(Header.SetCookie.name, "x2")
          val result        = Conversions.headersToNetty(cookieHeaders).entries().size()
          assertTrue(result == 2)
        },
        test("should encode multiple cookie headers as two separate headers also if other headers are present") {
          val cookieHeaders = Headers(Header.SetCookie.name, "x1") ++ Headers(Header.SetCookie.name, "x2")
          val otherHeaders  = Headers(Header.ContentType.name, "application/json")
          val result        = Conversions.headersToNetty(otherHeaders ++ cookieHeaders).entries().size()
          assertTrue(result == 3)
        },
        test("header with multiple values should not be escaped") {
          val headers               = Headers("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
          val expected: HttpHeaders =
            new DefaultHttpHeaders(true).add("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
          assertTrue(Conversions.headersToNetty(headers) == expected)
        },
      ),
      suite("scheme")(
        test("java http scheme") {
          checkAll(jHttpScheme) { jHttpScheme =>
            assertTrue(Conversions.schemeFromNetty(jHttpScheme).flatMap(Conversions.schemeToNetty).get == jHttpScheme)
          }
        },
        test("java websocket scheme") {
          checkAll(jWebSocketScheme) { jWebSocketScheme =>
            assertTrue(
              Conversions
                .schemeFromNetty(jWebSocketScheme)
                .flatMap(Conversions.schemeToNettyWebSocketScheme)
                .get == jWebSocketScheme,
            )
          }
        },
      ),
    )

  private def jHttpScheme: Gen[Any, HttpScheme] = Gen.fromIterable(List(HttpScheme.HTTP, HttpScheme.HTTPS))

  private def jWebSocketScheme: Gen[Any, WebSocketScheme] =
    Gen.fromIterable(List(WebSocketScheme.WS, WebSocketScheme.WSS))
}
