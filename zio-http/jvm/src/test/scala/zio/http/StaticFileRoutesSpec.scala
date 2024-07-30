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

import java.nio.file.{Files, Path => NIOPath}

import zio._
import zio.test.Assertion._
import zio.test.TestAspect.{sequential, withLiveClock}
import zio.test.{TestAspect, assert, assertZIO}

import zio.http.internal.{DynamicServer, HttpRunnableSpec, serverTestLayer}

object StaticFileRoutesSpec extends HttpRunnableSpec {

  private val createTempFile                    = ZIO.attempt(Files.createTempFile("", ".jpg"))
  private def deleteTempFile(tempPath: NIOPath) = ZIO.attempt(Files.deleteIfExists(tempPath)).ignore
  private val createAndDeleteTempFile           = createTempFile.flatMap(f => deleteTempFile(f).as(f))

  override def spec = suite("StaticFileRoutesSpec") {
    serve.as(List(staticSpec))
  }
    .provideSome[DynamicServer & Server & Client](Scope.default)
    .provideShared(DynamicServer.live, serverTestLayer, Client.default) @@ withLiveClock @@ sequential

  private def staticSpec = suite("Static RandomAccessFile Server")(
    suite("serveDirectory")(
      test("serve an existing file") {
        ZIO.acquireRelease(createTempFile)(deleteTempFile) flatMap { tempPath =>
          val tempDir  = tempPath.getParent.toFile
          val tempFile = tempPath.getFileName.toString
          val path     = Path.empty / "assets"
          val routes   = Routes.serveDirectory(path, tempDir).sandbox.deploy
          val request  = Request.get(URL(path / tempFile))
          routes(request)
        } map { response =>
          assert(response.status)(equalTo(Status.Ok)) &&
          assert(response.header(Header.ContentLength))(isSome(equalTo(Header.ContentLength(0L)))) &&
          assert(response.header(Header.ContentType))(isSome(equalTo(Header.ContentType(MediaType.image.`jpeg`))))
        }
      },
      test("serve a non-existing file") {
        createAndDeleteTempFile.flatMap { tempPath =>
          val tempDir  = tempPath.getParent.toFile
          val tempFile = tempPath.getFileName.toString
          val path     = Path.empty / "assets"
          val routes   = Routes.serveDirectory(path, tempDir).sandbox.deploy
          val request  = Request.get(URL(path / tempFile))
          assertZIO(routes(request).map(_.status))(equalTo(Status.NotFound))
        }
      },
    ),
    suite("serveResources")(
      test("serve an existing resource") {
        val existing = "TestFile1.txt"
        val path     = Path.root / "assets"
        val routes   = Routes.serveResources(path, "TestStatic").sandbox.deploy
        val request  = Request.get(URL(path / existing))
        for {
          response <- routes(request)
          body     <- response.body.asString
        } yield {
          assert(response.status)(equalTo(Status.Ok)) &&
          assert(response.header(Header.ContentLength))(isSome(equalTo(Header.ContentLength(50L)))) &&
          assert(body)(equalTo("This file is added for testing Static File Server.")) &&
          assert(response.header(Header.ContentType))(
            isSome(equalTo(Header.ContentType(MediaType.text.plain, charset = Some(Charsets.Utf8)))),
          )
        }
      },
      test("serve a non-existing resource") {
        val nonExisting = "Nothing.txt"
        val path        = Path.root / "assets"
        val routes      = Routes.serveResources(path, "TestStatic").sandbox.deploy
        val request     = Request.get(URL(path / nonExisting))
        assertZIO(routes(request).map(_.status))(equalTo(Status.NotFound))
      },
      test("insecurely serve a resource from \".\"") {
        val existing = "TestFile.txt"
        val path     = Path.root / "assets"
        val routes   = Routes.serveResources(path, ".")
        val request  = Request.get(URL(path / existing))
        assertZIO(routes(request).map(_.status))(equalTo(Status.InternalServerError))
      },
    ),
  ) @@ TestAspect.blocking
}
