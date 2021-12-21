package zhttp.http

import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues}
import zhttp.http.Headers.BearerSchemeName
import zhttp.http.Headers.Literals._
import zio.test.Assertion._
import zio.test.{DefaultRunnableSpec, Gen, assert, check}

object HeaderSpec extends DefaultRunnableSpec {

  def spec = suite("Header") {
    suite("getHeader")(
      test("should not return header that doesn't exist in list") {
        val actual = predefinedHeaders.getHeader("dummyHeaderName")
        assert(actual)(isNone)
      } +
        test("should return header from predefined headers list by String") {
          val actual = predefinedHeaders.getHeaderValue(HttpHeaderNames.CONTENT_TYPE)
          assert(actual)(isSome(equalTo(HttpHeaderValues.APPLICATION_JSON.toString)))
        } +
        test("should return header from predefined headers list by String of another case") {
          val actual = predefinedHeaders.getHeaderValue("Content-Type")
          assert(actual)(isSome(equalTo(HttpHeaderValues.APPLICATION_JSON.toString)))
        } +
        test("should return header from predefined headers list by AsciiString") {
          val actual = predefinedHeaders.getHeaderValue(HttpHeaderNames.CONTENT_TYPE)
          assert(actual)(isSome(equalTo(HttpHeaderValues.APPLICATION_JSON.toString)))
        } +
        test("should return header from custom headers list by String") {
          val actual = customHeaders.getHeader(HttpHeaderNames.CONTENT_TYPE)
          assert(actual)(isSome(equalTo(customContentJsonHeader)))
        } +
        test("should return header from custom headers list by AsciiString") {
          val actual = customHeaders.getHeader(HttpHeaderNames.CONTENT_TYPE)
          assert(actual)(isSome(equalTo(customContentJsonHeader)))
        },
    ) +
      suite("getHeaderValue") {
        test("should return header value") {
          val actual = predefinedHeaders.getHeaderValue(HttpHeaderNames.CONTENT_TYPE)
          assert(actual)(isSome(equalTo(HttpHeaderValues.APPLICATION_JSON.toString)))
        }
      } +
      suite("getContentType")(
        test("should return content-type value") {
          val actual = predefinedHeaders.getContentType
          assert(actual)(isSome(equalTo(HttpHeaderValues.APPLICATION_JSON.toString)))
        } +
          test("should not return content-type value if it doesn't exist") {
            val actual = acceptJson.getContentType
            assert(actual)(isNone)
          },
      ) +
      suite("getAuthorizationHeader")(
        test("should return authorization value") {
          val authorizationValue = "dummyValue"
          val actual             = Headers.authorization(authorizationValue).getAuthorization
          assert(actual)(isSome(equalTo(authorizationValue)))
        } +
          test("should not return authorization value if it doesn't exist") {
            val actual = acceptJson.getContentType
            assert(actual)(isNone)
          },
      ) +
      suite("hasJsonContentType")(
        test("should return true if content-type is application/json") {
          val actual = contentTypeJson.hasJsonContentType
          assert(actual)(isTrue)
        } +
          test("should return false if content-type is not application/json") {
            val actual = contentTypeXml.hasJsonContentType
            assert(actual)(isFalse)
          } +
          test("should return false if content-type doesn't exist") {
            val headers = acceptJson
            val actual  = headers.hasJsonContentType
            assert(actual)(isFalse)
          },
      ) +
      suite("isPlainTextContentType")(
        test("should return true if content-type is text/plain") {
          val actual = contentTypeTextPlain.hasTextPlainContentType
          assert(actual)(isTrue)
        } +
          test("should return false if content-type is not text/plain") {
            val actual = contentTypeXml.hasTextPlainContentType
            assert(actual)(isFalse)
          } +
          test("should return false if content-type doesn't exist") {
            val actual = acceptJson.hasTextPlainContentType
            assert(actual)(isFalse)
          },
      ) +
      suite("isXmlContentType")(
        test("should return true if content-type is application/xml") {
          val actual = contentTypeXml.hasXmlContentType
          assert(actual)(isTrue)
        } +
          test("should return false if content-type is not application/xml") {
            val actual = contentTypeTextPlain.hasXmlContentType
            assert(actual)(isFalse)
          } +
          test("should return false if content-type doesn't exist") {
            val headers = acceptJson
            val actual  = headers.hasXmlContentType
            assert(actual)(isFalse)
          },
      ) +
      suite("isXhtmlXmlContentType")(
        test("should return true if content-type is application/xhtml+xml") {

          val actual = contentTypeXhtmlXml.hasXhtmlXmlContentType
          assert(actual)(isTrue)
        } +
          test("should return false if content-type is not application/xhtml+xml") {
            val actual = contentTypeTextPlain.hasXhtmlXmlContentType
            assert(actual)(isFalse)
          } +
          test("should return false if content-type doesn't exist") {
            val actual = acceptJson.hasXhtmlXmlContentType
            assert(actual)(isFalse)
          },
      ) +
      suite("isFormUrlencodedContentType")(
        test("should return true if content-type is application/x-www-form-urlencoded") {
          val actual = contentTypeFormUrlEncoded.hasFormUrlencodedContentType
          assert(actual)(isTrue)
        } +
          test("should return false if content-type is not application/x-www-form-urlencoded") {
            val actual = contentTypeTextPlain.hasFormUrlencodedContentType
            assert(actual)(isFalse)
          } +
          test("should return false if content-type doesn't exist") {
            val actual = acceptJson.hasFormUrlencodedContentType
            assert(actual)(isFalse)
          },
      ) +
      suite("getBasicAuthorizationCredentials")(
        test("should decode proper basic http authorization header") {
          val actual = Headers.authorization("Basic dXNlcjpwYXNzd29yZCAxMQ==").getBasicAuthorizationCredentials
          assert(actual)(isSome(equalTo(("user", "password 11"))))
        } +
          test("should decode basic http authorization header with empty name and password") {
            val actual = Headers.authorization("Basic Og==").getBasicAuthorizationCredentials
            assert(actual)(isSome(equalTo(("", ""))))
          } +
          test("should not decode improper base64") {
            val actual = Headers.authorization("Basic Og=").getBasicAuthorizationCredentials
            assert(actual)(isNone)
          } +
          test("should not decode only basic") {
            val actual = Headers.authorization("Basic").getBasicAuthorizationCredentials
            assert(actual)(isNone)
          } +
          test("should not decode basic contained header value") {
            val actual = Headers.authorization("wrongBasic Og==").getBasicAuthorizationCredentials
            assert(actual)(isNone)
          } +
          test("should get credentials for nonbasic schema") {
            val actual = Headers.authorization("DummySchema Og==").getBasicAuthorizationCredentials
            assert(actual)(isNone)
          } +
          test("should decode header from Header.basicHttpAuthorization") {
            val username = "username"
            val password = "password"
            val actual   = Headers.basicAuthorizationHeader(username, password).getBasicAuthorizationCredentials
            assert(actual)(isSome(equalTo((username, password))))
          } +
          test("should decode value from Header.basicHttpAuthorization") {
            val username = "username"
            val password = "password"
            val actual   = Headers
              .basicAuthorizationHeader(username, password)
              .getHeaderValue(Name.Authorization)
            val expected = "Basic dXNlcm5hbWU6cGFzc3dvcmQ="
            assert(actual)(isSome(equalTo(expected)))
          },
      ) +
      suite("getBearerToken")(
        test("should get bearer token") {
          val token  = "token"
          val actual = Headers.authorization(String.format("%s %s", BearerSchemeName, token)).getBearerToken
          assert(actual)(isSome(equalTo(token)))
        } +
          test("should get empty bearer token") {
            val actual = Headers.authorization(String.format("%s %s", BearerSchemeName, "")).getBearerToken
            assert(actual)(isSome(equalTo("")))
          } +
          test("should not get bearer token for nonbearer schema") {
            val actual = Headers.authorization("DummySchema token").getBearerToken
            assert(actual)(isNone)
          } +
          test("should not get bearer token for bearer contained header") {
            val actual = Headers.authorization("wrongBearer token").getBearerToken
            assert(actual)(isNone)
          },
      ) +
      suite("getContentLength") {
        testM("should get content-length") {
          check(Gen.anyLong) { c =>
            val actual = Headers.contentLength(c).getContentLength
            assert(actual)(isSome(equalTo(c)))
          }
        } +
          test("should not return content-length value if it doesn't exist") {
            val actual = Headers.empty.getContentType
            assert(actual)(isNone)
          } +
          testM("should get content-length") {
            check(Gen.anyChar) { c =>
              val actual = Headers(HttpHeaderNames.CONTENT_LENGTH, c.toString).getContentLength
              assert(actual)(isNone)
            }
          }
      }
  }

  private val contentTypeXhtmlXml       = Headers(Name.ContentType, Value.ApplicationXhtml)
  private val contentTypeTextPlain      = Headers(Name.ContentType, Value.ApplicationJson)
  private val contentTypeXml            = Headers(Name.ContentType, Value.ApplicationXml)
  private val contentTypeJson           = Headers(Name.ContentType, Value.ApplicationJson)
  private val acceptJson                = Headers(Name.Accept, Value.ApplicationJson)
  private val contentTypeFormUrlEncoded = Headers(Name.ContentType, Value.ApplicationXWWWFormUrlencoded)
  private def customAcceptJsonHeader    = ("accept", "application/json")
  private def customContentJsonHeader   = ("content-type", "application/json")
  private def customHeaders: Headers    = Headers(customContentJsonHeader) ++ Headers(customAcceptJsonHeader)

  private def predefinedHeaders: Headers = Headers {
    Name.Accept      -> Value.ApplicationJson
    Name.ContentType -> Value.ApplicationJson
  }
}
