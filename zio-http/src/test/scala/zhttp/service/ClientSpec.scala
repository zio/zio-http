package zhttp.service

import zhttp.http._
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.server._
import zio.test.Assertion._
import zio.test.TestAspect.{sequential, timeout}
import zio.test._
import zio.{Scope, durationInt}

import java.net.ConnectException

object ClientSpec extends HttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live ++ Scope.default

  def clientSpec = suite("ClientSpec") {
    test("respond Ok") {
      val app = Http.ok.deploy.status.run()
      assertZIO(app)(equalTo(Status.Ok))
    } +
      test("non empty content") {
        val app             = Http.text("abc")
        val responseContent = app.deploy.body.run()
        assertZIO(responseContent)(isNonEmpty)
      } +
      test("echo POST request content") {
        val app = Http.collectZIO[Request] { case req => req.bodyAsString.map(Response.text(_)) }
        val res = app.deploy.bodyAsString.run(method = Method.POST, content = HttpData.fromString("ZIO user"))
        assertZIO(res)(equalTo("ZIO user"))
      } +
      test("non empty content") {
        val app             = Http.empty
        val responseContent = app.deploy.body.run().map(_.length)
        assertZIO(responseContent)(isGreaterThan(0))
      } +
      test("text content") {
        val app             = Http.text("zio user does not exist")
        val responseContent = app.deploy.bodyAsString.run()
        assertZIO(responseContent)(containsString("user"))
      } +
      test("handle connection failure") {
        val res = Client.request("http://localhost:1").either
        assertZIO(res)(isLeft(isSubtype[ConnectException](anything)))
      }
  }

  override def spec = {
    suite("Client") {
      serve(DynamicServer.app).as(List(clientSpec))
    }.provideLayerShared(env) @@ timeout(5 seconds) @@ sequential
  }
}
