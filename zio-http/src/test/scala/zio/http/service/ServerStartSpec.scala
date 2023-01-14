package zio.http.service

import zio.http.internal.{DynamicServer, HttpRunnableSpec}
import zio.http.{Http, Server, ServerConfig}
import zio.test.Assertion.{equalTo, not}
import zio.test._
import zio.{Scope, ZIO}

object ServerStartSpec extends HttpRunnableSpec {

  def serverStartSpec = suite("ServerStartSpec")(
    test("desired port") {
      val port   = 8088
      val config = ServerConfig.default.port(port)
      serve(Http.empty).flatMap { port =>
        assertZIO(ZIO.attempt(port))(equalTo(port))
      }.provide(ServerConfig.live(config), DynamicServer.live, Server.live)
    },
    test("available port") {
      val port   = 0
      val config = ServerConfig.default.port(port)
      serve(Http.empty).flatMap { bindPort =>
        assertZIO(ZIO.attempt(bindPort))(not(equalTo(port)))
      }.provide(ServerConfig.live(config), DynamicServer.live, Server.live)
    },
  )

  override def spec: Spec[TestEnvironment with Scope, Any] = serverStartSpec
}
