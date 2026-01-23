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
import java.nio.file.Files

import zio._
import zio.test.Assertion._
import zio.test.TestAspect.{sequential, withLiveClock}
import zio.test._

import zio.http.internal.{DynamicServer, RoutesRunnableSpec, serverTestLayer}

object StaticFileServerSpec extends RoutesRunnableSpec {

  private val fileOk       = Handler.fromResource("TestFile.txt").sandbox.toRoutes.deploy
  private val fileNotFound = Handler.fromResource("Nothing").sandbox.toRoutes.deploy

  private val testArchivePath  = getClass.getResource("/TestArchive.jar").getPath
  private val resourceOk       =
    Handler
      .fromResourceWithURL(new java.net.URI(s"jar:file:$testArchivePath!/TestFile.txt").toURL, Charsets.Utf8)
      .sandbox
      .toRoutes
      .deploy
  private val resourceNotFound =
    Handler
      .fromResourceWithURL(new java.net.URI(s"jar:file:$testArchivePath!/NonExistent.txt").toURL, Charsets.Utf8)
      .sandbox
      .toRoutes
      .deploy

  override def spec = suite("StaticFileServerSpec") {
    serve.as(List(staticSpec, rangeSpec))
  }.provideShared(Scope.default, DynamicServer.live, serverTestLayer, Client.default) @@ withLiveClock @@ sequential

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
          assertZIO(res)(isSome(equalTo(Header.ContentType(MediaType.text.plain, charset = Some(Charsets.Utf8)))))
        },
        test("should have Accept-Ranges header") {
          val res = fileOk.run().map(_.header(Header.AcceptRanges))
          assertZIO(res)(isSome(equalTo(Header.AcceptRanges.Bytes)))
        },
        test("should have ETag header") {
          val res = fileOk.run().map(_.header(Header.ETag))
          assertZIO(res)(isSome)
        },
        test("should have Last-Modified header") {
          val res = fileOk.run().map(_.header(Header.LastModified))
          assertZIO(res)(isSome)
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
          val res = Handler.fromFile(throw new Error("Wut happened?")).sandbox.toRoutes.deploy.run().map(_.status)
          assertZIO(res)(equalTo(Status.InternalServerError))
        },
      ),
      suite("unreadable file")(
        test("should respond with 500") {
          ZIO.blocking {
            val tmpFile = File.createTempFile("test", "txt")
            tmpFile.setReadable(false)
            val res     = Handler.fromFile(tmpFile).sandbox.toRoutes.deploy.run().map(_.status)
            assertZIO(res)(equalTo(Status.Forbidden))
          }
        } @@ TestAspect.os(o => o.isUnix || o.isMac),
      ),
      suite("invalid file")(
        test("should respond with 500") {
          final class BadFile(name: String) extends File(name) {
            override def exists(): Boolean = throw new Error("Haha")
          }
          val res = Handler.fromFile(new BadFile("Length Failure")).sandbox.toRoutes.deploy.run().map(_.status)
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
          assertZIO(res)(isSome(equalTo(Header.ContentType(MediaType.text.plain, charset = Some(Charsets.Utf8)))))
        },
        test("should have Accept-Ranges: none for JAR resources") {
          val res = resourceOk.run().map(_.header(Header.AcceptRanges))
          assertZIO(res)(isSome(equalTo(Header.AcceptRanges.None)))
        },
        test("should respond with empty if not found") {
          val res = resourceNotFound.run().map(_.status)
          assertZIO(res)(equalTo(Status.NotFound))
        },
      ),
    ),
  )

  private def rangeSpec = suite("HTTP Range Request Support")(
    suite("Single Range")(
      test("returns 206 Partial Content for valid range") {
        for {
          tmpFile <- createTestFile("0123456789abcdefghijklmnopqrstuvwxyz") // 36 bytes
          handler = Handler.fromFile(tmpFile).sandbox.toRoutes.deploy
          res  <- handler.runZIO(Request.get(URL.root).addHeader(Header.Range.Single("bytes", 0, Some(9))))
          body <- res.body.asString
        } yield assertTrue(
          res.status == Status.PartialContent,
          body == "0123456789",
          res.header(Header.ContentRange).contains(Header.ContentRange.EndTotal("bytes", 0, 9, 36)),
          res.header(Header.AcceptRanges).contains(Header.AcceptRanges.Bytes),
        )
      },
      test("returns correct bytes for middle range") {
        for {
          tmpFile <- createTestFile("0123456789abcdefghijklmnopqrstuvwxyz")
          handler = Handler.fromFile(tmpFile).sandbox.toRoutes.deploy
          res  <- handler.runZIO(Request.get(URL.root).addHeader(Header.Range.Single("bytes", 10, Some(19))))
          body <- res.body.asString
        } yield assertTrue(
          res.status == Status.PartialContent,
          body == "abcdefghij",
          res.header(Header.ContentRange).contains(Header.ContentRange.EndTotal("bytes", 10, 19, 36)),
        )
      },
      test("clamps end to file size when end exceeds file size") {
        for {
          tmpFile <- createTestFile("0123456789")
          handler = Handler.fromFile(tmpFile).sandbox.toRoutes.deploy
          res  <- handler.runZIO(Request.get(URL.root).addHeader(Header.Range.Single("bytes", 5, Some(100))))
          body <- res.body.asString
        } yield assertTrue(
          res.status == Status.PartialContent,
          body == "56789",
          res.header(Header.ContentRange).contains(Header.ContentRange.EndTotal("bytes", 5, 9, 10)),
        )
      },
    ),
    suite("Suffix Range")(
      test("returns last N bytes for suffix range") {
        for {
          tmpFile <- createTestFile("0123456789abcdefghijklmnopqrstuvwxyz")
          handler = Handler.fromFile(tmpFile).sandbox.toRoutes.deploy
          res  <- handler.runZIO(Request.get(URL.root).addHeader(Header.Range.Suffix("bytes", 5)))
          body <- res.body.asString
        } yield assertTrue(
          res.status == Status.PartialContent,
          body == "vwxyz",
          res.header(Header.ContentRange).contains(Header.ContentRange.EndTotal("bytes", 31, 35, 36)),
        )
      },
      test("returns entire file if suffix exceeds file size") {
        for {
          tmpFile <- createTestFile("short")
          handler = Handler.fromFile(tmpFile).sandbox.toRoutes.deploy
          res  <- handler.runZIO(Request.get(URL.root).addHeader(Header.Range.Suffix("bytes", 100)))
          body <- res.body.asString
        } yield assertTrue(
          res.status == Status.PartialContent,
          body == "short",
        )
      },
    ),
    suite("Prefix Range")(
      test("returns from offset to end for prefix range") {
        for {
          tmpFile <- createTestFile("0123456789abcdefghijklmnopqrstuvwxyz")
          handler = Handler.fromFile(tmpFile).sandbox.toRoutes.deploy
          res  <- handler.runZIO(Request.get(URL.root).addHeader(Header.Range.Prefix("bytes", 30)))
          body <- res.body.asString
        } yield assertTrue(
          res.status == Status.PartialContent,
          body == "uvwxyz",
          res.header(Header.ContentRange).contains(Header.ContentRange.EndTotal("bytes", 30, 35, 36)),
        )
      },
    ),
    suite("Invalid Ranges")(
      test("returns 416 for range starting beyond file size") {
        for {
          tmpFile <- createTestFile("short")
          handler = Handler.fromFile(tmpFile).sandbox.toRoutes.deploy
          res <- handler.runZIO(Request.get(URL.root).addHeader(Header.Range.Single("bytes", 100, Some(200))))
        } yield assertTrue(
          res.status == Status.RequestedRangeNotSatisfiable,
          res.header(Header.ContentRange).contains(Header.ContentRange.RangeTotal("bytes", 5)),
        )
      },
      test("returns 416 for range with start > end") {
        for {
          tmpFile <- createTestFile("0123456789")
          handler = Handler.fromFile(tmpFile).sandbox.toRoutes.deploy
          res <- handler.runZIO(Request.get(URL.root).addHeader(Header.Range.Single("bytes", 8, Some(3))))
        } yield assertTrue(
          res.status == Status.RequestedRangeNotSatisfiable,
        )
      },
    ),
    suite("Multiple Ranges")(
      test("returns multipart/byteranges for multiple valid ranges") {
        for {
          tmpFile <- createTestFile("0123456789abcdefghijklmnopqrstuvwxyz")
          handler = Handler.fromFile(tmpFile).sandbox.toRoutes.deploy
          res  <- handler.runZIO(
            Request
              .get(URL.root)
              .addHeader(Header.Range.Multiple("bytes", List((0L, Some(4L)), (10L, Some(14L))))),
          )
          body <- res.body.asString
        } yield assertTrue(
          res.status == Status.PartialContent,
          res
            .header(Header.ContentType)
            .exists(ct => ct.mediaType == MediaType.multipart.`byteranges`),
          body.contains("01234"),
          body.contains("abcde"),
          body.contains("Content-Range: bytes 0-4/36"),
          body.contains("Content-Range: bytes 10-14/36"),
        )
      },
      test("filters out invalid ranges in multiple range request") {
        for {
          tmpFile <- createTestFile("0123456789")
          handler = Handler.fromFile(tmpFile).sandbox.toRoutes.deploy
          res  <- handler.runZIO(
            Request
              .get(URL.root)
              .addHeader(Header.Range.Multiple("bytes", List((0L, Some(4L)), (100L, Some(200L))))),
          )
          body <- res.body.asString
        } yield assertTrue(
          res.status == Status.PartialContent,
          body.contains("01234"),
          body.contains("Content-Range: bytes 0-4/10"),
        )
      },
      test("returns 416 when all ranges are invalid") {
        for {
          tmpFile <- createTestFile("short")
          handler = Handler.fromFile(tmpFile).sandbox.toRoutes.deploy
          res <- handler.runZIO(
            Request
              .get(URL.root)
              .addHeader(Header.Range.Multiple("bytes", List((100L, Some(200L)), (300L, Some(400L))))),
          )
        } yield assertTrue(
          res.status == Status.RequestedRangeNotSatisfiable,
        )
      },
    ),
    suite("If-Range Conditional")(
      test("processes range when If-Range ETag matches") {
        for {
          tmpFile <- createTestFile("0123456789abcdefghijklmnopqrstuvwxyz")
          handler = Handler.fromFile(tmpFile).sandbox.toRoutes.deploy
          // First get the ETag
          fullRes <- handler.runZIO(Request.get(URL.root))
          etag      = fullRes.header(Header.ETag).get
          etagValue = etag match {
            case Header.ETag.Strong(v) => v
            case Header.ETag.Weak(v)   => v
          }
          // Then make range request with matching If-Range
          res <- handler.runZIO(
            Request
              .get(URL.root)
              .addHeader(Header.Range.Single("bytes", 0, Some(9)))
              .addHeader(Header.IfRange.ETag(etagValue)),
          )
          body <- res.body.asString
        } yield assertTrue(
          res.status == Status.PartialContent,
          body == "0123456789",
        )
      },
      test("returns full content when If-Range ETag does not match") {
        for {
          tmpFile <- createTestFile("0123456789abcdefghijklmnopqrstuvwxyz")
          handler = Handler.fromFile(tmpFile).sandbox.toRoutes.deploy
          res  <- handler.runZIO(
            Request
              .get(URL.root)
              .addHeader(Header.Range.Single("bytes", 0, Some(9)))
              .addHeader(Header.IfRange.ETag("non-matching-etag")),
          )
          body <- res.body.asString
        } yield assertTrue(
          res.status == Status.Ok,
          body == "0123456789abcdefghijklmnopqrstuvwxyz",
        )
      },
    ),
    suite("Non-bytes Unit")(
      test("ignores Range header with non-bytes unit") {
        for {
          tmpFile <- createTestFile("0123456789")
          handler = Handler.fromFile(tmpFile).sandbox.toRoutes.deploy
          res  <- handler.runZIO(Request.get(URL.root).addHeader(Header.Range.Single("items", 0, Some(5))))
          body <- res.body.asString
        } yield assertTrue(
          res.status == Status.Ok,
          body == "0123456789",
        )
      },
    ),
    suite("ETag and Last-Modified")(
      test("includes ETag header in response") {
        for {
          tmpFile <- createTestFile("test content")
          handler = Handler.fromFile(tmpFile).sandbox.toRoutes.deploy
          res <- handler.runZIO(Request.get(URL.root))
        } yield assertTrue(
          res.header(Header.ETag).isDefined,
        )
      },
      test("includes Last-Modified header in response") {
        for {
          tmpFile <- createTestFile("test content")
          handler = Handler.fromFile(tmpFile).sandbox.toRoutes.deploy
          res <- handler.runZIO(Request.get(URL.root))
        } yield assertTrue(
          res.header(Header.LastModified).isDefined,
        )
      },
    ),
  )

  private def createTestFile(content: String): ZIO[Scope, Throwable, File] =
    ZIO.acquireRelease(
      ZIO.attemptBlocking {
        val tmpFile = File.createTempFile("range-test-", ".txt")
        Files.write(tmpFile.toPath, content.getBytes(Charsets.Utf8))
        tmpFile
      },
    )(file => ZIO.attemptBlocking(file.delete()).ignoreLogged)

}
