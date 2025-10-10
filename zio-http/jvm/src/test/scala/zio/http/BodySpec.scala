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
import zio.{Scope, durationInt}

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
    ) @@ timeout(10 seconds)
}
