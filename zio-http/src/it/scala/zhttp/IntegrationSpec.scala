package zhttp

import zhttp.http._
import zhttp.service.Client
import zio._
import zio.test.Assertion._
import zio.test._

object IntegrationSpec extends IntegrationRunnableSpec(80) {
  Runtime.default.unsafeRun(serve(app).forkDaemon)

  def app = HttpApp.collectM {
    case Method.GET -> !!  => ZIO.succeed(Response.ok)
    case Method.POST -> !! => ZIO.succeed(Response.status(Status.CREATED))
  }

  def spec = suite("IntegrationSpec") {
    HttpSpec
  } provideCustomLayer env

  def HttpSpec = suite("HttpSpec") {
    testM("200 ok on /") {
      val response = Client.request(s"http://${addr}:${port}")

      assertM(response.map(_.status))(
        equalTo(Status.OK),
      )
    } + testM("201 created on /post") {
      val response = Client.request(
        Client.ClientParams((Method.POST, URL(Path.apply(), URL.Location.Absolute(Scheme.HTTP, addr, port)))),
      )

      assertM(response.map(_.status))(equalTo(Status.CREATED))
    }
  }
}
