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

import zio._
import zio.test.Assertion._
import zio.test.TestAspect.{timeout, unix, withLiveClock}
import zio.test.assertZIO

import zio.http.internal.{DynamicServer, HttpRunnableSpec, severTestLayer}

object StaticFileServerSpec extends HttpRunnableSpec {

  private val fileOk       = Http.fromResource("TestFile.txt").deploy
  private val fileNotFound = Http.fromResource("Nothing").deploy

  private val testArchivePath  = getClass.getResource("/TestArchive.jar").getPath
  private val resourceOk       =
    Http.fromResourceWithURL(new java.net.URL(s"jar:file:$testArchivePath!/TestFile.txt")).deploy
  private val resourceNotFound =
    Http.fromResourceWithURL(new java.net.URL(s"jar:file:$testArchivePath!/NonExistent.txt")).deploy

  override def spec = suite("StaticFileServer") {
    ZIO.scoped(serve.as(List(staticSpec)))
  }.provideShared(DynamicServer.live, severTestLayer, Client.default, Scope.default) @@
    timeout(5 seconds) @@ withLiveClock

  private def staticSpec = suite("Static RandomAccessFile Server")(
    suite("fromResource")(
      suite("file")(
        test("should have 200 status code") {
          val res = fileOk.run().map(_.status)
          assertZIO(res)(equalTo(Status.Ok))
        },
        test("should have content-length") {
          val res = fileOk.run().map(_.header(Header.ContentLength))
          assertZIO(res)(isSome(equalTo(Header.ContentLength(7L))))
        },
        test("should have content") {
          val res = fileOk.run().flatMap(_.body.asString)
          assertZIO(res)(equalTo("foo\nbar"))
        },
        test("should have content-type") {
          val res = fileOk.run().map(_.header(Header.ContentType))
          assertZIO(res)(isSome(equalTo(Header.ContentType(MediaType.text.plain))))
        },
        test("should respond with empty if file not found") {
          val res = fileNotFound.run().map(_.status)
          assertZIO(res)(equalTo(Status.NotFound))
        },
      ),
    ),
    suite("fromFile")(
      suite("failure on construction")(
        test("should respond with 500") {
          val res = Http.fromFile(throw new Error("Wut happened?")).deploy.run().map(_.status)
          assertZIO(res)(equalTo(Status.InternalServerError))
        },
      ),
      suite("unreadable file")(
        test("should respond with 500") {
          val tmpFile = File.createTempFile("test", "txt")
          tmpFile.setReadable(false)
          val res     = Http.fromFile(tmpFile).deploy.run().map(_.status)
          assertZIO(res)(equalTo(Status.InternalServerError))
        } @@ unix,
      ),
      suite("invalid file")(
        test("should respond with 500") {
          final class BadFile(name: String) extends File(name) {
            override def length: Long    = throw new Error("Haha")
            override def isFile: Boolean = true
          }
          val res = Http.fromFile(new BadFile("Length Failure")).deploy.run().map(_.status)
          assertZIO(res)(equalTo(Status.InternalServerError))
        },
      ),
    ),
    suite("fromResourceWithURL")(
      suite("with 'jar' protocol")(
        test("should have 200 status code") {
          val res = resourceOk.run().map(_.status)
          assertZIO(res)(equalTo(Status.Ok))
        },
        test("should have content-length") {
          val res = resourceOk.run().map(_.header(Header.ContentLength))
          assertZIO(res)(isSome(equalTo(Header.ContentLength(7L))))
        },
        test("should have content") {
          val res = resourceOk.run().flatMap(_.body.asString)
          assertZIO(res)(equalTo("foo\nbar"))
        },
        test("should have content-type") {
          val res = resourceOk.run().map(_.header(Header.ContentType))
          assertZIO(res)(isSome(equalTo(Header.ContentType(MediaType.text.plain))))
        },
        test("should respond with empty if not found") {
          val res = resourceNotFound.run().map(_.status)
          assertZIO(res)(equalTo(Status.NotFound))
        },
      ),
    ),
  )

}
