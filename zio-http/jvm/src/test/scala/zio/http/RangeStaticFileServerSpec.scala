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

import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path => JPath}

import zio._
import zio.test._

import zio.http.internal.{DynamicServer, RoutesRunnableSpec, serverTestLayer}

object RangeStaticFileServerSpec extends RoutesRunnableSpec {
  private val largeFileTestSupported =
    if (scala.util.Properties.isWin) TestAspect.ignore else TestAspect.identity

  override def spec =
    suite("RangeStaticFileServerSpec (Issue #709 Range + multipart/byteranges)") {
      serve.as(List(rangeSpec))
    }
      .provideSome[DynamicServer & Server & Server.Config & Client](Scope.default)
      .provideShared(
        DynamicServer.live,
        serverTestLayer,
        Client.default,
      ) @@ TestAspect.withLiveClock @@ TestAspect.sequential

  private def rangeSpec = suite("static file range")(
    test("single range -> 206 + Content-Range + correct bytes") {
      withTempAsciiFile { case (filePath, bytes) =>
        val app = Routes(
          Method.GET / "file" -> Handler.fromFile(filePath.toFile),
        ).sandbox.deploy

        val request = Request
          .get(URL(Path.root / "file"))
          .addHeader(Header.Custom("Range", "bytes=10-19"))

        for {
          response <- app(request)
          bodyStr  <- response.body.asString(StandardCharsets.US_ASCII)
        } yield assertTrue(
          response.status == Status.PartialContent,
          response.header(Header.AcceptRanges).contains(Header.AcceptRanges.Bytes),
          response.header(Header.ContentRange).contains(Header.ContentRange.EndTotal("bytes", 10, 19, bytes.length)),
          response.header(Header.ContentLength).contains(Header.ContentLength(10L)),
          bodyStr == new String(bytes.slice(10, 20), StandardCharsets.US_ASCII),
        )
      }
    },
    test("open-ended range -> 206 + end derived from total") {
      withTempAsciiFile { case (filePath, bytes) =>
        val app = Routes(
          Method.GET / "file" -> Handler.fromFile(filePath.toFile),
        ).sandbox.deploy

        val start   = 30
        val request = Request
          .get(URL(Path.root / "file"))
          .addHeader(Header.Custom("Range", s"bytes=$start-"))

        for {
          response <- app(request)
          bodyStr  <- response.body.asString(StandardCharsets.US_ASCII)
        } yield {
          val expectedLength = bytes.length - start
          assertTrue(
            response.status == Status.PartialContent,
            response
              .header(Header.ContentRange)
              .contains(
                Header.ContentRange.EndTotal("bytes", start, bytes.length - 1, bytes.length),
              ),
            response.header(Header.ContentLength).contains(Header.ContentLength(expectedLength.toLong)),
            bodyStr == new String(bytes.slice(start, bytes.length), StandardCharsets.US_ASCII),
          )
        }
      }
    },
    test("suffix range -> 206 + start derived from total") {
      withTempAsciiFile { case (filePath, bytes) =>
        val app = Routes(
          Method.GET / "file" -> Handler.fromFile(filePath.toFile),
        ).sandbox.deploy

        val suffixLen = 12
        val start     = bytes.length - suffixLen
        val request   = Request
          .get(URL(Path.root / "file"))
          .addHeader(Header.Custom("Range", s"bytes=-$suffixLen"))

        for {
          response <- app(request)
          bodyStr  <- response.body.asString(StandardCharsets.US_ASCII)
        } yield assertTrue(
          response.status == Status.PartialContent,
          response
            .header(Header.ContentRange)
            .contains(
              Header.ContentRange.EndTotal("bytes", start, bytes.length - 1, bytes.length),
            ),
          response.header(Header.ContentLength).contains(Header.ContentLength(suffixLen.toLong)),
          bodyStr == new String(bytes.slice(start, bytes.length), StandardCharsets.US_ASCII),
        )
      }
    },
    test("multi-range -> 206 multipart/byteranges with 2 correct parts") {
      withTempAsciiFile { case (filePath, bytes) =>
        val app = Routes(
          Method.GET / "file" -> Handler.fromFile(filePath.toFile),
        ).sandbox.deploy

        val request = Request
          .get(URL(Path.root / "file"))
          .addHeader(Header.Custom("Range", "bytes=0-9,20-29"))

        for {
          response <- app(request)
          bodyStr  <- response.body.asString(StandardCharsets.ISO_8859_1)
        } yield {
          val contentType = response.header(Header.ContentType)
          val boundaryOpt = contentType.flatMap(_.boundary).map(_.id)

          val boundary = boundaryOpt.getOrElse {
            throw new RuntimeException(
              "Expected multipart boundary in Content-Type, got: " + contentType.map(_.renderedValue),
            )
          }

          val parts = splitMultipart(bodyStr, boundary)

          val expected0  = new String(bytes.slice(0, 10), StandardCharsets.US_ASCII)
          val expected20 = new String(bytes.slice(20, 30), StandardCharsets.US_ASCII)

          assertTrue(
            response.status == Status.PartialContent,
            contentType.exists(_.mediaType == MediaType.multipart.byteranges),
            parts.length == 2,
            partHasHeader(parts(0), "content-range", "bytes 0-9/" + bytes.length.toString),
            partBody(parts(0)) == expected0,
            partHasHeader(parts(1), "content-range", "bytes 20-29/" + bytes.length.toString),
            partBody(parts(1)) == expected20,
            bodyStr.endsWith("--" + boundary + "--\r\n"),
          )
        }
      }
    },
    test("unsatisfiable range -> 416 + Content-Range: bytes */total") {
      withTempAsciiFile { case (filePath, bytes) =>
        val app = Routes(
          Method.GET / "file" -> Handler.fromFile(filePath.toFile),
        ).sandbox.deploy

        val start   = bytes.length * 10
        val request = Request
          .get(URL(Path.root / "file"))
          .addHeader(Header.Custom("Range", "bytes=" + start.toString + "-" + (start + 5).toString))

        for {
          response <- app(request)
        } yield assertTrue(
          response.status == Status.RequestedRangeNotSatisfiable,
          response.header(Header.ContentRange).contains(Header.ContentRange.RangeTotal("bytes", bytes.length)),
        )
      }
    },
    test("malformed Range header -> 200 and full body") {
      withTempAsciiFile { case (filePath, bytes) =>
        val app = Routes(
          Method.GET / "file" -> Handler.fromFile(filePath.toFile),
        ).sandbox.deploy

        val request = Request
          .get(URL(Path.root / "file"))
          .addHeader(Header.Custom("Range", "bytes=invalid-syntax"))

        for {
          response <- app(request)
          bodyStr  <- response.body.asString(StandardCharsets.US_ASCII)
        } yield assertTrue(
          response.status == Status.Ok,
          bodyStr == new String(bytes, StandardCharsets.US_ASCII),
        )
      }
    },
    test("no Range header -> 200 and full body") {
      withTempAsciiFile { case (filePath, bytes) =>
        val app = Routes(
          Method.GET / "file" -> Handler.fromFile(filePath.toFile),
        ).sandbox.deploy

        val request = Request.get(URL(Path.root / "file"))

        for {
          response <- app(request)
          bodyStr  <- response.body.asString(StandardCharsets.US_ASCII)
        } yield assertTrue(
          response.status == Status.Ok,
          bodyStr == new String(bytes, StandardCharsets.US_ASCII),
        )
      }
    },
    test("HEAD request -> 200 and no body but Content-Length present") {
      withTempAsciiFile { case (filePath, bytes) =>
        val app = Routes(
          Method.HEAD / "file" -> Handler.fromFile(filePath.toFile),
        ).sandbox.deploy

        val request = Request.head(URL(Path.root / "file"))

        for {
          response <- app(request)
          bodyStr  <- response.body.asString(StandardCharsets.US_ASCII)
        } yield assertTrue(
          response.status == Status.Ok,
          bodyStr.isEmpty,
          response.header(Header.ContentLength).contains(Header.ContentLength(bytes.length.toLong)),
          response.header(Header.AcceptRanges).contains(Header.AcceptRanges.Bytes),
        )
      }
    },
    test("HEAD + single range -> 206, Correct Headers, Empty Body") {
      withTempAsciiFile { case (filePath, bytes) =>
        val app = Routes(
          Method.HEAD / "file" -> Handler.fromFile(filePath.toFile),
        ).sandbox.deploy

        val request = Request.head(URL(Path.root / "file")).addHeader(Header.Custom("Range", "bytes=0-9"))

        for {
          response <- app(request)
          bodyStr  <- response.body.asString(StandardCharsets.US_ASCII)
        } yield assertTrue(
          response.status == Status.PartialContent,
          bodyStr.isEmpty,
          response.header(Header.ContentLength).contains(Header.ContentLength(10L)),
          response.header(Header.ContentRange).contains(Header.ContentRange.EndTotal("bytes", 0, 9, bytes.length)),
        )
      }
    },
    test("HEAD + multi-range -> 206 multipart, Correct Headers, Empty Body") {
      withTempAsciiFile { case (filePath, _) =>
        val app = Routes(
          Method.HEAD / "file" -> Handler.fromFile(filePath.toFile),
        ).sandbox.deploy

        val request = Request.head(URL(Path.root / "file")).addHeader(Header.Custom("Range", "bytes=0-9,20-29"))

        for {
          response <- app(request)
          bodyStr  <- response.body.asString(StandardCharsets.US_ASCII)
        } yield {
          val contentType = response.header(Header.ContentType)
          val totalLen    = response.header(Header.ContentLength).map(_.length).getOrElse(0L)
          assertTrue(
            response.status == Status.PartialContent,
            bodyStr.isEmpty,
            contentType.exists(_.mediaType == MediaType.multipart.byteranges),
            totalLen > 0L,
          )
        }
      }
    },
    test("HEAD + unsatisfiable range -> 416, Empty Body") {
      withTempAsciiFile { case (filePath, bytes) =>
        val app = Routes(
          Method.HEAD / "file" -> Handler.fromFile(filePath.toFile),
        ).sandbox.deploy

        val request = Request.head(URL(Path.root / "file")).addHeader(Header.Custom("Range", "bytes=10000-10005"))

        for {
          response <- app(request)
          bodyStr  <- response.body.asString(StandardCharsets.US_ASCII)
        } yield assertTrue(
          response.status == Status.RequestedRangeNotSatisfiable,
          bodyStr.isEmpty,
          response.header(Header.ContentRange).contains(Header.ContentRange.RangeTotal("bytes", bytes.length)),
        )
      }
    },
    test("If-Range ETag handling") {
      withTempAsciiFile { case (filePath, bytes) =>
        val app = Routes(
          Method.GET / "file" -> Handler.fromFile(filePath.toFile),
        ).sandbox.deploy

        val rangeHeader = Header.Custom("Range", "bytes=0-4") // First 5 bytes

        for {
          // 1. Get ETag
          resp1 <- app(Request.get(URL(Path.root / "file")))
          etagH        = resp1.header(Header.ETag).getOrElse(throw new RuntimeException("Missing ETag"))
          validatorStr = etagH match {
            case Header.ETag.Strong(v) => v
            case Header.ETag.Weak(v)   => v
          }

          // 2. Request with Matching If-Range -> 206
          // Note: Header.IfRange.ETag constructor implies it holds the value.
          // Since our logic compares raw string in IfRange with calculated strong etag,
          // passing the validator string directly should work if parsing is symmetric.
          req2 = Request
            .get(URL(Path.root / "file"))
            .addHeader(rangeHeader)
            .addHeader(Header.IfRange.ETag(validatorStr))

          resp2 <- app(req2)

          // 3. Request with Mismatching If-Range -> 200
          req3 = Request
            .get(URL(Path.root / "file"))
            .addHeader(rangeHeader)
            .addHeader(Header.IfRange.ETag("mismatch"))
          resp3 <- app(req3)

        } yield assertTrue(
          resp2.status == Status.PartialContent,
          resp3.status == Status.Ok,
          resp3.header(Header.ContentLength).map(_.length).contains(bytes.length.toLong),
        )
      }
    },
    test("Header.ContentRange parse unit tests") {
      assertTrue(
        Header.ContentRange.parse("bytes */100") == Right(Header.ContentRange.RangeTotal("bytes", 100)),
        Header.ContentRange.parse("bytes 0-9/*") == Right(Header.ContentRange.StartEnd("bytes", 0, 9)),
        Header.ContentRange.parse("bytes 0-9/100") == Right(Header.ContentRange.EndTotal("bytes", 0, 9, 100)),
      )
    },
    test("Large file (>2GB) Range handling") {
      // Just over 2GB is enough to validate we don't overflow `Int`.
      // Note: Windows temp filesystems may not support sparse extension by default.
      val size          = Int.MaxValue.toLong + 1024
      val createBigFile = ZIO.attempt {
        val tempFile = Files.createTempFile("large-test", ".dat")
        val raf      = new RandomAccessFile(tempFile.toFile, "rw")
        try {
          raf.setLength(size)
        } finally {
          raf.close()
        }
        tempFile
      }

      ZIO.acquireRelease(createBigFile)(f => ZIO.attempt(Files.delete(f)).ignore).flatMap { tempFile =>
        val app = Routes(
          Method.GET / "large" -> Handler.fromFile(tempFile.toFile),
        ).sandbox.deploy

        val start = size - 10
        val end   = size - 1
        val req   = Request
          .get(URL(Path.root / "large"))
          .addHeader(Header.Custom("Range", s"bytes=$start-$end"))

        for {
          resp <- app(req)
          headerOpt = resp.headers.get("Content-Range")
        } yield assertTrue(
          resp.status == Status.PartialContent,
          headerOpt.exists { h =>
            h match {
              case s"bytes $s-$e/$t" => s.toLong == start && e.toLong == end && t.toLong == size
              case _                 => false
            }
          },
        )
      }
    } @@ largeFileTestSupported,
  ) @@ TestAspect.blocking

