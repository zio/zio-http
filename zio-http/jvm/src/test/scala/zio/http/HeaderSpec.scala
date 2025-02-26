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

import java.time.Instant
import java.util.UUID

import zio._
import zio.test.Assertion._
import zio.test._

import zio.schema._

object HeaderSpec extends ZIOHttpSpec {

  case class SimpleWrapper(a: String)
  implicit val simpleWrapperSchema: Schema[SimpleWrapper] = DeriveSchema.gen[SimpleWrapper]
  case class Foo(a: Int, b: SimpleWrapper, c: NonEmptyChunk[String], chunk: Chunk[String])
  implicit val fooSchema: Schema[Foo]                     = DeriveSchema.gen[Foo]

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
    suite("add typed")(
      test("primitives") {
        val uuid = "123e4567-e89b-12d3-a456-426614174000"
        assertTrue(
          Headers.empty.addHeader("a", 1).rawHeader("a").get == "1",
          Headers.empty.addHeader("a", 1.0d).rawHeader("a").get == "1.0",
          Headers.empty.addHeader("a", 1.0f).rawHeader("a").get == "1.0",
          Headers.empty.addHeader("a", 1L).rawHeader("a").get == "1",
          Headers.empty.addHeader("a", 1.toShort).rawHeader("a").get == "1",
          Headers.empty.addHeader("a", true).rawHeader("a").get == "true",
          Headers.empty.addHeader("a", 'a').rawHeader("a").get == "a",
          Headers.empty.addHeader("a", Instant.EPOCH).rawHeader("a").get == "1970-01-01T00:00:00Z",
          Headers.empty
            .addHeader("a", UUID.fromString(uuid))
            .rawHeader("a")
            .get == uuid,
        )

      },
      test("collections") {
        assertTrue(
          // Chunk
          Headers.empty.addHeader("a", Chunk.empty[Int]).rawHeader("a").isEmpty,
          Headers.empty.addHeader("a", Chunk(1)).rawHeaders("a") == Chunk("1"),
          Headers.empty.addHeader("a", Chunk(1, 2)).rawHeaders("a") == Chunk("1", "2"),
          Headers.empty.addHeader("a", Chunk(1.0, 2.0)).rawHeaders("a") == Chunk("1.0", "2.0"),
          // List
          Headers.empty.addHeader("a", List.empty[Int]).rawHeader("a").isEmpty,
          Headers.empty.addHeader("a", List(1)).rawHeaders("a") == Chunk("1"),
          // NonEmptyChunk
          Headers.empty.addHeader("a", NonEmptyChunk(1)).rawHeaders("a") == Chunk("1"),
          Headers.empty.addHeader("a", NonEmptyChunk(1, 2)).rawHeaders("a") == Chunk("1", "2"),
        )
      },
      test("case class") {
        val foo      = Foo(1, SimpleWrapper("foo"), NonEmptyChunk("1", "2"), Chunk("foo", "bar"))
        val fooEmpty = Foo(0, SimpleWrapper(""), NonEmptyChunk("1"), Chunk.empty)
        assertTrue(
          Headers.empty.addHeader(foo).rawHeader("a").get == "1",
          Headers.empty.addHeader(foo).rawHeader("b").get == "foo",
          Headers.empty.addHeader(foo).rawHeaders("c") == Chunk("1", "2"),
          Headers.empty.addHeader(foo).rawHeaders("chunk") == Chunk("foo", "bar"),
          Headers.empty.addHeader(fooEmpty).rawHeader("a").get == "0",
          Headers.empty.addHeader(fooEmpty).rawHeader("b").get == "",
          Headers.empty.addHeader(fooEmpty).rawHeaders("c") == Chunk("1"),
          Headers.empty.addHeader(fooEmpty).rawHeaders("chunk").isEmpty,
        )
      },
    ),
    suite("schema based getters")(
      test("pure") {
        val typed        = "typed"
        val default      = 3
        val invalidTyped = "invalidTyped"
        val unknown      = "non-existent"
        val headers      = Headers(typed -> "1", typed -> "2", "invalid-typed" -> "str")
        val single       = Headers(typed -> "1")
        val headersFoo   = Headers("a" -> "1", "b" -> "foo", "c" -> "2", "chunk" -> "foo", "chunk" -> "bar")
        assertTrue(
          single.header[Int](typed) == Right(1),
          headers.header[Int](invalidTyped).isLeft,
          headers.header[Int](unknown).isLeft,
          single.headerOrElse[Int](typed, default) == 1,
          headers.headerOrElse[Int](invalidTyped, default) == default,
          headers.headerOrElse[Int](unknown, default) == default,
          headers.header[Chunk[Int]](typed) == Right(Chunk(1, 2)),
          headers.header[Chunk[Int]](invalidTyped).isLeft,
          headers.header[Chunk[Int]](unknown) == Right(Chunk.empty),
          headers.header[NonEmptyChunk[Int]](unknown).isLeft,
          headers.headerOrElse[Chunk[Int]](typed, Chunk(default)) == Chunk(1, 2),
          headers.headerOrElse[Chunk[Int]](invalidTyped, Chunk(default)) == Chunk(default),
          headers.headerOrElse[Chunk[Int]](unknown, Chunk(default)) == Chunk.empty,
          headers.headerOrElse[NonEmptyChunk[Int]](unknown, NonEmptyChunk(default)) == NonEmptyChunk(default),
          // case class
          headersFoo.header[Foo] == Right(Foo(1, SimpleWrapper("foo"), NonEmptyChunk("2"), Chunk("foo", "bar"))),
          headersFoo.header[SimpleWrapper] == Right(SimpleWrapper("1")),
          headersFoo.header[SimpleWrapper]("b") == Right(SimpleWrapper("foo")),
          headers.header[Foo].isLeft,
          headersFoo.headerOrElse[Foo](Foo(0, SimpleWrapper(""), NonEmptyChunk("1"), Chunk.empty)) == Foo(
            1,
            SimpleWrapper("foo"),
            NonEmptyChunk("2"),
            Chunk("foo", "bar"),
          ),
          headers.headerOrElse[Foo](Foo(0, SimpleWrapper(""), NonEmptyChunk("1"), Chunk.empty)) == Foo(
            0,
            SimpleWrapper(""),
            NonEmptyChunk("1"),
            Chunk.empty,
          ),
        )
      },
      test("as ZIO") {
        val typed        = "typed"
        val invalidTyped = "invalidTyped"
        val unknown      = "non-existent"
        val headers      = Headers(typed -> "1", typed -> "2", "invalid-typed" -> "str")
        val single       = Headers(typed -> "1")
        assertZIO(single.headerZIO[Int](typed))(equalTo(1)) &&
        assertZIO(single.headerZIO[Int](unknown).exit)(fails(anything)) &&
        assertZIO(single.headerZIO[Chunk[Int]](typed))(hasSize(equalTo(1))) &&
        assertZIO(single.headerZIO[Chunk[Int]](unknown).exit)(succeeds(equalTo(Chunk.empty[Int]))) &&
        assertZIO(single.headerZIO[NonEmptyChunk[Int]](unknown).exit)(fails(anything)) &&
        assertZIO(headers.headerZIO[Int](invalidTyped).exit)(fails(anything)) &&
        assertZIO(headers.headerZIO[Int](unknown).exit)(fails(anything)) &&
        assertZIO(headers.headerZIO[Chunk[Int]](typed))(hasSize(equalTo(2))) &&
        assertZIO(headers.headerZIO[Chunk[Int]](invalidTyped).exit)(fails(anything)) &&
        assertZIO(headers.headerZIO[Chunk[Int]](unknown).exit)(succeeds(equalTo(Chunk.empty[Int]))) &&
        assertZIO(headers.headerZIO[NonEmptyChunk[Int]](unknown).exit)(fails(anything))
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
        val headers = Headers.empty
        assert(headers.getAll(Header.SetCookie))(hasSameElements(Seq.empty))
      },
    ),
    suite("hasMediaType")(
      test("should return true if content-type is application/json") {
        val actual = contentTypeJson.hasContentType(MediaType.application.json)
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
