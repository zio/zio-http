package zhttp.service.client

import io.netty.bootstrap.Bootstrap
import io.netty.channel.{Channel, ChannelFactory => JChannelFactory, EventLoopGroup => JEventLoopGroup}
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler
import zhttp.http._
import zhttp.service._
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.content.handlers.ClientSocketUpgradeHandler
import zhttp.socket.SocketApp
import zio._

import java.net.{InetSocketAddress, URI}

class SocketClient[R](rtm: HttpRuntime[R], cf: JChannelFactory[Channel], el: JEventLoopGroup) {
  def unsafeSocket(
    uri: String,
    headers: Headers,
    ss: SocketApp[R],
    pr: Promise[Throwable, Client.ClientResponse],
    clientSSLOptions: ClientSSLOptions,
  ): Unit = {
    val url      = new URI(uri)
    val config   = ss.protocol.clientBuilder.webSocketUri(uri).customHeaders(headers.encode)
    val handlers = List(
      ClientSocketUpgradeHandler(rtm, pr),
      new WebSocketClientProtocolHandler(config.build()),
      new SocketAppHandler(rtm, ss),
    )

    val host = url.getHost
    assert(host != null)

    val scheme = url.getScheme
    assert(scheme != null)

    val port = if (url.getPort == -1) 80 else url.getPort

    val init = ClientChannelInitializer(handlers, scheme, clientSSLOptions)

    val jboo = new Bootstrap().channelFactory(cf).group(el).handler(init)
    jboo.remoteAddress(new InetSocketAddress(host, port))

    jboo.connect(): Unit
  }
}
