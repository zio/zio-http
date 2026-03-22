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

import java.io.File

import zio.json.{DeriveJsonDecoder, DeriveJsonEncoder, JsonDecoder, JsonEncoder}
import zio.test.Assertion.equalTo
import zio.test.TestAspect.timeout
import zio.test._
import zio.{Chunk, Scope, ZIO, durationInt}

import zio.stream.ZStream

import zio.schema.{DeriveSchema, Schema}

object BodySpec extends ZIOHttpSpec {
  private val testFile = new File(getClass.getResource("/TestFile.txt").getPath)

  case class Person(name: String, age: Int)

  object Person {
    implicit val schema: Schema[Person]       = DeriveSchema.gen[Person]
    implicit val encoder: JsonEncoder[Person] = DeriveJsonEncoder.gen[Person]
    implicit val decoder: JsonDecoder[Person] = DeriveJsonDecoder.gen[Person]
  }

  override def spec: Spec[TestEnvironment with Scope, Throwable] =
    suite("BodySpec")(
      suite("outgoing")(
        suite("encode")(
          suite("fromStream")(
            test("success") {
              check(Gen.string) { payload =>
                val stringBuffer    = payload.getBytes(Charsets.Http)
                val responseContent = ZStream.fromIterable(stringBuffer, chunkSize = 2)
                val res             = Body.fromStreamChunked(responseContent).asString(Charsets.Http)
                assertZIO(res)(equalTo(payload))
              }
            },
          ),
          suite("fromFile")(
            test("success") {
              lazy val file = testFile
              val res       = Body.fromFile(file).flatMap(_.asString(Charsets.Http))
              assertZIO(res)(equalTo("foo\nbar"))
            },
            test("success small chunk") {
              lazy val file = testFile
              val res       = Body.fromFile(file, 3).flatMap(_.asString(Charsets.Http))
              assertZIO(res)(equalTo("foo\nbar"))
            },
          ),
        ),
      ),
      suite("json")(
        suite("Body.json with zio-schema")(
          test("creates JSON body from case class") {
            val person = Person("John", 42)
            val body   = Body.json(person)
            for {
              content <- body.asString
            } yield assertTrue(
              content.contains("John"),
              content.contains("42"),
              body.mediaType.contains(MediaType.application.json),
            )
          },
          test("round-trip encoding and decoding") {
            val person = Person("Alice", 30)
            val body   = Body.json(person)
            for {
              decoded <- body.asJson[Person]
            } yield assertTrue(decoded == person)
          },
        ),
        suite("Body.json with zio-json")(
          test("creates JSON body from case class") {
            val person = Person("Jane", 25)
            val body   = Body.jsonCodec(person)
            for {
              content <- body.asString
            } yield assertTrue(
              content.contains("Jane"),
              content.contains("25"),
              body.mediaType.contains(MediaType.application.json),
            )
          },
          test("round-trip encoding and decoding") {
            val person = Person("Bob", 35)
            val body   = Body.jsonCodec(person)
            for {
              decoded <- body.asJsonFromCodec[Person]
            } yield assertTrue(decoded == person)
          },
        ),
        suite("Body.asJson with zio-schema")(
          test("decodes JSON string to case class") {
            val jsonString = """{"name":"Charlie","age":28}"""
            val body       = Body.fromString(jsonString)
            for {
              decoded <- body.asJson[Person]
            } yield assertTrue(
              decoded.name == "Charlie",
              decoded.age == 28,
            )
          },
          test("fails on invalid JSON") {
            val invalidJson = """{"name":"Invalid"}"""
            val body        = Body.fromString(invalidJson)
            for {
              result <- body.asJson[Person].exit
            } yield assertTrue(result.isFailure)
          },
        ),
        suite("Body.asJsonZio with zio-json")(
          test("decodes JSON string to case class") {
            val jsonString = """{"name":"David","age":40}"""
            val body       = Body.fromString(jsonString)
            for {
              decoded <- body.asJsonFromCodec[Person]
            } yield assertTrue(
              decoded.name == "David",
              decoded.age == 40,
            )
          },
          test("fails on invalid JSON") {
            val invalidJson = """{"invalid":"json"}"""
            val body        = Body.fromString(invalidJson)
            for {
              result <- body.asJsonFromCodec[Person].exit
            } yield assertTrue(result.isFailure)
          },
          test("fails on malformed JSON") {
            val malformedJson = """not valid json"""
            val body          = Body.fromString(malformedJson)
            for {
              result <- body.asJsonFromCodec[Person].exit
            } yield assertTrue(result.isFailure)
          },
        ),
      ),
      suite("mediaType")(
        test("updates the Body media type with the provided value") {
          val body = Body.fromString("test").contentType(MediaType.text.plain)
          assertTrue(body.mediaType == Option(MediaType.text.plain))
        },
      ),
      suite("knownContentLength")(
        test("UTF-8 ASCII string returns correct byte count") {
          val body = Body.fromString("hello")
          assertTrue(body.knownContentLength == Some(5L))
        },
        test("UTF-8 multi-byte characters counted correctly") {
          val body = Body.fromString("日本語")
          assertTrue(body.knownContentLength == Some(9L))
        },
        test("UTF-8 mixed ASCII and multi-byte") {
          val body = Body.fromString("hello日本")
          assertTrue(body.knownContentLength == Some(11L))
        },
        test("ISO-8859-1 returns data.length") {
          val body = Body.fromString("hello", java.nio.charset.StandardCharsets.ISO_8859_1)
          assertTrue(body.knownContentLength == Some(5L))
        },
        test("fallback charset returns correct byte count") {
          val body     = Body.fromString("test", java.nio.charset.StandardCharsets.UTF_16)
          val expected = "test".getBytes(java.nio.charset.StandardCharsets.UTF_16).length.toLong
          assertTrue(body.knownContentLength == Some(expected))
        },
        test("UTF-8 unpaired surrogate counted as replacement character") {
          val data     = "a\uD800b"
          val body     = Body.fromString(data)
          val expected = data.getBytes(java.nio.charset.StandardCharsets.UTF_8).length.toLong
          assertTrue(body.knownContentLength == Some(expected))
        },
      ),
      suite("materializedContent")(
        test("EmptyBody returns Some(Chunk.empty)") {
          val body = Body.empty
          assertTrue(body.materializedContent == Some(Chunk.empty))
        },
        test("ArrayBody returns Some with chunk") {
          val data = "hello".getBytes(Charsets.Http)
          val body = Body.fromArray(data)
          assertTrue(body.materializedContent == Some(Chunk.fromArray(data)))
        },
        test("ChunkBody returns Some with chunk") {
          val chunk = Chunk.fromArray("hello".getBytes(Charsets.Http))
          val body  = Body.fromChunk(chunk)
          assertTrue(body.materializedContent == Some(chunk))
        },
        test("StringBody returns Some with encoded bytes") {
          val text     = "hello"
          val body     = Body.fromString(text)
          val expected = Chunk.fromArray(text.getBytes(Charsets.Http))
          assertTrue(body.materializedContent == Some(expected))
        },
        test("FileBody returns None") {
          lazy val file = testFile
          val body      = Body.fromFile(file)
          for {
            b <- body
          } yield assertTrue(b.materializedContent == None)
        },
        test("StreamBody returns None") {
          val stream = ZStream.fromIterable("hello".getBytes(Charsets.Http))
          val body   = Body.fromStreamChunked(stream)
          assertTrue(body.materializedContent == None)
        },
      ),
      suite("materializedAsString")(
        test("EmptyBody returns Some empty string") {
          val body = Body.empty
          assertTrue(body.materializedAsString == Some(""))
        },
        test("StringBody returns Some with string") {
          val text = "hello world"
          val body = Body.fromString(text)
          assertTrue(body.materializedAsString == Some(text))
        },
        test("ArrayBody returns Some with decoded string") {
          val text = "test data"
          val body = Body.fromArray(text.getBytes(Charsets.Http))
          assertTrue(body.materializedAsString == Some(text))
        },
        test("ChunkBody returns Some with decoded string") {
          val text  = "chunk data"
          val chunk = Chunk.fromArray(text.getBytes(Charsets.Http))
          val body  = Body.fromChunk(chunk)
          assertTrue(body.materializedAsString == Some(text))
        },
        test("StreamBody returns None") {
          val stream = ZStream.fromIterable("hello".getBytes(Charsets.Http))
          val body   = Body.fromStreamChunked(stream)
          assertTrue(body.materializedAsString == None)
        },
        test("respects charset in contentType") {
          val text    = "test"
          val charset = java.nio.charset.StandardCharsets.UTF_16
          val body    = Body.fromString(text, charset)
          assertTrue(body.materializedAsString == Some(text))
        },
      ),
      suite("asForm schema-based form decoding")(
        test("asForm decodes URL-encoded form body to case class") {
          val body = Body
            .fromString("name=John&age=30")
            .contentType(MediaType.application.`x-www-form-urlencoded`)
          body.asForm[Person].map(person => assertTrue(person == Person("John", 30)))
        },
      ),
    ) @@ timeout(10 seconds)
}
