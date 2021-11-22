package zhttp

import zhttp.http._
import zhttp.service._
import zhttp.service.server._
import zio._
import zio.test.Assertion._
import zio.test.TestAspect._
import zio.test._

object IntegrationSpec extends DefaultRunnableSpec {
  val addr     = "localhost"
  val port     = 80
  val baseAddr = s"http://${addr}:${port}"
  val server   = Server.port(80) ++ Server.app(AllApis()) ++ Server.acceptContinue
  def env      = EventLoopGroup.auto() ++ ChannelFactory.auto ++ ServerChannelFactory.auto

  def spec = suite("IntegrationSpec")(
    ApiSpec,
    ContentDecoderSpec,
  ).provideCustomLayer(env)

  def ApiSpec = suite("ApiSpec") {
    testM("100 continue on /continue") {
      val response = Client.request(s"${baseAddr}/continue", List(Header("Expect", "100-continue")))
      assertM(response.map(_.status))(equalTo(Status.CONTINUE))
    } +
      testM("200 ok on /") {
        val response = Client.request(baseAddr)
        assertM(response.map(_.status))(equalTo(Status.OK))
      } +
      testM("201 created on /post") {
        val url      = URL(Path.apply(), URL.Location.Absolute(Scheme.HTTP, addr, port))
        val response = Client.request(Client.ClientParams((Method.POST, url)))
        assertM(response.map(_.status))(equalTo(Status.CREATED))
      } +
      testM("400 bad request on /subscriptions without the connection upgrade header") {
        val response = Client.request(s"${baseAddr}/subscriptions")
        assertM(response.map(_.status))(equalTo(Status.BAD_REQUEST))
      } +
      testM("408 timeout on /timeout") {
        val response = Client.request(s"${baseAddr}/timeout")
        assertM(response.map(_.status))(equalTo(Status.REQUEST_TIMEOUT))
      } +
      testM("500 internal server error on /boom") {
        val response = Client.request(s"${baseAddr}/boom")
        assertM(response.map(_.status))(equalTo(Status.INTERNAL_SERVER_ERROR))
      }
  }

  def AuthSpec = suite("AuthSpec") {
    testM("403 forbidden ok on /auth") {
      val response = Client.request(s"${baseAddr}/auth")
      assertM(response.map(_.status))(equalTo(Status.FORBIDDEN))
    }
  }

  def ContentDecoderSpec = suite("ContentDecoderSpec") {
    testM("ContentDecoder text identity") {
      val response = Client.request(s"${baseAddr}/contentdecoder/text")
      assertM(response.map(_.status))(equalTo(Status.OK))
    }
  }

  Runtime.default.unsafeRun(server.make.useForever.provideSomeLayer(env ++ zio.clock.Clock.live).forkDaemon)
}
