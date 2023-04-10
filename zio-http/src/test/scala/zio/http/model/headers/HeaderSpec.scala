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

package zio.http.model.headers

import zio.test.Assertion._
import zio.test.{ZIOSpecDefault, assert}

import zio.http._

import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues}

object HeaderSpec extends ZIOSpecDefault {

  def spec = suite("Header")(
    suite("getHeader")(
      test("should not return header that doesn't exist in list") {
        val actual = predefinedHeaders.rawHeader("dummyHeaderName")
        assert(actual)(isNone)
      },
      test("should return header from predefined headers list by String") {
        val actual = predefinedHeaders.rawHeader(HttpHeaderNames.CONTENT_TYPE)
        assert(actual)(isSome(equalTo(HttpHeaderValues.APPLICATION_JSON.toString)))
      },
      test("should return header from predefined headers list by String of another case") {
        val actual = predefinedHeaders.rawHeader("Content-Type")
        assert(actual)(isSome(equalTo(HttpHeaderValues.APPLICATION_JSON.toString)))
      },
      test("should return header from predefined headers list by AsciiString") {
        val actual = predefinedHeaders.rawHeader(HttpHeaderNames.CONTENT_TYPE)
        assert(actual)(isSome(equalTo(HttpHeaderValues.APPLICATION_JSON.toString)))
      },
      test("should return header from custom headers list by String") {
        val actual = customHeaders.rawHeader(HttpHeaderNames.CONTENT_TYPE)
        assert(actual)(isSome(equalTo(customContentJsonHeader.renderedValue)))
      },
      test("should return header from custom headers list by AsciiString") {
        val actual = customHeaders.rawHeader(HttpHeaderNames.CONTENT_TYPE)
        assert(actual)(isSome(equalTo(customContentJsonHeader.renderedValue)))
      },
    ),
    suite("getHeaderValue")(
      test("should return header value") {
        val actual = predefinedHeaders.rawHeader(HttpHeaderNames.CONTENT_TYPE)
        assert(actual)(isSome(equalTo(HttpHeaderValues.APPLICATION_JSON.toString)))
      },
    ),
    suite("hasHeader")(
      test("should return true if content-type header is present") {
        val actual = contentTypeJson.hasHeader(Header.ContentType)
        assert(actual)(isTrue)
      },
    ),
    suite("hasContentType")(
      test("should match content type with charsets and boundaries") {
        val header = Headers(Header.ContentType.name, "application/json; charset=UTF-8")
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
  )

  private val acceptJson                = Headers(Header.Accept(MediaType.application.json))
  private val contentTypeXhtmlXml       = Headers(Header.ContentType(MediaType.application.`xhtml+xml`))
  private val contentTypeTextPlain      = Headers(Header.ContentType(MediaType.text.plain))
  private val contentTypeXml            = Headers(Header.ContentType(MediaType.application.xml))
  private val contentTypeJson           = Headers(Header.ContentType(MediaType.application.json))
  private val contentTypeFormUrlEncoded = Headers(Header.ContentType(MediaType.application.`x-www-form-urlencoded`))
  private def customAcceptJsonHeader    = Header.Accept(MediaType.application.json)
  private def customContentJsonHeader   = Header.ContentType(MediaType.application.json)
  private def customHeaders: Headers    = Headers(customContentJsonHeader, customAcceptJsonHeader)

  private def predefinedHeaders: Headers =
    Headers(Header.Accept(MediaType.application.json), Header.ContentType(MediaType.application.json))
}
