package zhttp.service.client.transport

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.handler.codec.http.FullHttpRequest
import zhttp.service.Client.{ClientRequest, ClientResponse}
import zhttp.service.ClientSettings.Config
import zhttp.service.HttpMessageCodec
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.domain.ConnectionData.ReqKey
import zhttp.service.client.domain.{Connection}
import zhttp.service.client.handler.{EnhancedClientChannelInitializer, EnhancedClientInboundHandler}
import zio.{Promise, Task, ZIO}

import java.net.InetSocketAddress

/**
 * Holds Reference to Client state (ConnectionData) have functions to build /
 * reuse netty channels for request / response
 */
case class ClientConnectionManager(
//  connectionData: ConnectionData,
  boo: Bootstrap,
  zExec: zhttp.service.HttpRuntime[Any],
) extends HttpMessageCodec {

  /**
   * core method for getting a connection for a request
   *   - create new connection and increment allocated simultaneously (depending
   *     on limits)
   *   - assign a new callback (may be like empty promise to connection)
   *     Connections are referenced based on RequestKey \```type ReqKey =
   *     InetSocketAddress```
   *
   * @param jReq
   * @param req
   * @param promise
   * @return
   */
  def fetchConnection(
    req: ClientRequest,
    promise: Promise[Throwable, ClientResponse],
  ): Task[Connection] = for {
    reqKey <- getRequestKey(req)
    isWebSocket = req.url.scheme.exists(_.isWebSocket)
    isSSL       = req.url.scheme.exists(_.isSecure)
    conn <- buildChannel(reqKey, isWebSocket, isSSL)
    jReq <- encode(req)
    _    <- attachHandler(conn, jReq, promise)
  } yield conn

  def getRequestKey(req: ClientRequest) = for {
    host <- Task(req.host.fold { "localhost" }(_.toString))
//    _    <- Task(assert(host != null, "Host name is required"))
    port   = req.url.port.getOrElse(80)
    reqKey = if (port == -1) new ReqKey(host, 80) else new InetSocketAddress(host, port)
  } yield reqKey

  /**
   * build an underlying connection (channel for a given request key)
   *
   * @param jReq
   *   FullHttpRequest
   * @param promise
   *   Empty promise used as a callback to retrieve response
   * @param reqKey
   *   InetSocketAddress used as a "key" in the state to reference connections
   *   corresponding to this host:port
   * @param isWebSocket
   * @param isSSL
   * @tparam R
   *   // Not sure if we need this.
   * @return
   */
  def buildChannel[R](
    reqKey: ReqKey,
    isWebSocket: Boolean = false,
    isSSL: Boolean = false,
  ): Task[Connection] =
    for {
      init <- ZIO.effect(
        EnhancedClientChannelInitializer(
          isWebSocket,
          isSSL,
          reqKey,
          ClientSSLOptions.DefaultSSL,
        ),
      )
      (h, p) = (reqKey.getHostName, reqKey.getPort)
      prom <- zio.Promise.make[Throwable, Channel]
      chf = boo.handler(init).connect(h, p)
      _ <- prom.succeed(chf.channel())
      c <- prom.await
    } yield Connection(c, false)

  /*
   */
  def attachHandler(
    connection: Connection,
    jReq: FullHttpRequest,
    promise: Promise[Throwable, ClientResponse],
  ) = {
    ZIO
      .effect(
        connection.channel
          .newSucceededFuture()
          .addListener(new io.netty.channel.ChannelFutureListener() {

            override def operationComplete(future: io.netty.channel.ChannelFuture): Unit = {
              if (!future.isSuccess()) {
                println(s"error: ${future.cause().getMessage}")
                future.cause().printStackTrace()
              } else {
                if (connection.isReuse) {
                  if (future.channel.pipeline().get(zhttp.service.CLIENT_INBOUND_HANDLER) != null)
                    future.channel().pipeline().remove(zhttp.service.CLIENT_INBOUND_HANDLER)
                }
                future
                  .channel()
                  .pipeline()
                  .addLast(
                    zhttp.service.CLIENT_INBOUND_HANDLER,
                    EnhancedClientInboundHandler(zExec, jReq, promise),
                  ): Unit
              }
            }
          }): Unit,
      )
  }
}
object ClientConnectionManager {
  def apply(settings: Config): ZIO[Any, Throwable, ClientConnectionManager] = for {
    channelFactory <- settings.transport.clientChannel
    eventLoopGroup <- settings.transport.eventLoopGroup(settings.threads)
    zExec          <- zhttp.service.HttpRuntime.default[Any]
    clientBootStrap = new Bootstrap()
      .channelFactory(channelFactory)
      .group(eventLoopGroup)
    connManager = ClientConnectionManager(clientBootStrap, zExec)
  } yield connManager
}
