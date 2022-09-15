package zio.http.service

import zhttp.http.{Http, Method, Request, Status}
import zhttp.internal.DynamicServer
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{ChannelFactory, EventLoopGroup}
import zio.http.Client
import zio.http.internal.HttpRunnableSpec
import zio.stream.ZStream
import zio.test.Assertion._
import zio.test.TestAspect.timeout
import zio.test.assertM

import java.net.ConnectException

object ClientSpec extends HttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live

  def clientSpec = suite("ClientSpec") {
    testM("respond Ok") {
      val app = Http.ok.deploy.status.run()
      assertM(app)(equalTo(Status.Ok))
    } +
      testM("non empty content") {
        val app             = Http.text("abc")
        val responseContent = app.deploy.body.run()
        assertM(responseContent)(isNonEmpty)
      } +
      testM("echo POST request content") {
        val app = Http.collectZIO[Request] { case req => req.bodyAsString.map(Response.text(_)) }
        val res = app.deploy.bodyAsString.run(method = Method.POST, content = HttpData.fromString("ZIO user"))
        assertM(res)(equalTo("ZIO user"))
      } +
      testM("non empty content") {
        val app             = Http.empty
        val responseContent = app.deploy.body.run().map(_.length)
        assertM(responseContent)(isGreaterThan(0))
      } +
      testM("text content") {
        val app             = Http.text("zio user does not exist")
        val responseContent = app.deploy.bodyAsString.run()
        assertM(responseContent)(containsString("user"))
      } +
      testM("handle connection failure") {
        val res = Client.request("http://localhost:1").either
        assertM(res)(isLeft(isSubtype[ConnectException](anything)))
      } +
      testM("streaming content to server") {
        val app    = Http.collectZIO[Request] { case req => req.bodyAsString.map(Response.text(_)) }
        val stream = ZStream.fromIterable(List("a", "b", "c"))
        val res    = app.deploy.bodyAsString
          .run(method = Method.POST, content = HttpData.fromStream(stream))
        assertM(res)(equalTo("abc"))
      } +
      testM("streaming content from server - extended") {
        val app    = Http.collect[Request] { case req => Response(data = HttpData.fromStream(req.bodyAsStream)) }
        val stream = ZStream.fromIterable(List("This ", "is ", "a ", "longer ", "text."))
        val res    = app.deployChunked.bodyAsString
          .run(method = Method.POST, content = HttpData.fromStream(stream))
        assertM(res)(equalTo("This is a longer text."))
      }
  }

  override def spec = {
    suiteM("Client") {
      serve(DynamicServer.app).as(List(clientSpec)).useNow
    }.provideCustomLayerShared(env) @@ timeout(20 seconds) @@ sequential
  }
}
