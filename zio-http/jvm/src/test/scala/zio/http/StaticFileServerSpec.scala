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
import zio.test.TestAspect.{mac, os, sequential, unix, withLiveClock}
import zio.test.{assertTrue, assertZIO}

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
    serve.as(List(staticSpec))
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
        } @@ os(o => o.isUnix || o.isMac),
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
        test("should respond with empty if not found") {
          val res = resourceNotFound.run().map(_.status)
          assertZIO(res)(equalTo(Status.NotFound))
        },
      ),
    ),
    suite("fromFileWithRange")(
      test("should return 206 Partial Content for valid Range request") {
        ZIO.blocking {
          for {
            tmpFile <- ZIO.succeed {
              val f      = File.createTempFile("range-test", ".txt")
              val writer = new java.io.FileWriter(f)
              writer.write("0123456789abcdefghij")
              writer.close()
              f.deleteOnExit()
              f
            }
            request = Request.get(url"/file").addHeader(Header.Range.Single("bytes", 0, Some(9)))
            handler = Handler.fromFileWithRange(ZIO.succeed(tmpFile), request).sandbox
            response <- handler.toRoutes.deploy.run()
            body     <- response.body.asString
          } yield assertTrue(
            response.status == Status.PartialContent,
            body == "0123456789",
            response.header(Header.ContentRange).isDefined,
            response.header(Header.AcceptRanges).isDefined,
          )
        }
      },
      test("should return 416 Range Not Satisfiable for invalid range") {
        ZIO.blocking {
          for {
            tmpFile <- ZIO.succeed {
              val f      = File.createTempFile("range-invalid", ".txt")
              val writer = new java.io.FileWriter(f)
              writer.write("short")
              writer.close()
              f.deleteOnExit()
              f
            }
            request = Request.get(url"/file").addHeader(Header.Range.Single("bytes", 100, Some(200)))
            handler = Handler.fromFileWithRange(ZIO.succeed(tmpFile), request).sandbox
            response <- handler.toRoutes.deploy.run()
          } yield assertTrue(
            response.status == Status.RequestedRangeNotSatisfiable,
            response.header(Header.ContentRange).isDefined,
          )
        }
      },
      test("should return Accept-Ranges header for full file requests") {
        ZIO.blocking {
          for {
            tmpFile <- ZIO.succeed {
              val f      = File.createTempFile("full-file", ".txt")
              val writer = new java.io.FileWriter(f)
              writer.write("content")
              writer.close()
              f.deleteOnExit()
              f
            }
            request = Request.get(url"/file")
            handler = Handler.fromFileWithRange(ZIO.succeed(tmpFile), request).sandbox
            response <- handler.toRoutes.deploy.run()
          } yield assertTrue(
            response.status == Status.Ok,
            response.header(Header.AcceptRanges).isDefined,
          )
        }
      },
      test("should handle suffix range request") {
        ZIO.blocking {
          for {
            tmpFile <- ZIO.succeed {
              val f      = File.createTempFile("suffix-test", ".txt")
              val writer = new java.io.FileWriter(f)
              writer.write("0123456789")
              writer.close()
              f.deleteOnExit()
              f
            }
            request = Request.get(url"/file").addHeader(Header.Range.Suffix("bytes", 5))
            handler = Handler.fromFileWithRange(ZIO.succeed(tmpFile), request).sandbox
            response <- handler.toRoutes.deploy.run()
            body     <- response.body.asString
          } yield assertTrue(
            response.status == Status.PartialContent,
            body == "56789",
          )
        }
      },
      test("should handle prefix range request") {
        ZIO.blocking {
          for {
            tmpFile <- ZIO.succeed {
              val f      = File.createTempFile("prefix-test", ".txt")
              val writer = new java.io.FileWriter(f)
              writer.write("0123456789")
              writer.close()
              f.deleteOnExit()
              f
            }
            request = Request.get(url"/file").addHeader(Header.Range.Prefix("bytes", 5))
            handler = Handler.fromFileWithRange(ZIO.succeed(tmpFile), request).sandbox
            response <- handler.toRoutes.deploy.run()
            body     <- response.body.asString
          } yield assertTrue(
            response.status == Status.PartialContent,
            body == "56789",
          )
        }
      },
    ),
  )

}
