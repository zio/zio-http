package zio.http.service

import zio.http._
import zio.http.internal.{DynamicServer, HttpRunnableSpec, severTestLayer}
import zio.http.model._
import zio.test.Assertion.{equalTo, isSome}
import zio.test.TestAspect.timeout
import zio.test.assertZIO
import zio.{Scope, ZIO, durationInt}

import java.io.File

object StaticFileServerSpec extends HttpRunnableSpec {

  private val fileOk       = Http.fromResource("TestFile.txt").deploy
  private val fileNotFound = Http.fromResource("Nothing").deploy

  private val testArchivePath  = getClass.getResource("/TestArchive.jar").getPath
  private val resourceOk       =
    Http.fromResourceWithURL(new java.net.URL(s"jar:file:$testArchivePath!/TestFile.txt")).deploy
  private val resourceNotFound =
    Http.fromResourceWithURL(new java.net.URL(s"jar:file:$testArchivePath!/NonExistent.txt")).deploy

  override def spec = suite("StaticFileServer") {
    ZIO.scoped(serve(DynamicServer.app).as(List(staticSpec)))
  }.provideShared(DynamicServer.live, severTestLayer, Client.default, Scope.default) @@
    timeout(5 seconds)

  private def staticSpec = suite("Static RandomAccessFile Server")(
    suite("fromResource")(
      suite("file")(
        test("should have 200 status code") {
          val res = fileOk.run().map(_.status)
          assertZIO(res)(equalTo(Status.Ok))
        },
        test("should have content-length") {
          val res = fileOk.run().map(_.contentLength)
          assertZIO(res)(isSome(equalTo(7L)))
        },
        test("should have content") {
          val res = fileOk.run().flatMap(_.body.asString)
          assertZIO(res)(equalTo("foo\nbar"))
        },
        test("should have content-type") {
          val res = fileOk.run().map(_.mediaType)
          assertZIO(res)(isSome(equalTo(MediaType.text.plain)))
        },
        test("should respond with empty") {
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
          val res = resourceOk.run().map(_.contentLength)
          assertZIO(res)(isSome(equalTo(7L)))
        },
        test("should have content") {
          val res = resourceOk.run().flatMap(_.body.asString)
          assertZIO(res)(equalTo("foo\nbar"))
        },
        test("should have content-type") {
          val res = resourceOk.run().map(_.mediaType)
          assertZIO(res)(isSome(equalTo(MediaType.text.plain)))
        },
        test("should respond with empty") {
          val res = resourceNotFound.run().map(_.status)
          assertZIO(res)(equalTo(Status.NotFound))
        },
      ),
    ),
  )

}
