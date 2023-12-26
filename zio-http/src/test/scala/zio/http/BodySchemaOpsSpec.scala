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

import zio.Chunk
import zio.test._

import zio.stream.ZStream

import zio.schema._
import zio.schema.codec.JsonCodec._

object BodySchemaOpsSpec extends ZIOHttpSpec {

  case class Person(name: String, age: Int)
  implicit val schema: Schema[Person]        = DeriveSchema.gen[Person]
  val person: Person                         = Person("John", 42)
  val person2: Person                        = Person("Jane", 43)
  val persons: ZStream[Any, Nothing, Person] = ZStream(person, person2)

  def spec = suite("Body schema ops")(
    test("Body.from") {
      val body     = Body.from(person)
      val expected = """{"name":"John","age":42}"""
      body.asString.map(s => assertTrue(s == expected))
    },
    test("Body.fromStream") {
      val body     = Body.fromStream(persons)
      val expected = """{"name":"John","age":42}{"name":"Jane","age":43}"""
      body.asString.map(s => assertTrue(s == expected))
    },
    test("Body#as") {
      val body = Body.fromString("""{"name":"John","age":42}""")
      body.as[Person].map(p => assertTrue(p == person))
    },
  )
}
