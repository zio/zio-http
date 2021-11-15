package zhttp.http

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http.Header._
import zhttp.http.HeaderExtension.BearerSchemeName
import zio.test.Assertion._
import zio.test.{DefaultRunnableSpec, Gen, assert, check}

object HeaderSpec extends DefaultRunnableSpec {

  def customAcceptJsonHeader: Header  = Header.custom("accept", "application/json")
  def customContentJsonHeader: Header = Header.custom("content-type", "application/json")

  val predefinedHeaders: List[Header] = List(acceptJson, contentTypeJson)
  val customHeaders: List[Header]     = List(customAcceptJsonHeader, customContentJsonHeader)

  def spec = suite("Header")(
    suite("getHeader")(
      test("should not return header that doesn't exist in list") {
        val headersHolder = HeaderExtension(predefinedHeaders)
        val found         = headersHolder.getHeader("dummyHeaderName")
        assert(found)(isNone)
      } +
        test("should return header from predefined headers list by String") {
          val headersHolder = HeaderExtension(predefinedHeaders)
          val found         = headersHolder.getHeader(contentTypeJson.name.toString)
          assert(found)(isSome(equalTo(contentTypeJson)))
        } +
        test("should return header from predefined headers list by String of another case") {
          val headersHolder = HeaderExtension(predefinedHeaders)
          val found         = headersHolder.getHeader("Content-Type")
          assert(found)(isSome(equalTo(contentTypeJson)))
        } +
        test("should return header from predefined headers list by AsciiString") {
          val headersHolder = HeaderExtension(predefinedHeaders)
          val found         = headersHolder.getHeader(contentTypeJson.name)
          assert(found)(isSome(equalTo(contentTypeJson)))
        } +
        test("should return header from custom headers list by String") {
          val headersHolder = HeaderExtension(customHeaders)
          val found         = headersHolder.getHeader(contentTypeJson.name.toString)
          assert(found)(isSome(equalTo(customContentJsonHeader)))
        } +
        test("should return header from custom headers list by AsciiString") {
          val headersHolder = HeaderExtension(customHeaders)
          val found         = headersHolder.getHeader(contentTypeJson.name)
          assert(found)(isSome(equalTo(customContentJsonHeader)))
        },
    ) +
      suite("getHeaderValue") {
        test("should return header value") {
          val headersHolder = HeaderExtension(predefinedHeaders)
          val found         = headersHolder.getHeaderValue(contentTypeJson.name.toString)
          assert(found)(isSome(equalTo(contentTypeJson.value.toString)))
        }
      } +
      suite("getContentType")(
        test("should return content-type value") {
          val headersHolder = HeaderExtension(predefinedHeaders)
          val found         = headersHolder.getContentType
          assert(found)(isSome(equalTo(contentTypeJson.value.toString)))
        } +
          test("should not return content-type value if it doesn't exist") {
            val headersHolder = HeaderExtension(List(acceptJson))
            val found         = headersHolder.getContentType
            assert(found)(isNone)
          },
      ) +
      suite("getAuthorizationHeader")(
        test("should return authorization value") {
          val authorizationValue = "dummyValue"
          val headersHolder      = HeaderExtension(List(createAuthorizationHeader(authorizationValue)))
          val found              = headersHolder.getAuthorization
          assert(found)(isSome(equalTo(authorizationValue)))
        } +
          test("should not return authorization value if it doesn't exist") {
            val headersHolder = HeaderExtension(List(acceptJson))
            val found         = headersHolder.getContentType
            assert(found)(isNone)
          },
      ) +
      suite("isJsonContentType")(
        test("should return true if content-type is application/json") {
          val headersHolder = HeaderExtension(List(contentTypeJson))
          val found         = headersHolder.isJsonContentType
          assert(found)(isTrue)
        } +
          test("should return false if content-type is not application/json") {
            val headersHolder = HeaderExtension(List(contentTypeXml))
            val found         = headersHolder.isJsonContentType
            assert(found)(isFalse)
          } +
          test("should return false if content-type doesn't exist") {
            val headersHolder = HeaderExtension(List(acceptJson))
            val found         = headersHolder.isJsonContentType
            assert(found)(isFalse)
          },
      ) +
      suite("isPlainTextContentType")(
        test("should return true if content-type is text/plain") {
          val headersHolder = HeaderExtension(List(contentTypeTextPlain))
          val found         = headersHolder.isTextPlainContentType
          assert(found)(isTrue)
        } +
          test("should return false if content-type is not text/plain") {
            val headersHolder = HeaderExtension(List(contentTypeXml))
            val found         = headersHolder.isTextPlainContentType
            assert(found)(isFalse)
          } +
          test("should return false if content-type doesn't exist") {
            val headersHolder = HeaderExtension(List(acceptJson))
            val found         = headersHolder.isTextPlainContentType
            assert(found)(isFalse)
          },
      ) +
      suite("isXmlContentType")(
        test("should return true if content-type is application/xml") {
          val headersHolder = HeaderExtension(List(contentTypeXml))
          val found         = headersHolder.isXmlContentType
          assert(found)(isTrue)
        } +
          test("should return false if content-type is not application/xml") {
            val headersHolder = HeaderExtension(List(contentTypeTextPlain))
            val found         = headersHolder.isXmlContentType
            assert(found)(isFalse)
          } +
          test("should return false if content-type doesn't exist") {
            val headersHolder = HeaderExtension(List(acceptJson))
            val found         = headersHolder.isXmlContentType
            assert(found)(isFalse)
          },
      ) +
      suite("isXhtmlXmlContentType")(
        test("should return true if content-type is application/xhtml+xml") {
          val headersHolder = HeaderExtension(List(contentTypeXhtmlXml))
          val found         = headersHolder.isXhtmlXmlContentType
          assert(found)(isTrue)
        } +
          test("should return false if content-type is not application/xhtml+xml") {
            val headersHolder = HeaderExtension(List(contentTypeTextPlain))
            val found         = headersHolder.isXhtmlXmlContentType
            assert(found)(isFalse)
          } +
          test("should return false if content-type doesn't exist") {
            val headersHolder = HeaderExtension(List(acceptJson))
            val found         = headersHolder.isXhtmlXmlContentType
            assert(found)(isFalse)
          },
      ) +
      suite("isFormUrlencodedContentType")(
        test("should return true if content-type is application/x-www-form-urlencoded") {
          val headersHolder = HeaderExtension(List(contentTypeFormUrlEncoded))
          val found         = headersHolder.isFormUrlencodedContentType
          assert(found)(isTrue)
        } +
          test("should return false if content-type is not application/x-www-form-urlencoded") {
            val headersHolder = HeaderExtension(List(contentTypeTextPlain))
            val found         = headersHolder.isFormUrlencodedContentType
            assert(found)(isFalse)
          } +
          test("should return false if content-type doesn't exist") {
            val headersHolder = HeaderExtension(List(acceptJson))
            val found         = headersHolder.isFormUrlencodedContentType
            assert(found)(isFalse)
          },
      ) +
      suite("getBasicAuthorizationCredentials")(
        test("should decode proper basic http authorization header") {
          val headerHolder = HeaderExtension(List(Header.authorization("Basic dXNlcjpwYXNzd29yZCAxMQ==")))
          val found        = headerHolder.getBasicAuthorizationCredentials
          assert(found)(isSome(equalTo(("user", "password 11"))))
        } +
          test("should decode basic http authorization header with empty name and password") {
            val headerHolder = HeaderExtension(List(Header.authorization("Basic Og==")))
            val found        = headerHolder.getBasicAuthorizationCredentials
            assert(found)(isSome(equalTo(("", ""))))
          } +
          test("should not decode improper base64") {
            val headerHolder = HeaderExtension(List(Header.authorization("Basic Og=")))
            val found        = headerHolder.getBasicAuthorizationCredentials
            assert(found)(isNone)
          } +
          test("should not decode only basic") {
            val headerHolder = HeaderExtension(List(Header.authorization("Basic")))
            val found        = headerHolder.getBasicAuthorizationCredentials
            assert(found)(isNone)
          } +
          test("should not decode basic contained header value") {
            val headerHolder = HeaderExtension(List(Header.authorization("wrongBasic Og==")))
            val found        = headerHolder.getBasicAuthorizationCredentials
            assert(found)(isNone)
          } +
          test("should get credentials for nonbasic schema") {
            val headerHolder = HeaderExtension(List(Header.authorization("DummySchema Og==")))
            val found        = headerHolder.getBasicAuthorizationCredentials
            assert(found)(isNone)
          } +
          test("should decode header from Header.basicHttpAuthorization") {
            val username     = "username"
            val password     = "password"
            val headerHolder = HeaderExtension(List(Header.basicHttpAuthorization(username, password)))
            val found        = headerHolder.getBasicAuthorizationCredentials
            assert(found)(isSome(equalTo((username, password))))
          } +
          test("should decode value from Header.basicHttpAuthorization") {
            val username    = "username"
            val password    = "password"
            val headerValue = Header.basicHttpAuthorization(username, password).value
            val found       = "Basic dXNlcm5hbWU6cGFzc3dvcmQ="
            assert(found)(equalTo(headerValue))
          },
      ) +
      suite("getBearerToken")(
        test("should get bearer token") {
          val someToken    = "token"
          val headerValue  = String.format("%s %s", BearerSchemeName, someToken)
          val headerHolder = HeaderExtension(List(Header.authorization(headerValue)))
          val found        = headerHolder.getBearerToken
          assert(found)(isSome(equalTo(someToken)))
        } +
          test("should get empty bearer token") {
            val headerValue  = String.format("%s %s", BearerSchemeName, "")
            val headerHolder = HeaderExtension(List(Header.authorization(headerValue)))
            val found        = headerHolder.getBearerToken
            assert(found)(isSome(equalTo("")))
          } +
          test("should not get bearer token for nonbearer schema") {
            val headerHolder = HeaderExtension(List(Header.authorization("DummySchema token")))
            val found        = headerHolder.getBearerToken
            assert(found)(isNone)
          } +
          test("should not get bearer token for bearer contained header") {
            val headerHolder = HeaderExtension(List(Header.authorization("wrongBearer token")))
            val found        = headerHolder.getBearerToken
            assert(found)(isNone)
          },
      ) +
      suite("getContentLength") {
        testM("should get content-length") {
          check(Gen.anyLong) { c =>
            val found = HeaderExtension(List(Header.contentLength(c))).getContentLength
            assert(found)(isSome(equalTo(c)))
          }
        } +
          test("should not return content-length value if it doesn't exist") {
            val found = HeaderExtension.empty.getContentType
            assert(found)(isNone)
          } +
          testM("should get content-length") {
            check(Gen.anyChar) { c =>
              val found = HeaderExtension(List(Header(HttpHeaderNames.CONTENT_LENGTH, c.toString))).getContentLength
              assert(found)(isNone)
            }
          }
      },
  )
}
