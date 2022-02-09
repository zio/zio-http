package zhttp.service.client.transport

import io.netty.bootstrap.Bootstrap
import io.netty.channel.{Channel, ChannelFuture, ChannelFutureListener}
import io.netty.handler.codec.http.FullHttpRequest
import zhttp.http.HeaderNames
import zhttp.service.Client.ClientRequest
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.handler.{NewClientChannelInitializer, NewClientInboundHandler}
import zhttp.service.client.model.ClientConnectionState.ReqKey
import zhttp.service.client.model.{ClientConnectionState, ConnectionRuntime, Timeouts}
import zio.{Task, ZIO}

import java.net.InetSocketAddress
import scala.collection.mutable

/*
  Can hold atomic reference to ZConnectionState comprising of
    - Timeouts like (idleTimeout,requestTimeout,connectionTimeout etc.)
    - states like (currentTotal)
    - Data structures like (idleQueue, waitingRequestQueue etc)
 */
case class ClientConnectionManager(
  connectionState: ClientConnectionState,
  timeouts: Timeouts,
  boo: Bootstrap,
  zExec: zhttp.service.HttpRuntime[Any],
) {

  /**
   *   - core method for getting a connection for a request
   *   - create new connection and increment allocated simultaneously (depending
   *     on limits)
   *   - assign a new callback (may be like empty promise to connection)
   *
   * TBD: uri Authority examples to be handled like examples Valid authority
   *   - http://www.xyz.com/path www.xyz.com
   *   - http://host:8080/path host:8080
   *   - http://user:pass@host:8080/path user:pass@host:8080
   * @param jReq
   * @return
   */
  def fetchConnection(jReq: FullHttpRequest, req: ClientRequest): Task[Channel] = for {
    reqKey <- getRequestKey(jReq, req)
    isWebSocket = req.url.scheme.exists(_.isWebSocket)
    isSSL       = req.url.scheme.exists(_.isSecure)
    channel <- getConnectionForRequestKey(reqKey, isWebSocket, isSSL)
  } yield channel

  def getRequestKey(jReq: FullHttpRequest, req: ClientRequest) = for {
    uri <- Task(new java.net.URI(jReq.uri()))
    host = if (uri.getHost == null) jReq.headers().get(HeaderNames.host) else uri.getHost
    _ <- Task(assert(host != null, "Host name is required"))
    port   = req.url.port.getOrElse(80)
    reqKey = if (port == -1) new ReqKey(host,80) else new InetSocketAddress(host, port)
  } yield reqKey

  /**
   * build an underlying connection (channel for a given request key)
   * @param scheme
   * @param reqKey
   * @tparam R
   * @return
   */
  def buildChannel[R](reqKey: ReqKey, isWebSocket: Boolean = false, isSSL: Boolean = false): Task[Channel] =
    for {
      init <- ZIO.effect(
        NewClientChannelInitializer(
          NewClientInboundHandler(zExec, this),
          isWebSocket,
          isSSL,
          reqKey,
          ClientSSLOptions.DefaultSSL,
        ),
      )
      (h, p) = (reqKey.getHostName, reqKey.getPort)
      prom    <- zio.Promise.make[Throwable, Channel]
      chf    = boo.handler(init).connect(h, p)
      _ <- prom.succeed(chf.channel())
      // optional can be removed if not really utilised.
//      _ <- attachHandler(chf)
      c <- prom.await
    } yield c
  
  def sendRequest(channel: Channel, connectionRuntime: ConnectionRuntime) = for {
    _ <- connectionState.currentAllocatedChannels.update(m => m + (channel -> connectionRuntime))
    _ <- Task(channel.pipeline().fireChannelActive())
  } yield ()

  def getConnectionForRequestKey(reqKey: ReqKey, isWebSocket: Boolean, isSSL: Boolean) = for {
    idleMap <- connectionState.idleConnectionsMap.get
    _ <- Task(println(s"idleMap: $idleMap"))
    idleQ = idleMap.get(reqKey).fold(mutable.Queue.empty[Channel])(q => q)
    channel <- if (!idleQ.isEmpty) Task(idleQ.dequeue())
      else {
        println(s"IDLE Q EMPTY CONNECTION for $reqKey")
        val ch = buildChannel(reqKey, isWebSocket, isSSL)
        connectionState.idleConnectionsMap.update(_ => Map(reqKey -> idleQ))
        ch
      }
    _ <- connectionState.idleConnectionsMap.set(Map(reqKey -> idleQ))
    _ <- Task(println(s"IDLEQ: $idleQ"))
  } yield channel

  def getActiveConnections: Task[Int] = for {
    alloc <- connectionState.currentAllocatedChannels.get
    _ <- ZIO.effect(println(s"alloc size: ${alloc.size}"))
    idleMap <- connectionState.idleConnectionsMap.get
    idle = idleMap.values.foldLeft(0){ (acc,q) => acc + q.size}
    _ <- ZIO.effect(println(s"idle size: ${idle}"))
  } yield (alloc.size + idle)

//  private def incrementConnection: Unit = ???
//  private def decrementConnection = ???
//  private def isConnectionExpired = ???
//  private def isConnectionWithinLimits = ???
//  private def addConnectionToIdleQ = ???
//  private def addConnectionToWaitQ = ???
//
//  def releaseConnection = ???
//  def shutdownConnectionManager = ???
//

//  def getActiveConnectionsForReqKey(reqKey: ReqKey): Task[Int] = connRef.get.map(_.size)
//
//  def getIdleConnections: Task[Int] = connRef.get.map(_.size)
//  def getIdleConnectionsForReqKey(reqKey: ReqKey): Task[Int] = connRef.get.map(_.size)

}

object ClientConnectionManager {}
