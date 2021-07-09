package zhttp.http

import io.netty.handler.codec.http.{HttpHeaderNames => JHttpHeaderNames}
import io.netty.util.CharsetUtil
import zhttp.http.Header._
import zhttp.http.HeadersHelpers.BearerSchemeName
import zio.test.Assertion._
import zio.test.{DefaultRunnableSpec, assert}

import java.nio.charset.Charset

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
    suite("charSet spec")(
      test("should return UTF-8 charset if header contains charset UTF-8") {
        val headerHolder: HeadersHolder =
          HeadersHolder(List(Header.custom(JHttpHeaderNames.CONTENT_TYPE.toString, "text/html; charset=utf-8")))
        val found: Option[Charset]      = Some(HTTP_CHARSET)
        assert(found)(equalTo(headerHolder.getCharSet))
      },
      test("should return UTF-16 charset if header contains charset UTF-16") {
        val headerHolder: HeadersHolder =
          HeadersHolder(List(Header.custom(JHttpHeaderNames.CONTENT_TYPE.toString, "text/html; charset=utf-16")))
        val found: Option[Charset]      = Some(CharsetUtil.UTF_16)
        assert(found)(equalTo(headerHolder.getCharSet))
      },
      test("should return UTF-16BE charset if header contains charset UTF-16BE") {
        val headerHolder: HeadersHolder =
          HeadersHolder(List(Header.custom(JHttpHeaderNames.CONTENT_TYPE.toString, "text/html; charset=utf-16be")))
        val found: Option[Charset]      = Some(CharsetUtil.UTF_16BE)
        assert(found)(equalTo(headerHolder.getCharSet))
      },
      test("should return UTF_16LE charset if header contains charset UTF_16LE") {
        val headerHolder: HeadersHolder =
          HeadersHolder(List(Header.custom(JHttpHeaderNames.CONTENT_TYPE.toString, "text/html; charset=utf-16le")))
        val found: Option[Charset]      = Some(CharsetUtil.UTF_16LE)
        assert(found)(equalTo(headerHolder.getCharSet))
      },
      test("should return ISO_8859_1 charset if header contains charset ISO_8859_1") {
        val headerHolder: HeadersHolder =
          HeadersHolder(List(Header.custom(JHttpHeaderNames.CONTENT_TYPE.toString, "text/html; charset=iso-8859-1")))
        val found: Option[Charset]      = Some(CharsetUtil.ISO_8859_1)
        assert(found)(equalTo(headerHolder.getCharSet))
      },
      test("should return US_ASCII charset if header contains charset US_ASCII") {
        val headerHolder: HeadersHolder =
          HeadersHolder(
            List(
              Header.host("xyz.com"),
              Header.custom(JHttpHeaderNames.CONTENT_TYPE.toString, "text/html; charset=US-ASCII"),
            ),
          )
        val found: Option[Charset]      = Some(CharsetUtil.US_ASCII)
        assert(found)(equalTo(headerHolder.getCharSet))
      },
      test("should return default UTF-8 charset if header contain non-standard charset") {
        val headerHolder: HeadersHolder =
          HeadersHolder(
            List(
              Header.host("xyz.com"),
              Header.custom(JHttpHeaderNames.CONTENT_TYPE.toString, "text/html; charset=ISO-9"),
            ),
          )
        val found: Option[Charset]      = Some(HTTP_CHARSET)
        assert(found)(equalTo(headerHolder.getCharSet))
      },
      test("should return None if header doesn't contain content-type") {
        val headerHolder: HeadersHolder =
          HeadersHolder(List(Header.host("s")))
        val found: Option[Charset]      = None
        assert(found)(equalTo(headerHolder.getCharSet))
      },
    ),
  )
}
