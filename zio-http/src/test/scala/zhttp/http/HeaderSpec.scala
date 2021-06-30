package zhttp.http

import zhttp.http.Header._
import zhttp.http.HeadersHelpers.BearerSchemeName
import zio.test.Assertion._
import zio.test.{DefaultRunnableSpec, assert}

object HeaderSpec extends DefaultRunnableSpec {

  def customAcceptJsonHeader: Header  = Header.custom("accept", "application/json")
  def customContentJsonHeader: Header = Header.custom("content-type", "application/json")

  val predefinedHeaders: List[Header] = List(acceptJson, contentTypeJson)
  val customHeaders: List[Header]     = List(customAcceptJsonHeader, customContentJsonHeader)

  final case class HeadersHolder(headers: List[Header]) extends HasHeaders with HeadersHelpers

  def spec = suite("Header")(
    suite("getHeader")(
      test("should not return header that doesn't exist in list") {
        val headersHolder = HeadersHolder(predefinedHeaders)
        val found         = headersHolder.getHeader("dummyHeaderName")
        assert(found)(isNone)
      },
      test("should return header from predefined headers list by String") {
        val headersHolder = HeadersHolder(predefinedHeaders)
        val found         = headersHolder.getHeader(contentTypeJson.name.toString)
        assert(found)(isSome(equalTo(contentTypeJson)))
      },
      test("should return header from predefined headers list by String of another case") {
        val headersHolder = HeadersHolder(predefinedHeaders)
        val found         = headersHolder.getHeader("Content-Type")
        assert(found)(isSome(equalTo(contentTypeJson)))
      },
      test("should return header from predefined headers list by AsciiString") {
        val headersHolder = HeadersHolder(predefinedHeaders)
        val found         = headersHolder.getHeader(contentTypeJson.name)
        assert(found)(isSome(equalTo(contentTypeJson)))
      },
      test("should return header from custom headers list by String") {
        val headersHolder = HeadersHolder(customHeaders)
        val found         = headersHolder.getHeader(contentTypeJson.name.toString)
        assert(found)(isSome(equalTo(customContentJsonHeader)))
      },
      test("should return header from custom headers list by AsciiString") {
        val headersHolder = HeadersHolder(customHeaders)
        val found         = headersHolder.getHeader(contentTypeJson.name)
        assert(found)(isSome(equalTo(customContentJsonHeader)))
      },
    ),
    suite("getHeaderValue") {
      test("should return header value") {
        val headersHolder = HeadersHolder(predefinedHeaders)
        val found         = headersHolder.getHeaderValue(contentTypeJson.name.toString)
        assert(found)(isSome(equalTo(contentTypeJson.value.toString)))
      }
    },
    suite("getContentType")(
      test("should return content-type value") {
        val headersHolder = HeadersHolder(predefinedHeaders)
        val found         = headersHolder.getContentType
        assert(found)(isSome(equalTo(contentTypeJson.value.toString)))
      },
      test("should not return content-type value if it doesn't exist") {
        val headersHolder = HeadersHolder(List(acceptJson))
        val found         = headersHolder.getContentType
        assert(found)(isNone)
      },
    ),
    suite("getAuthorizationHeader")(
      test("should return authorization value") {
        val authorizationValue = "dummyValue"
        val headersHolder      = HeadersHolder(List(createAuthorizationHeader(authorizationValue)))
        val found              = headersHolder.getAuthorization
        assert(found)(isSome(equalTo(authorizationValue)))
      },
      test("should not return authorization value if it doesn't exist") {
        val headersHolder = HeadersHolder(List(acceptJson))
        val found         = headersHolder.getContentType
        assert(found)(isNone)
      },
    ),
    suite("isJsonContentType")(
      test("should return true if content-type is application/json") {
        val headersHolder = HeadersHolder(List(contentTypeJson))
        val found         = headersHolder.isJsonContentType
        assert(found)(isTrue)
      },
      test("should return false if content-type is not application/json") {
        val headersHolder = HeadersHolder(List(contentTypeXml))
        val found         = headersHolder.isJsonContentType
        assert(found)(isFalse)
      },
      test("should return false if content-type doesn't exist") {
        val headersHolder = HeadersHolder(List(acceptJson))
        val found         = headersHolder.isJsonContentType
        assert(found)(isFalse)
      },
    ),
    suite("isPlainTextContentType")(
      test("should return true if content-type is text/plain") {
        val headersHolder = HeadersHolder(List(contentTypeTextPlain))
        val found         = headersHolder.isTextPlainContentType
        assert(found)(isTrue)
      },
      test("should return false if content-type is not text/plain") {
        val headersHolder = HeadersHolder(List(contentTypeXml))
        val found         = headersHolder.isTextPlainContentType
        assert(found)(isFalse)
      },
      test("should return false if content-type doesn't exist") {
        val headersHolder = HeadersHolder(List(acceptJson))
        val found         = headersHolder.isTextPlainContentType
        assert(found)(isFalse)
      },
    ),
    suite("isXmlContentType")(
      test("should return true if content-type is application/xml") {
        val headersHolder = HeadersHolder(List(contentTypeXml))
        val found         = headersHolder.isXmlContentType
        assert(found)(isTrue)
      },
      test("should return false if content-type is not application/xml") {
        val headersHolder = HeadersHolder(List(contentTypeTextPlain))
        val found         = headersHolder.isXmlContentType
        assert(found)(isFalse)
      },
      test("should return false if content-type doesn't exist") {
        val headersHolder = HeadersHolder(List(acceptJson))
        val found         = headersHolder.isXmlContentType
        assert(found)(isFalse)
      },
    ),
    suite("isXhtmlXmlContentType")(
      test("should return true if content-type is application/xhtml+xml") {
        val headersHolder = HeadersHolder(List(contentTypeXhtmlXml))
        val found         = headersHolder.isXhtmlXmlContentType
        assert(found)(isTrue)
      },
      test("should return false if content-type is not application/xhtml+xml") {
        val headersHolder = HeadersHolder(List(contentTypeTextPlain))
        val found         = headersHolder.isXhtmlXmlContentType
        assert(found)(isFalse)
      },
      test("should return false if content-type doesn't exist") {
        val headersHolder = HeadersHolder(List(acceptJson))
        val found         = headersHolder.isXhtmlXmlContentType
        assert(found)(isFalse)
      },
    ),
    suite("isFormUrlencodedContentType")(
      test("should return true if content-type is application/x-www-form-urlencoded") {
        val headersHolder = HeadersHolder(List(contentTypeFormUrlEncoded))
        val found         = headersHolder.isFormUrlencodedContentType
        assert(found)(isTrue)
      },
      test("should return false if content-type is not application/x-www-form-urlencoded") {
        val headersHolder = HeadersHolder(List(contentTypeTextPlain))
        val found         = headersHolder.isFormUrlencodedContentType
        assert(found)(isFalse)
      },
      test("should return false if content-type doesn't exist") {
        val headersHolder = HeadersHolder(List(acceptJson))
        val found         = headersHolder.isFormUrlencodedContentType
        assert(found)(isFalse)
      },
    ),
    suite("getBasicAuthorizationCredentials")(
      test("should decode proper basic http authorization header") {
        val headerHolder = HeadersHolder(List(Header.authorization("Basic dXNlcjpwYXNzd29yZCAxMQ==")))
        val found        = headerHolder.getBasicAuthorizationCredentials
        assert(found)(isSome(equalTo(("user", "password 11"))))
      },
      test("should decode basic http authorization header with empty name and password") {
        val headerHolder = HeadersHolder(List(Header.authorization("Basic Og==")))
        val found        = headerHolder.getBasicAuthorizationCredentials
        assert(found)(isSome(equalTo(("", ""))))
      },
      test("should not decode improper base64") {
        val headerHolder = HeadersHolder(List(Header.authorization("Basic Og=")))
        val found        = headerHolder.getBasicAuthorizationCredentials
        assert(found)(isNone)
      },
      test("should not decode only basic") {
        val headerHolder = HeadersHolder(List(Header.authorization("Basic")))
        val found        = headerHolder.getBasicAuthorizationCredentials
        assert(found)(isNone)
      },
      test("should not decode basic contained header value") {
        val headerHolder = HeadersHolder(List(Header.authorization("wrongBasic Og==")))
        val found        = headerHolder.getBasicAuthorizationCredentials
        assert(found)(isNone)
      },
      test("should get credentials for nonbasic schema") {
        val headerHolder = HeadersHolder(List(Header.authorization("DummySchema Og==")))
        val found        = headerHolder.getBasicAuthorizationCredentials
        assert(found)(isNone)
      },
      test("should decode header from Header.basicHttpAuthorization") {
        val username     = "username"
        val password     = "password"
        val headerHolder = HeadersHolder(List(Header.basicHttpAuthorization(username, password)))
        val found        = headerHolder.getBasicAuthorizationCredentials
        assert(found)(isSome(equalTo((username, password))))
      },
      test("should decode value from Header.basicHttpAuthorization") {
        val username    = "username"
        val password    = "password"
        val headerValue = Header.basicHttpAuthorization(username, password).value
        val found       = "Basic dXNlcm5hbWU6cGFzc3dvcmQ="
        assert(found)(equalTo(headerValue))
      },
    ),
    suite("getBearerToken")(
      test("should get bearer token") {
        val someToken    = "token"
        val headerValue  = String.format("%s %s", BearerSchemeName, someToken)
        val headerHolder = HeadersHolder(List(Header.authorization(headerValue)))
        val found        = headerHolder.getBearerToken
        assert(found)(isSome(equalTo(someToken)))
      },
      test("should get empty bearer token") {
        val headerValue  = String.format("%s %s", BearerSchemeName, "")
        val headerHolder = HeadersHolder(List(Header.authorization(headerValue)))
        val found        = headerHolder.getBearerToken
        assert(found)(isSome(equalTo("")))
      },
      test("should not get bearer token for nonbearer schema") {
        val headerHolder = HeadersHolder(List(Header.authorization("DummySchema token")))
        val found        = headerHolder.getBearerToken
        assert(found)(isNone)
      },
      test("should not get bearer token for bearer contained header") {
        val headerHolder = HeadersHolder(List(Header.authorization("wrongBearer token")))
        val found        = headerHolder.getBearerToken
        assert(found)(isNone)
      },
    ),
    suite("Cookie")(
      test("should return set-cookie") {
        val cookie       = Cookie("abc", "value")
        val headerHolder = HeadersHolder(List(Header.setCookie(cookie)))
        val found        = headerHolder.getSetCookie
        assert(found)(isSome(equalTo(cookie.fromCookie)))
      },
      test("should return cookie") {
        val cookie       = Cookie("abc", "value")
        val headerHolder = HeadersHolder(List(Header.cookie(cookie)))
        val found        = headerHolder.getCookie
        assert(found)(isSome(equalTo(cookie.fromCookie)))
      },
      test("should return cookies") {
        val cookie1      = Cookie("abc", "value1")
        val cookie2      = Cookie("xyz", "value2")
        val headerHolder = HeadersHolder(List(Header.cookies(List(cookie1, cookie2))))
        val found        = headerHolder.getCookie
        assert(found)(isSome(equalTo(s"""${cookie1.fromCookie};${cookie2.fromCookie}""")))
      },
      test("should remove set-cookie") {
        val headerHolder = HeadersHolder(List(Header.removeCookie("abc")))
        val found        = headerHolder.getSetCookie
        assert(found)(isSome(equalTo("abc=")))
      },
    ),
  )
}
