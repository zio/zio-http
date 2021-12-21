package zhttp.service

import zhttp.http._
import zhttp.internal.{AppCollection, HttpRunnableSpec}
import zhttp.service.server._
import zio.ZIO
import zio.duration.durationInt
import zio.test.Assertion.{anything, containsString, isEmpty, isNonEmpty}
import zio.test.TestAspect.{flaky, sequential, timeout}
import zio.test.assertM

object ClientSpec extends HttpRunnableSpec(8082) {

  override def spec = {
    suiteM("Client") {
      app.as(List(clientSpec)).useNow
    }.provideCustomLayerShared(env) @@ timeout(30 seconds) @@ sequential
  }

  def clientSpec = suite("Client")(
    testM("respond Ok") {
      val actual = request(!! / "users" / "zio" / "repos")
      assertM(actual)(anything)
    } +
      testM("non empty content") {
        val actual          = request(!! / "users" / "zio")
        val responseContent = actual.flatMap(_.getBody)
        assertM(responseContent)(isNonEmpty)
      } +
      testM("POST request expect non empty response content") {
        val headers         = List(Header.userAgent("zio-http test"))
        val response        = request(!! / "users" / "zio", method = Method.POST, headers = headers, content = "test")
        val responseContent = response.flatMap(_.getBody)
        assertM(responseContent)(isNonEmpty)
      } +
      testM("empty content") {
        val actual          = request(!! / "users" / "zio" / "repos")
        val responseContent = actual.flatMap(_.getBody)
        assertM(responseContent)(isEmpty)
      } +
      testM("text content") {
        val actual          = request(!! / "users" / "zio")
        val responseContent = actual.flatMap(_.getBodyAsString)
        assertM(responseContent)(containsString("user"))
      },
  ).provideCustomLayer(env) @@ flaky @@ timeout(5 second)

  val env = EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ AppCollection.live

  val clientApp: HttpApp[Any, Nothing] = Http.collectM[Request] {
    case Method.GET -> !! / "users" / "zio" / "repos" =>
      ZIO.succeed(Response.ok)

    case Method.GET -> !! / "users" / "zio" =>
      ZIO.succeed(Response(status = Status.OK, data = HttpData.fromText("a zio user")))

    case data @ Method.POST -> !! / "users" / "zio" =>
      data.getBodyAsString
        .flatMap(content => ZIO.succeed(Response(status = Status.OK, data = HttpData.fromText(content))))
        .orDie

  }

  private val app = serve { clientApp ++ AppCollection.app }
}
