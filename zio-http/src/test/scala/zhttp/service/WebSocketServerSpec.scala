package zhttp.service

import zhttp.http._
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.server._
import zhttp.socket.{Socket, WebSocketFrame}
import zio.ZIO
import zio.duration._
import zio.test.Assertion.equalTo
import zio.test.TestAspect.timeout
import zio.test._

object WebSocketServerSpec extends HttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ ServerChannelFactory.nio ++ DynamicServer.live ++ ChannelFactory.nio
  private val app = serve { DynamicServer.app }

  override def spec = suiteM("Server") {
    app.as(List(websocketSpec)).useNow
  }.provideCustomLayerShared(env) @@ timeout(30 seconds)

  def websocketSpec = suite("WebSocket Server") {
    suite("connections") {
      testM("Multiple websocket upgrades") {

        val app   = Socket.succeed(WebSocketFrame.text("BAR")).toHttp.deployWS
        val codes = ZIO.foreach(1 to 1024) { _ =>
          for {
            code <- app(Socket.empty.toSocketApp)
              .map(_.status)
              .catchAll {
                case None        => ZIO.fail(new Error("No status code"))
                case Some(error) => ZIO.fail(error)
              }
          } yield code == Status.SWITCHING_PROTOCOLS
        }

        val allTrue = codes.map(_.count(identity))
        assertM(allTrue)(equalTo(1024))
      }
    }
  }
}
