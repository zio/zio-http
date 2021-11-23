package zhttp

import zhttp.http.UHttpResponse
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{ChannelFactory, EventLoopGroup, Server}
import zio.Chunk

object IntegrationSpecHelper {
  val addr     = "localhost"
  val port     = 80
  val baseAddr = s"http://${addr}:${port}"
  val server   = Server.port(80) ++ Server.app(AllApis())
  def env      = EventLoopGroup.auto() ++ ChannelFactory.auto ++ ServerChannelFactory.auto ++ zio.clock.Clock.live

  implicit class StringToChunk(val string: String) extends AnyVal {
    def toChunk = Chunk.fromArray(string.getBytes())
  }

  implicit class ResponseHelper(val response: UHttpResponse) extends AnyVal {
    def headerRemoved(name: String) =
      response.copy(headers = response.headers.filterNot(_.name == name))
  }
}
