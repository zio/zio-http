package zhttp.http

import zhttp.http.Header._
import zio.test.Assertion.{equalTo, isFalse, isNone, isSome, isTrue}
import zio.test.{DefaultRunnableSpec, assert}

object HeaderSpec extends DefaultRunnableSpec {

  def customAcceptJsonHeader: Header  = Header.custom("accept", "application/json")
  def customContentJsonHeader: Header = Header.custom("content-type", "application/json")

  val predefinedHeaders: List[Header] = List(acceptJson, contentTypeJson)
  val customHeaders: List[Header]     = List(customAcceptJsonHeader, customContentJsonHeader)

  def spec = suite("Header")(
    suite("getHeader")(
      test("should not return header that doesn't exist in list") {
        val found = getHeader("dummyHeaderName", predefinedHeaders)
        assert(found)(isNone)
      },
      test("should return header from predefined headers list by String") {
        val found = getHeader(contentTypeJson.name.toString, predefinedHeaders)
        assert(found)(isSome(equalTo(contentTypeJson)))
      },
      test("should return header from predefined headers list by String of another case") {
        val found = getHeader("Content-Type", predefinedHeaders)
        assert(found)(isSome(equalTo(contentTypeJson)))
      },
      test("should return header from predefined headers list by AsciiString") {
        val found = getHeader(contentTypeJson.name, predefinedHeaders)
        assert(found)(isSome(equalTo(contentTypeJson)))
      },
      test("should return header from custom headers list by String") {
        val found = getHeader(contentTypeJson.name.toString, customHeaders)
        assert(found)(isSome(equalTo(customContentJsonHeader)))
      },
      test("should return header from custom headers list by AsciiString") {
        val found = getHeader(contentTypeJson.name, customHeaders)
        assert(found)(isSome(equalTo(customContentJsonHeader)))
      },
    ),
    suite("getHeaderValue") {
      test("should return header value") {
        val found = getHeaderValue(contentTypeJson.name.toString, predefinedHeaders)
        assert(found)(isSome(equalTo(contentTypeJson.value.toString)))
      }
    },
    suite("getContentType")(
      test("should return content-type value") {
        val found = getContentType(predefinedHeaders)
        assert(found)(isSome(equalTo(contentTypeJson.value.toString)))
      },
      test("should not return content-type value if it doesn't exist") {
        val found = getContentType(List(acceptJson))
        assert(found)(isNone)
      },
    ),
    suite("getAuthorizationHeader")(
      test("should return authorization value") {
        val authorizationValue = "dummyValue"
        val found              = getAuthorization(List(createAuthorizationHeader(authorizationValue)))
        assert(found)(isSome(equalTo(authorizationValue)))
      },
      test("should not return authorization value if it doesn't exist") {
        val found = getContentType(List(acceptJson))
        assert(found)(isNone)
      },
    ),
    suite("isJsonContentType")(
      test("should return true if content-type is application/json") {
        val found = isJsonContentType(List(contentTypeJson))
        assert(found)(isTrue)
      },
      test("should return false if content-type is not application/json") {
        val found = isJsonContentType(List(contentTypeXml))
        assert(found)(isFalse)
      },
      test("should return false if content-type doesn't exist") {
        val found = isJsonContentType(List(acceptJson))
        assert(found)(isFalse)
      },
    ),
    suite("isPlainTextContentType")(
      test("should return true if content-type is text/plain") {
        val found = isTextPlainContentType(List(contentTypeTextPlain))
        assert(found)(isTrue)
      },
      test("should return false if content-type is not text/plain") {
        val found = isTextPlainContentType(List(contentTypeXml))
        assert(found)(isFalse)
      },
      test("should return false if content-type doesn't exist") {
        val found = isTextPlainContentType(List(acceptJson))
        assert(found)(isFalse)
      },
    ),
    suite("isXmlContentType")(
      test("should return true if content-type is application/xml") {
        val found = isXmlContentType(List(contentTypeXml))
        assert(found)(isTrue)
      },
      test("should return false if content-type is not application/xml") {
        val found = isXmlContentType(List(contentTypeTextPlain))
        assert(found)(isFalse)
      },
      test("should return false if content-type doesn't exist") {
        val found = isXmlContentType(List(acceptJson))
        assert(found)(isFalse)
      },
    ),
    suite("isXhtmlXmlContentType")(
      test("should return true if content-type is application/xhtml+xml") {
        val found = isXhtmlXmlContentType(List(contentTypeXhtmlXml))
        assert(found)(isTrue)
      },
      test("should return false if content-type is not application/xhtml+xml") {
        val found = isXhtmlXmlContentType(List(contentTypeTextPlain))
        assert(found)(isFalse)
      },
      test("should return false if content-type doesn't exist") {
        val found = isXhtmlXmlContentType(List(acceptJson))
        assert(found)(isFalse)
      },
    ),
    suite("isFormUrlencodedContentType")(
      test("should return true if content-type is application/x-www-form-urlencoded") {
        val found = isFormUrlencodedContentType(List(contentTypeFormUrlEncoded))
        assert(found)(isTrue)
      },
      test("should return false if content-type is not application/x-www-form-urlencoded") {
        val found = isFormUrlencodedContentType(List(contentTypeTextPlain))
        assert(found)(isFalse)
      },
      test("should return false if content-type doesn't exist") {
        val found = isFormUrlencodedContentType(List(acceptJson))
        assert(found)(isFalse)
      },
    ),
  )
}
