package zhttp.service

import zhttp.http._
import zhttp.internal.{DynamicServer, HttpRunnableSpec, testClient}
import zio._
import zio.test.Assertion.{equalTo, isSome}
import zio.test.TestAspect.timeout
import zio.test.assertZIO

import java.io.File

object StaticFileServerSpec extends HttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ DynamicServer.live ++ Scope.default

  private val fileOk = Http.fromZIO(testClient).flatMap(client => Http.fromResource("TestFile.txt").deploy(client))
  private val fileNotFound = Http.fromZIO(testClient).flatMap(client => Http.fromResource("Nothing").deploy(client))

  private val testArchivePath  = getClass.getResource("/TestArchive.jar").getPath
  private val resourceOk       =
    Http
      .fromZIO(testClient)
      .flatMap(client =>
        Http.fromResourceWithURL(new java.net.URL(s"jar:file:$testArchivePath!/TestFile.txt")).deploy(client),
      )
  private val resourceNotFound =
    Http
      .fromZIO(testClient)
      .flatMap(client =>
        Http.fromResourceWithURL(new java.net.URL(s"jar:file:$testArchivePath!/NonExistent.txt")).deploy(client),
      )

  override def spec = suite("StaticFileServer") {
    serve(DynamicServer.app).as(List(staticSpec))
  }.provideLayerShared(env) @@ timeout(5 seconds)

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
          val res = testClient
            .flatMap(client => Http.fromFile(throw new Error("Wut happened?")).deploy(client).run().map(_.status))
          assertZIO(res)(equalTo(Status.InternalServerError))
        },
      ),
      suite("invalid file")(
        test("should respond with 500") {
          final class BadFile(name: String) extends File(name) {
            override def length: Long    = throw new Error("Haha")
            override def isFile: Boolean = true
          }
          val res = testClient
            .flatMap(client => Http.fromFile(new BadFile("Length Failure")).deploy(client).run().map(_.status))
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
