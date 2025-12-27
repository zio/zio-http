package zio.http

import zio._
import zio.test._
import java.nio.file.{Files, Path}

object FileRangeSupportSpec extends ZIOSpecDefault {
  
  override def spec = suite("FileRangeSupport")(
    test("serve file without range returns 200 OK") {
      for {
        tempFile <- createTempFile("Hello ZIO HTTP Range Support!")
        request = Request.get("/test")
        response = FileRangeSupport.serveFile(tempFile.toFile, request, None)
        _ <- ZIO.succeed(Files.delete(tempFile))
      } yield assertTrue(
        response.status == Status.Ok
      )
    },
    
    test("serve file with single range returns 206 Partial Content") {
      for {
        tempFile <- createTempFile("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        request = Request.get("/test").addHeader(Header.Range.Single("bytes", 0L, Some(9L)))
        response = FileRangeSupport.serveFile(tempFile.toFile, request, None)
        _ <- ZIO.succeed(Files.delete(tempFile))
      } yield assertTrue(
        response.status == Status.PartialContent
      )
    },
    
    test("serve file with suffix range returns 206") {
      for {
        tempFile <- createTempFile("0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ")
        request = Request.get("/test").addHeader(Header.Range.Suffix("bytes", 10L))
        response = FileRangeSupport.serveFile(tempFile.toFile, request, None)
        _ <- ZIO.succeed(Files.delete(tempFile))
      } yield assertTrue(
        response.status == Status.PartialContent
      )
    },
    
    test("unsatisfiable range returns 416 Range Not Satisfiable") {
      for {
        tempFile <- createTempFile("short")
        request = Request.get("/test").addHeader(Header.Range.Single("bytes", 1000L, Some(2000L)))
        response = FileRangeSupport.serveFile(tempFile.toFile, request, None)
        _ <- ZIO.succeed(Files.delete(tempFile))
      } yield assertTrue(
        response.status == Status.RangeNotSatisfiable
      )
    },
    
    test("Accept-Ranges header is present in complete file response") {
      for {
        tempFile <- createTempFile("test content")
        request = Request.get("/test")
        response = FileRangeSupport.serveFile(tempFile.toFile, request, None)
        hasAcceptRanges = response.headers.get(Header.AcceptRanges).isDefined
        _ <- ZIO.succeed(Files.delete(tempFile))
      } yield assertTrue(hasAcceptRanges)
    }
  )
  
  private def createTempFile(content: String): UIO[Path] =
    ZIO.succeed {
      val temp = Files.createTempFile("zio-http-range-test", ".txt")
      Files.write(temp, content.getBytes("UTF-8"))
      temp
    }
}
