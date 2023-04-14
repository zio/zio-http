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

import zio._
import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test.TestAspect.{timeout, withLiveClock}
import zio.test._

import zio.http.internal.{DynamicServer, HttpRunnableSpec, severTestLayer}

object ContentTypeSpec extends HttpRunnableSpec {

  val contentSpec = suite("Content type header on file response")(
    test("mp4") {
      val res = Http.fromResource("TestFile2.mp4").deploy.contentType.run()
      assertZIO(res)(isSome(equalTo(Header.ContentType(MediaType.video.`mp4`))))
    },
    test("js") {
      val res = Http.fromResource("TestFile3.js").deploy.contentType.run()
      assertZIO(res)(isSome(equalTo(Header.ContentType(MediaType.application.`javascript`))))
    },
    test("no extension") {
      val res = Http.fromResource("TestFile4").deploy.contentType.run()
      assertZIO(res)(isNone)
    },
    test("css") {
      val res = Http.fromResource("TestFile5.css").deploy.contentType.run()
      assertZIO(res)(isSome(equalTo(Header.ContentType(MediaType.text.`css`))))
    },
    test("mp3") {
      val res = Http.fromResource("TestFile6.mp3").deploy.contentType.run()
      assertZIO(res)(isSome(equalTo(Header.ContentType(MediaType.audio.`mpeg`))))
    },
    test("unidentified extension") {
      val res = Http.fromResource("truststore.jks").deploy.contentType.run()
      assertZIO(res)(isNone)
    },
    test("already set content-type") {
      val expected = MediaType.application.`json`
      val res      =
        Http.fromResource("TestFile6.mp3").map(_.withHeader(Header.ContentType(expected))).deploy.contentType.run()
      assertZIO(res)(isSome(equalTo(Header.ContentType(expected))))
    },
  )

  override def spec = {
    suite("Content-type") {
      serve(DynamicServer.app).as(List(contentSpec))
    }.provideShared(DynamicServer.live, severTestLayer, Client.default, Scope.default) @@ timeout(
      5 seconds,
    ) @@ withLiveClock
  }
}
