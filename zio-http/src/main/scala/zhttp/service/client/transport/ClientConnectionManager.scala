package zhttp.service.client.transport

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.handler.codec.http.FullHttpRequest
import zhttp.http.HeaderNames
import zhttp.service.Client.{ClientRequest, ClientResponse}
import zhttp.service.ClientSettings.Config
import zhttp.service.client.domain.ConnectionData.ReqKey
import zhttp.service.client.domain.{Connection, ConnectionData, ConnectionState, Timeouts}
import zio.{Promise, Task, ZIO}

import java.net.InetSocketAddress
import scala.collection.immutable

/**
 * TODO: Comments
 */
case class ClientConnectionManager(
  connectionData: ConnectionData,
  timeouts: Timeouts,
  boo: Bootstrap,
  zExec: zhttp.service.HttpRuntime[Any],
) {

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
    jReq: FullHttpRequest,
    req: ClientRequest,
    promise: Promise[Throwable, ClientResponse],
  ): Task[Connection] = ???

  def getRequestKey(jReq: FullHttpRequest, req: ClientRequest) = for {
    uri <- Task(new java.net.URI(jReq.uri()))
    host = if (uri.getHost == null) jReq.headers().get(HeaderNames.host) else uri.getHost
    _ <- Task(assert(host != null, "Host name is required"))
    port   = req.url.port.getOrElse(80)
    reqKey = if (port == -1) new ReqKey(host, 80) else new InetSocketAddress(host, port)
  } yield reqKey

  /**
   * Get a connection
   *   - If an idle connection already exists for given ReqKey (host:port) reuse
   *     it
   *   - If no idle connection found build a new one.
   * @param jReq
   * @param promise
   * @param reqKey
   * @param isWebSocket
   * @param isSSL
   * @return
   */

  def triggerConn(
    idleChannelOpt: Option[Connection],
    jReq: FullHttpRequest,
    promise: Promise[Throwable, ClientResponse],
    reqKey: ReqKey,
    isWebSocket: Boolean,
    isSSL: Boolean,
  ) = ???

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
  ): Task[Connection] = ???

  /**
   * Attach Handler to a ChannelFuture
   */
  def attachHandler(
    connection: Connection,
    jReq: FullHttpRequest,
    promise: Promise[Throwable, ClientResponse],
  ) = ???
}

object ClientConnectionManager {
  def apply(settings: Config): ZIO[Any, Throwable, ClientConnectionManager] = for {
    channelFactory <- settings.transport.clientChannel
    eventLoopGroup <- settings.transport.eventLoopGroup(settings.threads)
    zExec          <- zhttp.service.HttpRuntime.default[Any]
    clientBootStrap = new Bootstrap()
      .channelFactory(channelFactory)
      .group(eventLoopGroup)
    connectionDataRef <- zio.Ref.make(
      (
        None.asInstanceOf[Option[Connection]],
        ConnectionState(Map.empty[Channel, ReqKey], Map.empty[ReqKey, immutable.Queue[Connection]]),
      ),
    )
    timeouts    = Timeouts(settings.connectionTimeout, settings.idleTimeout, settings.requestTimeout)
    connManager = ClientConnectionManager(ConnectionData(connectionDataRef), timeouts, clientBootStrap, zExec)
  } yield connManager
}
