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

import scala.annotation.nowarn

import zio.Scope
import zio.test._

import zio.http.{Header, Headers, Status, Version, ZIOHttpSpec}

import io.netty.handler.codec.http.websocketx.WebSocketScheme
import io.netty.handler.codec.http.{DefaultHttpHeaders, HttpHeaders, HttpResponseStatus, HttpScheme, HttpVersion}

@nowarn("msg=possible missing interpolator")
object ConversionsSpec extends ZIOHttpSpec {
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
            new DefaultHttpHeaders().add("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
          assertTrue(Conversions.headersToNetty(headers) == expected)
        },
        test("should encode multiple headers with the same name") {
          val headers =
            Headers(Header.Custom("WWW-Authenticate", "Bearer")) ++ Headers(Header.Custom("WWW-Authenticate", "Basic"))
          val result  = Conversions.headersToNetty(headers).entries().size()
          assertTrue(result == 2)
        },
        test("should deduplicate singleton Content-Type header (last wins)") {
          val headers = Headers("content-type", "text/plain") ++ Headers("content-type", "application/json")
          val result  = Conversions.headersToNetty(headers)
          assertTrue(
            result.entries().size() == 1,
            result.get("content-type") == "application/json",
          )
        },
        test("should deduplicate mixed-case singleton Content-Type header (last wins)") {
          val headers = Headers("Content-Type", "text/plain") ++ Headers("content-type", "application/json")
          val result  = Conversions.headersToNetty(headers)
          assertTrue(
            result.entries().size() == 1,
            result.get("content-type") == "application/json",
          )
        },
        test("should deduplicate Accept headers (last wins)") {
          val headers = Headers("accept", "text/html") ++ Headers("accept", "application/json")
          val result  = Conversions.headersToNetty(headers)
          assertTrue(
            result.entries().size() == 1,
            result.get("accept") == "application/json",
          )
        },
        test("should preserve duplicate Set-Cookie headers") {
          val headers = Headers("set-cookie", "a=1") ++ Headers("set-cookie", "b=2")
          val result  = Conversions.headersToNetty(headers)
          assertTrue(result.entries().size() == 2)
        },
        test("should preserve duplicate WWW-Authenticate headers") {
          val headers = Headers("www-authenticate", "Bearer") ++ Headers("www-authenticate", "Basic")
          val result  = Conversions.headersToNetty(headers)
          assertTrue(result.entries().size() == 2)
        },
        test("should deduplicate singleton Host header (last wins)") {
          val headers = Headers("host", "example.com") ++ Headers("host", "other.com")
          val result  = Conversions.headersToNetty(headers)
          assertTrue(
            result.entries().size() == 1,
            result.get("host") == "other.com",
          )
        },
        test("should deduplicate singleton Authorization header (last wins)") {
          val headers = Headers("authorization", "Bearer token1") ++ Headers("authorization", "Bearer token2")
          val result  = Conversions.headersToNetty(headers)
          assertTrue(
            result.entries().size() == 1,
            result.get("authorization") == "Bearer token2",
          )
        },
        test("should deduplicate custom headers (last wins)") {
          val headers = Headers("x-custom", "value1") ++ Headers("x-custom", "value2")
          val result  = Conversions.headersToNetty(headers)
          assertTrue(
            result.entries().size() == 1,
            result.get("x-custom") == "value2",
          )
        },
        test("should preserve duplicate Via headers") {
          val headers = Headers("via", "1.0 proxy1") ++ Headers("via", "1.1 proxy2")
          val result  = Conversions.headersToNetty(headers)
          assertTrue(
            result.entries().size() == 2,
            result.getAll("via").get(0) == "1.0 proxy1",
            result.getAll("via").get(1) == "1.1 proxy2",
          )
        },
        test("should preserve duplicate Proxy-Authenticate headers") {
          val headers =
            Headers("proxy-authenticate", "Bearer") ++ Headers("proxy-authenticate", "Basic")
          val result  = Conversions.headersToNetty(headers)
          assertTrue(
            result.entries().size() == 2,
            result.getAll("proxy-authenticate").get(0) == "Bearer",
            result.getAll("proxy-authenticate").get(1) == "Basic",
          )
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
        suite("Versions")(
          test("Should correctly convert from zio.http to Netty.") {

            assertTrue(
              Conversions.versionToNetty(Version.Http_1_0) == HttpVersion.HTTP_1_0,
              Conversions.versionToNetty(Version.Http_1_1) == HttpVersion.HTTP_1_1,
            )
          },
        ),
      ),
      suite("status")(
        test("statusToNetty returns cached Netty instance for standard status codes") {
          val standardStatuses = List(
            Status.Ok,
            Status.NotFound,
            Status.InternalServerError,
            Status.BadRequest,
            Status.Created,
            Status.NoContent,
            Status.MovedPermanently,
            Status.Found,
            Status.Forbidden,
            Status.Unauthorized,
          )
          assertTrue(
            standardStatuses.forall { status =>
              val nettyStatus = Conversions.statusToNetty(status)
              // valueOf(int) returns the cached singleton; reference equality proves no new allocation
              nettyStatus eq HttpResponseStatus.valueOf(status.code)
            },
          )
        },
        test("statusToNetty preserves custom reason phrase for Custom status") {
          val custom      = Status.Custom(299, "My Custom Reason")
          val nettyStatus = Conversions.statusToNetty(custom)
          assertTrue(
            nettyStatus.code() == 299,
            nettyStatus.reasonPhrase() == "My Custom Reason",
          )
        },
        test("statusToNetty preserves empty reason phrase for Custom status") {
          // Use a non-standard code (999) with an empty reason phrase to verify that
          // statusToNetty calls valueOf(code, reasonPhrase) — not valueOf(code) — for
          // Custom statuses. valueOf(999) would default to "Unknown Status (999)",
          // breaking round-trip equality in statusFromNetty.
          val custom      = Status.Custom(999, "")
          val nettyStatus = Conversions.statusToNetty(custom)
          assertTrue(
            nettyStatus.code() == 999,
            nettyStatus.reasonPhrase() == "",
          )
        },
      ),
    )

  private def jHttpScheme: Gen[Any, HttpScheme] = Gen.fromIterable(List(HttpScheme.HTTP, HttpScheme.HTTPS))

  private def jWebSocketScheme: Gen[Any, WebSocketScheme] =
    Gen.fromIterable(List(WebSocketScheme.WS, WebSocketScheme.WSS))
}
