package zhttp.service.client

import io.netty.bootstrap.Bootstrap
import io.netty.channel.{Channel, ChannelFactory => JChannelFactory, EventLoopGroup => JEventLoopGroup}
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler
import zhttp.http.URL._
import zhttp.http._
import zhttp.service._
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.content.handlers.ClientSocketUpgradeHandler
import zhttp.socket.SocketApp
import zhttp.socket.SocketProtocol.Config.ClientConfig
import zio._

import java.net.InetSocketAddress

class SocketClient[R](rtm: HttpRuntime[R], cf: JChannelFactory[Channel], el: JEventLoopGroup) {
  def unsafeSocket(
    headers: Headers,
    ss: SocketApp[R],
    pr: Promise[Throwable, Client.ClientResponse],
    clientSSLOptions: ClientSSLOptions,
  ): Unit = {
    val config   = ss.protocol.narrow[ClientConfig].clientConfig(headers)
    val handlers = List(
      ClientSocketUpgradeHandler(rtm, pr),
      new WebSocketClientProtocolHandler(config),
      ClientSocketHandler(rtm, ss),
    )

    val url = URL.fromString(config.webSocketUri().toString).toOption

    val host   = url.flatMap(_.host)
    val port   = url.flatMap(_.port).fold(80) {
      case -1   => 80
      case port => port
    }
    val scheme = url
      .map(_.kind match {
        case Location.Relative               => ""
        case Location.Absolute(scheme, _, _) => scheme.asString
      })
      .get

    val init = ClientChannelInitializer(handlers, scheme, clientSSLOptions)

    val jboo = new Bootstrap().channelFactory(cf).group(el).handler(init)
    if (host.isDefined) jboo.remoteAddress(new InetSocketAddress(host.get, port))

    jboo.connect(): Unit
  }
}
