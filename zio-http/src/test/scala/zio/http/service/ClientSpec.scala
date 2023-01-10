package zio.http.service

import zio.http._
import zio.http.internal.{DynamicServer, HttpRunnableSpec, severTestLayer}
import zio.http.model._
import zio.stream.ZStream
import zio.test.Assertion.{isLeft, _}
import zio.test.TestAspect.{sequential, timeout}
import zio.test.assertZIO
import zio.{Scope, durationInt}

import java.net.ConnectException

object ClientSpec extends HttpRunnableSpec {

  def clientSpec = suite("ClientSpec")(
    test("respond Ok") {
      val app = Handler.ok.toRoute.deploy.status.run()
      assertZIO(app)(equalTo(Status.Ok))
    },
    test("non empty content") {
      val app             = Handler.text("abc").toRoute
      val responseContent = app.deploy.body.run().flatMap(_.asChunk)
      assertZIO(responseContent)(isNonEmpty)
    },
    test("echo POST request content") {
      val app = Handler.fromFunctionZIO[Request] { req => req.body.asString.map(Response.text(_)) }.toRoute
      val res = app.deploy.body.mapZIO(_.asString).run(method = Method.POST, body = Body.fromString("ZIO user"))
      assertZIO(res)(equalTo("ZIO user"))
    },
    test("empty content") {
      val app             = Route.empty
      val responseContent = app.deploy.body.run().flatMap(_.asString.map(_.length))
      assertZIO(responseContent)(equalTo(0))
    },
    test("text content") {
      val app             = Handler.text("zio user does not exist").toRoute
      val responseContent = app.deploy.body.mapZIO(_.asString).run()
      assertZIO(responseContent)(containsString("user"))
    },
    test("handle connection failure") {
      val res = Client.request("http://localhost:1").either
      assertZIO(res)(isLeft(isSubtype[ConnectException](anything)))
    },
    test("streaming content to server") {
      val app    = Handler.fromFunctionZIO[Request] { req => req.body.asString.map(Response.text(_)) }.toRoute
      val stream = ZStream.fromIterable(List("a", "b", "c"), chunkSize = 1)
      val res    = app.deploy.body
        .run(method = Method.POST, body = Body.fromStream(stream))
        .flatMap(_.asString)
      assertZIO(res)(equalTo("abc"))
    },
  )

  override def spec = {
    suite("Client") {
      serve(DynamicServer.app).as(List(clientSpec))
    }.provideShared(DynamicServer.live, severTestLayer, Client.default, Scope.default) @@
      timeout(5 seconds) @@ sequential
  }
}
