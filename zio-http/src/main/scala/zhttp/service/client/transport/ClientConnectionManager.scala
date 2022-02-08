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
      _ <- attachHandler(chf)
      c <- prom.await
    } yield c

  /*
   mostly kept for debugging purposes
   or if we need to do something during creation lifecycle.
   */
  def attachHandler(chf: ChannelFuture) = {
    ZIO
      .effect(
        chf.addListener(new ChannelFutureListener() {
          override def operationComplete(future: ChannelFuture): Unit = {
            if (!future.isSuccess()) {
              println(s"error: ${future.cause().getMessage}")
              future.cause().printStackTrace()
            } else {
              //                println("FUTURE SUCCESS");
            }
          }
        }): Unit,
      )
  }

  def sendRequest(channel: Channel, connectionRuntime: ConnectionRuntime) = for {
    _ <- connectionState.currentAllocatedChannels.update(m => m + (channel -> connectionRuntime))
//    _ <- Task(channel.pipeline().fireChannelActive())
  } yield ()

  def getConnectionForRequestKey(reqKey: ReqKey, isWebSocket: Boolean, isSSL: Boolean) = for {
    connQueue <- Task(if (!connectionState.idleConnectionsMap.isEmpty) connectionState.idleConnectionsMap(reqKey)
    else mutable.Queue.empty[Channel])
    // if already key exists for existing connections re-use it
    // else build a new connection (channel)
    channel <- if (!connQueue.isEmpty) {
      println(s"REUSING CONNECTION for $reqKey")
      Task(connectionState.idleConnectionsMap(reqKey).dequeue())
    }
    else {
      println(s"BUILDING NEW CONNECTION")
      buildChannel(reqKey, isWebSocket, isSSL)
    }
  } yield channel

  def getActiveConnections: Task[Int] = Task(0)

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
