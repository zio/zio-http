package zhttp

import zhttp.http._
import zhttp.service._
import zhttp.service.server._
import zio._
import zio.duration.durationInt
import zio.test.Assertion.equalTo
import zio.test.TestAspect.{ignore, timeout}
import zio.test._

object IntegrationSpec extends DefaultRunnableSpec {
  val addr     = "localhost"
  val port     = 80
  val baseAddr = s"http://${addr}:${port}"
  def env      = EventLoopGroup.auto() ++ ChannelFactory.auto ++ ServerChannelFactory.auto

  def spec = suite("IntegrationSpec")(
    ApiSpec,
    ContentDecoderSpec,
  ).provideCustomLayer(env) @@ timeout(10 seconds)

  def ApiSpec = suite("ApiSpec") {
    testM("200 ok on /") {
      val response = Client.request(baseAddr)

      assertM(response.map(_.status))(equalTo(Status.OK))
    } +
      testM("201 created on /post") {
        val url      = URL(Path.apply(), URL.Location.Absolute(Scheme.HTTP, addr, port))
        val endpoint = (Method.POST, url)

        val response = Client.request(Client.ClientParams(endpoint))

        assertM(response.map(_.status))(equalTo(Status.CREATED))
      } +
      testM("500 internal server error on /boom") {
        val response = Client.request(s"${baseAddr}/boom")

        assertM(response.map(_.status))(equalTo(Status.INTERNAL_SERVER_ERROR))
      }
  }

  def AuthSpec = suite("AuthSpec") {
    testM("403 forbidden ok on /auth") {
      val response = Client.request(s"${baseAddr}/auth")

      assertM(response.map(_.status)) {
        equalTo(Status.FORBIDDEN)
      }
    }
  }

  def ContentDecoderSpec = suite("ContentDecoderSpec") {
    testM("ContentDecoder text identity") {
      val response = Client.request(s"${baseAddr}/contentdecoder/text")

      assertM(response.map(_.status))(equalTo(Status.OK))
    } @@ ignore
  }

  Runtime.default.unsafeRun(Server.start(port, AllApis()).forkDaemon)
}
