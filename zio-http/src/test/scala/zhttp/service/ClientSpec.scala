package zhttp.service

import zhttp.http._
import zhttp.internal.HttpRunnableSpec
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.server.ServerChannelFactory
import zio.duration.durationInt
import zio.test.Assertion.{anything, containsString, isEmpty, isNonEmpty}
import zio.test.TestAspect.{flaky, timeout}
import zio.test.assertM
import zio.{Task, ZIO}

object ClientSpec extends HttpRunnableSpec(8082) {
  val env = ChannelFactory.auto ++ EventLoopGroup.auto() ++ ServerChannelFactory.auto

  val app: HttpApp[Any, Nothing] = Http.collectM[Request] {
    case Method.GET -> !! / "users" / "zio" / "repos" =>
      ZIO.succeed(Response.ok)

    case Method.GET -> !! / "users" / "zio" =>
      ZIO.succeed(Response(status = Status.OK, data = HttpData.fromText("a zio user")))

    case data @ Method.POST -> !! / "users" / "zio" =>
      data.getBodyAsString
        .flatMap(content => ZIO.succeed(Response(status = Status.OK, data = HttpData.fromText(content))))
        .orDie

  }

  override def spec = suiteM("Client")(
    Server
      .make(Server.app(app) ++ Server.port(8082))
      .orDie
      .as(
        List(
          testM("respond Ok") {
            val actual = Client.request("http://localhost:8082/users/zio/repos", ClientSSLOptions.DefaultSSL)
            assertM(actual)(anything)
          } +
            testM("non empty content") {
              val actual          = Client.request("http://localhost:8082/users/zio", ClientSSLOptions.DefaultSSL)
              val responseContent = actual.flatMap(_.getBody)
              assertM(responseContent)(isNonEmpty)
            } +
            testM("POST request expect non empty response content") {
              val url             = Task.fromEither(URL.fromString("http://localhost:8082/users/zio"))
              val endpoint        = url.map(u => Method.POST -> u)
              val headers         = List(Header.userAgent("zio-http test"))
              val response        = endpoint.flatMap { e =>
                Client.request(e, headers, HttpData.fromText("test"))
              }
              val responseContent = response.flatMap(_.getBody)
              assertM(responseContent)(isNonEmpty)
            } +
            testM("empty content") {
              val actual          = Client.request("http://localhost:8082/users/zio/repos", ClientSSLOptions.DefaultSSL)
              val responseContent = actual.flatMap(_.getBody)
              assertM(responseContent)(isEmpty)
            } +
            testM("text content") {
              val actual          = Client.request("http://localhost:8082/users/zio", ClientSSLOptions.DefaultSSL)
              val responseContent = actual.flatMap(_.getBodyAsString)
              assertM(responseContent)(containsString("user"))
            },
        ),
      )
      .useNow,
  ).provideCustomLayer(env) @@ flaky @@ timeout(5 second)
}
