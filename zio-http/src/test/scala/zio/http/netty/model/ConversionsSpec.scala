package zio.http.netty.model

import zio.Scope
import zio.test._

import zio.http.model.{HeaderNames, Headers}

import io.netty.handler.codec.http.websocketx.WebSocketScheme
import io.netty.handler.codec.http.{DefaultHttpHeaders, HttpHeaders, HttpScheme}

object ConversionsSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("Netty conversions")(
      suite("headers")(
        test("should encode multiple cookie headers as two separate headers") {
          val cookieHeaders = Headers(HeaderNames.setCookie, "x1") ++ Headers(HeaderNames.setCookie, "x2")
          val result        = Conversions.headersToNetty(cookieHeaders).entries().size()
          assertTrue(result == 2)
        },
        test("should encode multiple cookie headers as two separate headers also if other headers are present") {
          val cookieHeaders = Headers(HeaderNames.setCookie, "x1") ++ Headers(HeaderNames.setCookie, "x2")
          val otherHeaders  = Headers(HeaderNames.contentType, "application/json")
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
