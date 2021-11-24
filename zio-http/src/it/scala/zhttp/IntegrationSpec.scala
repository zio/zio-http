package zhttp

import zhttp.http.Status._
import zhttp.http._
import zhttp.internal.{AllApis, IntegrationTestAssertions, IntegrationTestExtensions}
import zhttp.service._
import zhttp.service.server.ServerChannelFactory
import zio.duration._
import zio._
import zio.test.TestAspect._
import zio.test._

object IntegrationSpec
    extends DefaultRunnableSpec
    with AllApis
    with IntegrationTestExtensions
    with IntegrationTestAssertions {
  def server = Server.port(port) ++ Server.app(app)
  def env    = EventLoopGroup
    .auto() ++ ChannelFactory.auto ++ ServerChannelFactory.auto ++ zio.clock.Clock.live ++ zio.blocking.Blocking.live

  def spec = suite("IntegrationSpec")(
    StatusCodeSpec,
  ).provideCustomLayer(env) @@ timeout(30 seconds)

  def StatusCodeSpec = suite("StatusCodeSpec") {
    testM("200 ok on /[GET]") {
      val zResponse = Client.request((Method.GET, "/".url))
      assertM(zResponse.getStatus)(status(OK))
    } +
      testM("201 created on /[POST]") {
        val zResponse = Client.request((Method.POST, "/".url))
        assertM(zResponse.getStatus)(status(CREATED))
      } +
      testM("204 no content ok on /[PUT]") {
        val zResponse = Client.request((Method.PUT, "/".url))
        assertM(zResponse.getStatus)(status(NO_CONTENT))
      } +
      testM("204 no content ok on /[DELETE]") {
        val zResponse = Client.request((Method.DELETE, "/".url))
        assertM(zResponse.getStatus)(status(NO_CONTENT))
      } +
      testM("404 on a random route") {
        checkM(Gen.int(1, 2).flatMap(Gen.stringN(_)(Gen.alphaNumericChar))) { randomUrl =>
          val zResponse = Client.request((Method.GET, randomUrl.url))
          assertM(zResponse.getStatus)(status(NOT_FOUND))
        }
      } +
      testM("400 bad request on /subscriptions without the connection upgrade header") {
        val zResponse = Client.request((Method.GET, "/subscriptions".url))
        assertM(zResponse.getStatus)(status(BAD_REQUEST))
      } +
      testM("500 internal server error on /boom") {
        val zResponse = Client.request((Method.GET, "/boom".url))
        assertM(zResponse.getStatus)(status(INTERNAL_SERVER_ERROR))
      } +
      testM("200 ok on Stream file") {
        val zResponse = Client.request((Method.GET, "/stream/file".url))
        assertM(zResponse.getStatus)(status(OK))
      } @@ ignore
  }

  Runtime.default.unsafeRun(server.make.useForever.provideSomeLayer(env).forkDaemon)
}
