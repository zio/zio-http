package zio.http.model.headers

import io.netty.handler.codec.http.{DefaultHttpHeaders, HttpHeaderNames, HttpHeaderValues, HttpHeaders}
import zio.http.middleware.Auth.Credentials
import zio.http.model.Headers.{BearerSchemeName, Header}
import zio.http.model._
import zio.test.Assertion._
import zio.test.{Gen, ZIOSpecDefault, assert, assertTrue, check}

object HeaderSpec extends ZIOSpecDefault {

  def spec = suite("Header")(
    suite("getHeader")(
      test("should not return header that doesn't exist in list") {
        val actual = predefinedHeaders.header("dummyHeaderName")
        assert(actual)(isNone)
      },
      test("should return header from predefined headers list by String") {
        val actual = predefinedHeaders.headerValue(HttpHeaderNames.CONTENT_TYPE)
        assert(actual)(isSome(equalTo(HttpHeaderValues.APPLICATION_JSON.toString)))
      },
      test("should return header from predefined headers list by String of another case") {
        val actual = predefinedHeaders.headerValue("Content-Type")
        assert(actual)(isSome(equalTo(HttpHeaderValues.APPLICATION_JSON.toString)))
      },
      test("should return header from predefined headers list by AsciiString") {
        val actual = predefinedHeaders.headerValue(HttpHeaderNames.CONTENT_TYPE)
        assert(actual)(isSome(equalTo(HttpHeaderValues.APPLICATION_JSON.toString)))
      },
      test("should return header from custom headers list by String") {
        val actual = customHeaders.header(HttpHeaderNames.CONTENT_TYPE)
        assert(actual)(isSome(equalTo(customContentJsonHeader)))
      },
      test("should return header from custom headers list by AsciiString") {
        val actual = customHeaders.header(HttpHeaderNames.CONTENT_TYPE)
        assert(actual)(isSome(equalTo(customContentJsonHeader)))
      },
    ),
    suite("getHeaderValue")(
      test("should return header value") {
        val actual = predefinedHeaders.headerValue(HttpHeaderNames.CONTENT_TYPE)
        assert(actual)(isSome(equalTo(HttpHeaderValues.APPLICATION_JSON.toString)))
      },
    ),
    suite("contentType")(
      test("should return content-type value") {
        val actual = predefinedHeaders.contentType
        assert(actual)(isSome(equalTo(HttpHeaderValues.APPLICATION_JSON.toString)))
      },
      test("should not return content-type value if it doesn't exist") {
        val actual = acceptJson.contentType
        assert(actual)(isNone)
      },
    ),
    suite("getAuthorizationHeader")(
      test("should return authorization value") {
        val authorizationValue = "dummyValue"
        val actual             = Headers.authorization(authorizationValue).authorization
        assert(actual)(isSome(equalTo(authorizationValue)))
      },
      test("should not return authorization value if it doesn't exist") {
        val actual = acceptJson.contentType
        assert(actual)(isNone)
      },
    ),
    suite("hasHeader")(
      test("should return true if content-type is application/json") {
        val actual = contentTypeJson.hasHeader(HeaderNames.contentType, HeaderValues.applicationJson)
        assert(actual)(isTrue)
      },
    ),
    suite("hasContentType")(
      test("should match content type with charsets and boundaries") {
        val header = Headers(HeaderNames.contentType, "application/json; charset=UTF-8")
        val actual = header.hasContentType("application/json")
        assert(actual)(isTrue)
      },
    ),
    suite("hasJsonContentType")(
      test("should return true if content-type is application/json") {
        val actual = contentTypeJson.hasJsonContentType
        assert(actual)(isTrue)
      },
      test("should return false if content-type is not application/json") {
        val actual = contentTypeXml.hasJsonContentType
        assert(actual)(isFalse)
      },
      test("should return false if content-type doesn't exist") {
        val headers = acceptJson
        val actual  = headers.hasJsonContentType
        assert(actual)(isFalse)
      },
    ),
    suite("hasMediaType")(
      test("should return true if content-type is application/json") {
        val actual = contentTypeJson.hasMediaType(MediaType.application.json)
        assert(actual)(isTrue)
      },
    ),
    suite("isPlainTextContentType")(
      test("should return true if content-type is text/plain") {
        val actual = contentTypeTextPlain.hasTextPlainContentType
        assert(actual)(isTrue)
      },
      test("should return false if content-type is not text/plain") {
        val actual = contentTypeXml.hasTextPlainContentType
        assert(actual)(isFalse)
      },
      test("should return false if content-type doesn't exist") {
        val actual = acceptJson.hasTextPlainContentType
        assert(actual)(isFalse)
      },
    ),
    suite("isXmlContentType")(
      test("should return true if content-type is application/xml") {
        val actual = contentTypeXml.hasXmlContentType
        assert(actual)(isTrue)
      },
      test("should return false if content-type is not application/xml") {
        val actual = contentTypeTextPlain.hasXmlContentType
        assert(actual)(isFalse)
      },
      test("should return false if content-type doesn't exist") {
        val headers = acceptJson
        val actual  = headers.hasXmlContentType
        assert(actual)(isFalse)
      },
    ),
    suite("isXhtmlXmlContentType")(
      test("should return true if content-type is application/xhtml+xml") {

        val actual = contentTypeXhtmlXml.hasXhtmlXmlContentType
        assert(actual)(isTrue)
      },
      test("should return false if content-type is not application/xhtml+xml") {
        val actual = contentTypeTextPlain.hasXhtmlXmlContentType
        assert(actual)(isFalse)
      },
      test("should return false if content-type doesn't exist") {
        val actual = acceptJson.hasXhtmlXmlContentType
        assert(actual)(isFalse)
      },
    ),
    suite("isFormUrlencodedContentType")(
      test("should return true if content-type is application/x-www-form-urlencoded") {
        val actual = contentTypeFormUrlEncoded.hasFormUrlencodedContentType
        assert(actual)(isTrue)
      },
      test("should return false if content-type is not application/x-www-form-urlencoded") {
        val actual = contentTypeTextPlain.hasFormUrlencodedContentType
        assert(actual)(isFalse)
      },
      test("should return false if content-type doesn't exist") {
        val actual = acceptJson.hasFormUrlencodedContentType
        assert(actual)(isFalse)
      },
    ),
    suite("getBasicAuthorizationCredentials")(
      test("should decode proper basic http authorization header") {
        val actual = Headers.authorization("Basic dXNlcjpwYXNzd29yZCAxMQ==").basicAuthorizationCredentials
        assert(actual)(isSome(equalTo(Credentials("user", "password 11"))))
      },
      test("should decode basic http authorization header with empty name and password") {
        val actual = Headers.authorization("Basic Og==").basicAuthorizationCredentials
        assert(actual)(isSome(equalTo(Credentials("", ""))))
      },
      test("should not decode improper base64") {
        val actual = Headers.authorization("Basic Og=").basicAuthorizationCredentials
        assert(actual)(isNone)
      },
      test("should not decode only basic") {
        val actual = Headers.authorization("Basic").basicAuthorizationCredentials
        assert(actual)(isNone)
      },
      test("should not decode basic contained header value") {
        val actual = Headers.authorization("wrongBasic Og==").basicAuthorizationCredentials
        assert(actual)(isNone)
      },
      test("should get credentials for nonbasic schema") {
        val actual = Headers.authorization("DummySchema Og==").basicAuthorizationCredentials
        assert(actual)(isNone)
      },
      test("should decode header from Header.basicHttpAuthorization") {
        val username = "username"
        val password = "password"
        val actual   = Headers.basicAuthorizationHeader(username, password).basicAuthorizationCredentials
        assert(actual)(isSome(equalTo(Credentials(username, password))))
      },
      test("should decode value from Header.basicHttpAuthorization") {
        val username = "username"
        val password = "password"
        val actual   = Headers
          .basicAuthorizationHeader(username, password)
          .headerValue(HeaderNames.authorization)
        val expected = "Basic dXNlcm5hbWU6cGFzc3dvcmQ="
        assert(actual)(isSome(equalTo(expected)))
      },
    ),
    suite("getBearerToken")(
      test("should get bearer token") {
        val token  = "token"
        val actual = Headers.authorization(String.format("%s %s", BearerSchemeName, token)).bearerToken
        assert(actual)(isSome(equalTo(token)))
      },
      test("should get empty bearer token") {
        val actual = Headers.authorization(String.format("%s %s", BearerSchemeName, "")).bearerToken
        assert(actual)(isSome(equalTo("")))
      },
      test("should not get bearer token for nonbearer schema") {
        val actual = Headers.authorization("DummySchema token").bearerToken
        assert(actual)(isNone)
      },
      test("should not get bearer token for bearer contained header") {
        val actual = Headers.authorization("wrongBearer token").bearerToken
        assert(actual)(isNone)
      },
    ),
    suite("getContentLength")(
      test("should get content-length") {
        check(Gen.long) { c =>
          val actual = Headers.contentLength(c).contentLength
          assert(actual)(isSome(equalTo(c)))
        }
      },
      test("should not return content-length value if it doesn't exist") {
        val actual = Headers.empty.contentType
        assert(actual)(isNone)
      },
      test("should get content-length") {
        check(Gen.char) { c =>
          val actual = Headers(HttpHeaderNames.CONTENT_LENGTH, c.toString).contentLength
          assert(actual)(isNone)
        }
      },
    ),
    suite("mediaType")(
      test("should correctly parse the media type") {
        val header = Headers(HeaderNames.contentType, "application/json; charset=UTF-8")
        val mt     = header.mediaType
        assertTrue(mt == Some(MediaType.application.json))
      },
    ),
    suite("encode")(
      test("should encode multiple cookie headers as two separate headers") {
        val cookieHeaders = Headers(HeaderNames.setCookie, "x1") ++ Headers(HeaderNames.setCookie, "x2")
        val result        = cookieHeaders.encode.entries().size()
        assertTrue(result == 2)
      },
      test("header with multiple values should not be escaped") {
        val headers               = Headers("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
        val expected: HttpHeaders =
          new DefaultHttpHeaders(true).add("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
        assertTrue(headers.encode == expected)
      },
    ),
  )

  private val contentTypeXhtmlXml       = Headers(HeaderNames.contentType, HeaderValues.applicationXhtml)
  private val contentTypeTextPlain      = Headers(HeaderNames.contentType, HeaderValues.textPlain)
  private val contentTypeXml            = Headers(HeaderNames.contentType, HeaderValues.applicationXml)
  private val contentTypeJson           = Headers(HeaderNames.contentType, HeaderValues.applicationJson)
  private val acceptJson                = Headers(HeaderNames.accept, HeaderValues.applicationJson)
  private val contentTypeFormUrlEncoded =
    Headers(HeaderNames.contentType, HeaderValues.applicationXWWWFormUrlencoded)
  private def customAcceptJsonHeader    = Header("accept", "application/json")
  private def customContentJsonHeader   = Header("content-type", "application/json")
  private def customHeaders: Headers    = Headers(customContentJsonHeader, customAcceptJsonHeader)

  private def predefinedHeaders: Headers = Headers(
    Header(HeaderNames.accept, HeaderValues.applicationJson),
    Header(HeaderNames.contentType, HeaderValues.applicationJson),
  )
}
