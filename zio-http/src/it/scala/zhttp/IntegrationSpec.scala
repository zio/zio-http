package zhttp

import zhttp.http._
import zhttp.http.middleware.HttpMiddleware.basicAuth
import zhttp.service._
import zhttp.service.server._
import zio._
import zio.test.Assertion.equalTo
import zio.test._

object IntegrationSpec extends DefaultRunnableSpec {
  val addr     = "localhost"
  val port     = 80
  val baseAddr = s"http://${addr}:${port}"
  def env      = EventLoopGroup.auto() ++ ChannelFactory.auto ++ ServerChannelFactory.auto

  def api = HttpApp.collect {
    case Method.GET -> !!          => Response.ok
    case Method.POST -> !!         => Response.status(Status.CREATED)
    case Method.GET -> !! / "boom" => Response.status(Status.INTERNAL_SERVER_ERROR)
  }

  def basicAuthApi = HttpApp.collect { case Method.GET -> !! / "auth" =>
    Response.ok
  } @@ basicAuth("root", "changeme")

  def spec = suite("IntegrationSpec")(
    HttpSpec,
  ).provideCustomLayer(env)

  def HttpSpec = suite("HttpStatusSpec") {
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
      testM("403 forbidden ok on /auth") {
        val response = Client.request(s"${baseAddr}/auth")

        assertM(response.map(_.status)) {
          equalTo(Status.FORBIDDEN)
        }
      } +
      testM("500 internal server error on /boom") {
        val response = Client.request(s"${baseAddr}/boom")

        assertM(response.map(_.status))(equalTo(Status.INTERNAL_SERVER_ERROR))
      }
  }

  Runtime.default.unsafeRun(Server.start(port, (api +++ basicAuthApi)).forkDaemon)
}
