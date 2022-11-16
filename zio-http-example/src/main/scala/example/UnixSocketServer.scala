package example

import zio.ZIOAppDefault
import zio.http._
import zio.http.model.Method
import zio.http.netty.ChannelType

object UnixSocketServer extends ZIOAppDefault {

  // Create HTTP route
  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
    case Method.GET -> !! / "json" => Response.json("""{"greetings": "Hello World!"}""")
  }

  // Run it like any simple app
  val serverConfig = ServerConfig.default.copy(channelType = ChannelType.KQUEUE_UDS).binding("/tmp/server.sock")
  override val run = Server.serve(app).provide(ServerConfig.live(serverConfig), Server.live)

}
