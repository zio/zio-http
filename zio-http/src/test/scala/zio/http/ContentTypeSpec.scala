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
      val res =
        Handler.fromResource("TestFile2.mp4").sandbox.toHttpApp.deploy(Request()).map(_.header(Header.ContentType))
      assertZIO(res)(isSome(equalTo(Header.ContentType(MediaType.video.`mp4`))))
    },
    test("js") {
      val res =
        Handler.fromResource("TestFile3.js").sandbox.toHttpApp.deploy(Request()).map(_.header(Header.ContentType))
      assertZIO(res)(isSome(equalTo(Header.ContentType(MediaType.application.`javascript`))))
    },
    test("no extension") {
      val res = Handler.fromResource("TestFile4").sandbox.toHttpApp.deploy(Request()).map(_.header(Header.ContentType))
      assertZIO(res)(isNone)
    },
    test("css") {
      val res =
        Handler.fromResource("TestFile5.css").sandbox.toHttpApp.deploy(Request()).map(_.header(Header.ContentType))
      assertZIO(res)(isSome(equalTo(Header.ContentType(MediaType.text.`css`))))
    },
    test("mp3") {
      val res =
        Handler.fromResource("TestFile6.mp3").sandbox.toHttpApp.deploy(Request()).map(_.header(Header.ContentType))
      assertZIO(res)(isSome(equalTo(Header.ContentType(MediaType.audio.`mpeg`))))
    },
    test("unidentified extension") {
      val res =
        Handler.fromResource("truststore.jks").sandbox.toHttpApp.deploy(Request()).map(_.header(Header.ContentType))
      assertZIO(res)(isNone)
    },
    test("already set content-type") {
      val expected = MediaType.application.`json`
      val res      =
        Handler
          .fromResource("TestFile6.mp3")
          .map(_.addHeader(Header.ContentType(expected)))
          .sandbox
          .toHttpApp
          .deploy(Request())
          .map(
            _.header(Header.ContentType),
          )
      assertZIO(res)(isSome(equalTo(Header.ContentType(expected))))
    },
  )

  override def spec = {
    suite("Content-type") {
      serve.as(List(contentSpec))
    }.provideSomeShared[Scope](DynamicServer.live, severTestLayer, Client.default) @@ timeout(
      5 seconds,
    ) @@ withLiveClock
  }
}
