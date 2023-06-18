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

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets._

import zio.Chunk
import zio.test.Assertion._
import zio.test._

object GetBodyAsStringSpec extends ZIOSpecDefault {

  def spec = suite("getBodyAsString") {
    val charsetGen: Gen[Any, Charset] =
      Gen.fromIterable(List(UTF_8, UTF_16, UTF_16BE, UTF_16LE, US_ASCII, ISO_8859_1))

    suite("binary chunk")(
      test("should map bytes according to charset given") {

        check(charsetGen) { charset =>
          val request = Request
            .post(
              URL(Root),
              Body.fromChunk(Chunk.fromArray("abc".getBytes(charset))),
            )
            .addHeader(Header.ContentType(MediaType.text.html, charset = Some(charset)))

          val encoded  =
            request.body.asString(request.header(Header.ContentType).flatMap(_.charset).getOrElse(Charsets.Http))
          val expected = new String(Chunk.fromArray("abc".getBytes(charset)).toArray, charset)
          assertZIO(encoded)(equalTo(expected))
        }
      },
      test("should map bytes to default utf-8 if no charset given") {
        val request  = Request.post(URL(Root), Body.fromChunk(Chunk.fromArray("abc".getBytes())))
        val encoded  = request.body.asString
        val expected = new String(Chunk.fromArray("abc".getBytes()).toArray, Charsets.Http)
        assertZIO(encoded)(equalTo(expected))
      },
    )
  }
}
