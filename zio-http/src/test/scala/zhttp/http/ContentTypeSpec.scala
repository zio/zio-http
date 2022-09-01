package zhttp.http

import zhttp.internal.{DynamicServer, HttpRunnableSpec, testClient}
import zio._
import zio.test.Assertion.{equalTo, isNone, isSome}
import zio.test.TestAspect.timeout
import zio.test._

object ContentTypeSpec extends HttpRunnableSpec {

  val contentSpec = suite("Content type header on file response")(
    test("mp4") {
      val res = testClient.flatMap(client => Http.fromResource("TestFile2.mp4").deploy(client).contentType.run())
      assertZIO(res)(isSome(equalTo("video/mp4")))
    },
    test("js") {
      val res = testClient.flatMap(client => Http.fromResource("TestFile3.js").deploy(client).contentType.run())
      assertZIO(res)(isSome(equalTo("application/javascript")))
    },
    test("no extension") {
      val res = testClient.flatMap(client => Http.fromResource("TestFile4").deploy(client).contentType.run())
      assertZIO(res)(isNone)
    },
    test("css") {
      val res = testClient.flatMap(client => Http.fromResource("TestFile5.css").deploy(client).contentType.run())
      assertZIO(res)(isSome(equalTo("text/css")))
    },
    test("mp3") {
      val res = testClient.flatMap(client => Http.fromResource("TestFile6.mp3").deploy(client).contentType.run())
      assertZIO(res)(isSome(equalTo("audio/mpeg")))
    },
    test("unidentified extension") {
      val res = testClient.flatMap(client => Http.fromResource("truststore.jks").deploy(client).contentType.run())
      assertZIO(res)(isNone)
    },
    test("already set content-type") {
      val expected = MediaType.application.`json`
      val res      = testClient.flatMap(client =>
        Http.fromResource("TestFile6.mp3").map(_.withMediaType(expected)).deploy(client).contentType.run(),
      )
      assertZIO(res)(isSome(equalTo(expected.fullType)))
    },
  )

  private val env = DynamicServer.live ++ Scope.default

  override def spec = {
    suite("Content-type") {
      serve(DynamicServer.app).as(List(contentSpec))
    }.provideLayerShared(env) @@ timeout(5 seconds)
  }
}
