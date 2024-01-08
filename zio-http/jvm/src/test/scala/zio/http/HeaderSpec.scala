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

package zio.http

import zio.NonEmptyChunk
import zio.test.Assertion._
import zio.test.assert

object HeaderSpec extends ZIOHttpSpec {

  def spec = suite("Header")(
    suite("getHeader")(
      test("should not return header that doesn't exist in list") {
        val actual = predefinedHeaders.rawHeader("dummyHeaderName")
        assert(actual)(isNone)
      },
      test("should return header from predefined headers list by String") {
        val actual = predefinedHeaders.rawHeader(Header.ContentType.name)
        assert(actual)(isSome(equalTo(MediaType.application.json.fullType)))
      },
      test("should return header from predefined headers list by String of another case") {
        val actual = predefinedHeaders.rawHeader("Content-Type")
        assert(actual)(isSome(equalTo(MediaType.application.json.fullType)))
      },
      test("should return header from predefined headers list by AsciiString") {
        val actual = predefinedHeaders.rawHeader(Header.ContentType.name)
        assert(actual)(isSome(equalTo(MediaType.application.json.fullType)))
      },
      test("should return header from custom headers list by String") {
        val actual = customHeaders.rawHeader(Header.ContentType.name)
        assert(actual)(isSome(equalTo(customContentJsonHeader.renderedValue)))
      },
      test("should return header from custom headers list by AsciiString") {
        val actual = customHeaders.rawHeader(Header.ContentType.name)
        assert(actual)(isSome(equalTo(customContentJsonHeader.renderedValue)))
      },
    ),
    suite("getHeaderValue")(
      test("should return header value") {
        val actual = predefinedHeaders.rawHeader(Header.ContentType.name)
        assert(actual)(isSome(equalTo(MediaType.application.json.fullType)))
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
    suite("cookie")(
      test("should be able to extract more than one header with the same name") {
        val firstCookie  = Cookie.Response("first", "value")
        val secondCookie = Cookie.Response("second", "value2")
        val headers      = Headers(
          Header.SetCookie(firstCookie),
          Header.SetCookie(secondCookie),
        )

        assert(headers.getAll(Header.SetCookie))(
          hasSameElements(Seq(Header.SetCookie(firstCookie), Header.SetCookie(secondCookie))),
        )
      },
      test("should return an empty sequence if no headers in the response") {
        val headers = Headers()
        assert(headers.getAll(Header.SetCookie))(hasSameElements(Seq.empty))
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
    suite("acceptEncoding")(
      /*
        zstd is not supported in zio-http but is a default header with curl's --compressed
        however as it might be added in the future, we use a string that is probably never going to be a header
       */
      test("should parse an known header") {
        val header = Header.AcceptEncoding.parse("br")
        assert(header)(isRight(equalTo(Header.AcceptEncoding.Br())))
      },
      test("should parse an known header with weight") {
        val header = Header.AcceptEncoding.parse("br;q=0.8")
        assert(header)(isRight(equalTo(Header.AcceptEncoding.Br(Some(0.8)))))
      },
      test("ignore an invalid wight") {
        val header = Header.AcceptEncoding.parse("br;q=INVALID")
        assert(header)(isRight(equalTo(Header.AcceptEncoding.Br())))
      },
      test("should parse an unknown header") {
        val header = Header.AcceptEncoding.parse("zio-http")
        assert(header)(isRight(equalTo(Header.AcceptEncoding.Unknown("zio-http"))))
      },
      test("should parse a list of accepted encodings") {
        val header = Header.AcceptEncoding.parse("gzip, br;q=0.8, zio-http")
        assert(header)(
          isRight(
            equalTo(
              Header.AcceptEncoding.Multiple(
                NonEmptyChunk(
                  Header.AcceptEncoding.GZip(),
                  Header.AcceptEncoding.Br(Some(0.8)),
                  Header.AcceptEncoding.Unknown("zio-http"),
                ),
              ),
            ),
          ),
        )
      },
      test("should parse an unknown header with weight") {
        val header = Header.AcceptEncoding.parse("zio-http;q=0.6")
        assert(header)(isRight(equalTo(Header.AcceptEncoding.Unknown("zio-http", Some(0.6d)))))
      },
    ),
    suite("isFormMultipartContentType")(
      test("should return true if content-type is multipart/form-data") {
        val actual = contentTypeFormMultipart.hasFormMultipartContentType
        assert(actual)(isTrue)
      },
      test("should return false if content-type is not multipart/form-data") {
        val actual = contentTypeTextPlain.hasFormMultipartContentType
        assert(actual)(isFalse)
      },
      test("should return false if content-type doesn't exist") {
        val actual = acceptJson.hasFormMultipartContentType
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
  private val contentTypeFormMultipart  = Headers(Header.ContentType(MediaType.multipart.`form-data`))
  private def customAcceptJsonHeader    = Header.Accept(MediaType.application.json)
  private def customContentJsonHeader   = Header.ContentType(MediaType.application.json)
  private def customHeaders: Headers    = Headers(customContentJsonHeader, customAcceptJsonHeader)

  private def predefinedHeaders: Headers =
    Headers(Header.Accept(MediaType.application.json), Header.ContentType(MediaType.application.json))
}
