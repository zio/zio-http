package zhttp.service.client.transport

import io.netty.bootstrap.Bootstrap
import io.netty.channel.{Channel, ChannelFuture, ChannelFutureListener}
import io.netty.handler.codec.http.FullHttpRequest
import zhttp.service.Client.ClientRequest
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.model.ClientConnectionState.ReqKey
import zhttp.service.client.model.{ClientConnectionState, ConnectionRuntime, Timeouts}
import zio.{Ref, Task, ZIO}
import zhttp.http.HeaderNames
import zhttp.service.client.handler.{NewClientChannelInitializer, NewClientInboundHandler}

import java.net.InetSocketAddress
import scala.collection.mutable

/*
  Can hold atomic reference to ZConnectionState comprising of
    - Timeouts like (idleTimeout,requestTimeout,connectionTimeout etc.)
    - states like (currentTotal)
    - Data structures like (idleQueue, waitingRequestQueue etc)
 */
case class ClientConnectionManager(
  connRef: Ref[mutable.Map[ReqKey, Channel]],       // Temp map for POC, to be removed
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
  def fetchConnection(jReq: FullHttpRequest, req: ClientRequest): Task[Channel] =     for {
    reqKey <- getRequestKey(jReq,req)
    isWebSocket = req.url.scheme.exists(_.isWebSocket)
    isSSL       = req.url.scheme.exists(_.isSecure)
    channel <- getConnectionForRequestKey(reqKey, isWebSocket, isSSL)
  } yield channel

  def getRequestKey(jReq: FullHttpRequest, req: ClientRequest) = for {
    uri  <- Task(new java.net.URI(jReq.uri()))
    host = if (uri.getHost == null) jReq.headers().get(HeaderNames.host) else uri.getHost
    _ <- Task(assert(host != null, "Host name is required"))
    port   = req.url.port.getOrElse(80)
    reqKey = new InetSocketAddress(host, port)
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
          NewClientInboundHandler(zExec, connectionState),
          isWebSocket,
          isSSL,
          reqKey,
          ClientSSLOptions.DefaultSSL,
        ),
      )
      (h, p) = (reqKey.getHostName, reqKey.getPort)
      chf    = boo.handler(init).connect(h, p)
      // optional can be removed if not really utilised.
      _ <- attachHandler(chf)
    } yield chf.channel()

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

  def sendRequest(channel: Channel, connectionRuntime: ConnectionRuntime) = Task{
//    incrementConnection
    this.connectionState.currentAllocatedChannels += (channel -> connectionRuntime)
    // trigger the channel, triggering inbound event propagation
    channel.pipeline().fireChannelActive()
  }.unit

  def getConnectionForRequestKey(reqKey: ReqKey, isWebSocket: Boolean, isSSL: Boolean) = for {
    mp <- connRef.get             // temporary map, to be replaced by ClientConnectionState
    // if already key exists for existing connections re-use it
    // else build a new connection (channel)
    channel <- mp.get(reqKey) match {
      case Some(c) =>
        println(s"REUSING CONNECTION for $reqKey")
        // To be tested to check if the channel is currently busy
        if (c.isWritable)
          Task.succeed(c)
        else
          buildChannel(reqKey, isWebSocket, isSSL)
      case _       =>
        buildChannel(reqKey, isWebSocket, isSSL)
    }
    _    <- connRef.update { m =>
      m += (reqKey -> channel)
    }
  } yield channel

  def getActiveConnections: Task[Int] = connRef.get.map(_.size)

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
