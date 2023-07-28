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

import java.io.File

import zio.test.Assertion.equalTo
import zio.test.TestAspect.timeout
import zio.test._
import zio.{Scope, durationInt}
import zio.stream.ZStream

object BodySpec extends ZIOHttpSpec {
  private val testFile = new File(getClass.getResource("/TestFile.txt").getPath)

    override def spec: Spec[TestEnvironment with Scope, Throwable] =
      suite("BodySpec")(
        suite("outgoing")(
          suite("encode")(
            suite("fromStream")(
              test("success") {
                check(Gen.string) { payload =>
                  val stringBuffer    = payload.getBytes(Charsets.Http)
                  val responseContent = ZStream.fromIterable(stringBuffer, chunkSize = 2)
                  val res             = Body.fromStream(responseContent).asString(Charsets.Http)
                  assertZIO(res)(equalTo(payload))
                }
              },
            ),
            suite("fromFile")(
              test("success") {
                lazy val file = testFile
                val res       = Body.fromFile(file).asString(Charsets.Http)
                assertZIO(res)(equalTo("foo\nbar"))
              },
              test("success small chunk") {
                lazy val file = testFile
                val res       = Body.fromFile(file, 3).asString(Charsets.Http)
                assertZIO(res)(equalTo("foo\nbar"))
              },
            ),
          ),
        ),
      ) @@ timeout(10 seconds)
  }
}

package public.api {
  import zio.http._
  import zio.Chunk
  object BodyPublicSpec extends ZIOSpecDefault {
    private val testFile = new File(getClass.getResource("/TestFile.txt").getPath)

    def spec: Spec[TestEnvironment with Scope, Any] =
      suite("BodyPublicSpec")(
        suite("fromFile")(
          test("returns the hinted mediaType when one is provided") {
            lazy val file = testFile
            val body      = Body.fromFile(file).mediaType(MediaType.text.plain)
            assertTrue(body.mediaType == Option(MediaType.text.plain))
          },
        ),
        suite("fromChunk")(
          test("returns the hinted mediaType when one is provided") {
            val body = Body.fromChunk(Chunk.fromArray("test".getBytes())).mediaType(MediaType.text.plain)
            assertTrue(body.mediaType == Option(MediaType.text.plain))
          },
        ),
        suite("fromStream")(
          test("returns the hinted mediaType when one is provided") {
            val body = Body.fromCharSequenceStream(ZStream.succeed("test")).mediaType(MediaType.text.plain)
            assertTrue(body.mediaType == Option(MediaType.text.plain))
          },
        ),
        suite("fromString")(
          test("returns the hinted mediaType when one is provided") {
            val body = Body.fromString("test").mediaType(MediaType.text.plain)
            assertTrue(body.mediaType == Option(MediaType.text.plain))
          },
        ),
        suite("fromCharSequence")(
          test("returns the hinted mediaType when one is provided") {
            val body = Body.fromCharSequence("test").mediaType(MediaType.text.plain)
            assertTrue(body.mediaType == Option(MediaType.text.plain))
          },
        ),
      )
  }
}
