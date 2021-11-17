package zhttp

import zhttp.http._
import zio._

object IntegrationSpec extends IntegrationRunnableSpec(80) {
  Runtime.default.unsafeRun(serve(app).forkDaemon)

  def app = HttpApp.collectM {
    case Method.GET -> !!  => ZIO.succeed(Response.ok)
    case Method.POST -> !! => ZIO.succeed(Response.status(Status.CREATED))
  }

  def spec = suite("IntegrationSpec") {
    HttpIntegrationSpec.testSuite
  } provideCustomLayer env
}