  private def withTempAsciiFile[R](
    f: (JPath, Array[Byte]) => ZIO[R, Throwable, TestResult],
  ): ZIO[Scope & R, Throwable, TestResult] =
    ZIO.acquireRelease {
      ZIO.attempt {
        val dir  = Files.createTempDirectory("zio-http-range-test")
        val file = dir.resolve("range.txt")

        val content =
          (0 until 10).map { _ =>
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
          }.mkString

        val bytes = content.getBytes(StandardCharsets.US_ASCII)
        Files.write(file, bytes)
        (file, bytes)
      }
    } { case (file, _) =>
      ZIO.attempt {
        Files.deleteIfExists(file)
        Files.deleteIfExists(file.getParent)
      }.ignore
    }.flatMap(f.tupled)

  private def splitMultipart(body: String, boundary: String): List[String] = {
    val marker   = s"--$boundary"
    val segments = body.split(marker, -1).toList.drop(1).map(stripLeadingNewlines)

    segments
      .takeWhile(s => !s.startsWith("--"))
      .map(stripTrailingNewlines)
      .filter(_.nonEmpty)
  }

  private def partHasHeader(part: String, headerNameLower: String, expectedValue: String): Boolean =
    part.linesIterator
      .takeWhile(_.nonEmpty)
      .exists { line =>
        val idx = line.indexOf(':')
        if (idx < 0) false
        else {
          val name  = line.substring(0, idx).trim.toLowerCase
          val value = line.substring(idx + 1).trim
          name == headerNameLower && value.equalsIgnoreCase(expectedValue)
        }
      }

  private def partBody(part: String): String = {
    val idxCrlf = part.indexOf("\r\n\r\n")
    val idxLf   = part.indexOf("\n\n")

    val idx = if (idxCrlf >= 0) idxCrlf + 4 else if (idxLf >= 0) idxLf + 2 else -1

    if (idx < 0) ""
    else stripTrailingNewlines(part.substring(idx))
  }

  private def stripLeadingNewlines(s: String): String =
    s.stripPrefix("\r\n").stripPrefix("\n")

  private def stripTrailingNewlines(s: String): String =
    s.stripSuffix("\r\n").stripSuffix("\n")
}
