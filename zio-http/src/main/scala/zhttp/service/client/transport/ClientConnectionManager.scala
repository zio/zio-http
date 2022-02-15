package zhttp.service.client.transport

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.handler.codec.http.FullHttpRequest
import zhttp.http.HeaderNames
import zhttp.service.Client.{ClientRequest, ClientResponse}
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.handler.{EnhancedClientChannelInitializer, EnhancedClientInboundHandler}
import zhttp.service.client.model.ClientConnectionState.ReqKey
import zhttp.service.client.model.{ClientConnectionState, ClientConnectionStateData, Connection, Timeouts}

import scala.collection.immutable
//import zio.stm.TRef
import zio.{Promise, Task, ZIO}

import java.net.InetSocketAddress

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
   *     Connections are referenced based on RequestKey ```type ReqKey =
   *     InetSocketAddress```
   *
   * @param jReq
   * @return
   */
  def fetchConnection(
    jReq: FullHttpRequest,
    req: ClientRequest,
    promise: Promise[Throwable, ClientResponse],
  ): Task[Connection] = for {
    reqKey <- getRequestKey(jReq, req)
    isWebSocket = req.url.scheme.exists(_.isWebSocket)
    isSSL       = req.url.scheme.exists(_.isSecure)
    getClientStateData <- connectionState.clientData.updateAndGet {
      clientData =>
        getIdleChannel(reqKey, clientData.currentAllocatedChannels, clientData.idleConnectionsMap)

    }
    _ <- Task.effect(println(s"getClientStateData: $getClientStateData"))
    conn <- triggerConn(getClientStateData.tempData,jReq,promise,reqKey,isWebSocket,isSSL)
  } yield conn

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
  ) = for {
    clientData <- connectionState.clientData.get
    connection <- idleChannelOpt match {
      case Some(ch) =>
        println(s"SOME CHANNEL: $ch  clientData.currentAllocatedChannels: ${clientData.currentAllocatedChannels}")
//        if (ch != null && !clientData.currentAllocatedChannels.contains(ch.channel)) Task {
        if (ch != null) Task {
          println(s"IDLE CHANNEL FOUND REUSING ......$ch")
          (ch.copy(isReuse = true, isFree = false))
        }
        else {
          println(s"$ch IS NULL building new")
          buildChannel(reqKey, isWebSocket, isSSL)
        }
      case None => {
        println(s"NONE building new")
        buildChannel(reqKey, isWebSocket, isSSL)
      }
    }
    _          <- Task.effect(println(s"ATTACHING HANDLER for ${connection.channel.id}"))
    _          <- attachHandler(connection, jReq, promise,this,reqKey)
  } yield connection

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
//    jReq: FullHttpRequest,
//    promise: Promise[Throwable, ClientResponse],
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
      // optional can be removed if not really utilised.
      c <- prom.await
    } yield Connection(c, false, false)

  /*
   mostly kept for debugging purposes
   or if we need to do something during creation lifecycle.
   */
  def attachHandler(
    connection: Connection,
    jReq: FullHttpRequest,
    promise: Promise[Throwable, ClientResponse],
    connectionManager: ClientConnectionManager,
    reqKey: ReqKey,
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
                    EnhancedClientInboundHandler(zExec, jReq, promise,connectionManager,reqKey,connection),
                  ): Unit
              }
            }
          }): Unit,
      )
  }

  def getIdleChannel(reqKey: ReqKey, currentAllocatedChannels: Map[Channel, ReqKey],
                     idleConnectionsMap: Map[ReqKey, immutable.Queue[Connection]]) =  {
    val idleMap =
      if (idleConnectionsMap.get(reqKey).isEmpty) (idleConnectionsMap + (reqKey -> immutable.Queue.empty[Connection]))
      else idleConnectionsMap
    val idleQ = idleMap(reqKey)
    // if no queue exists for this req key add an empty queue
    val defaultState = ClientConnectionStateData(None, currentAllocatedChannels, idleMap)
    val res = if (idleQ.isEmpty) defaultState else {
      val conn = idleQ.dequeue
      println(s"CONN: $conn === $currentAllocatedChannels")
      if (conn == null) defaultState
      else {
        val newCurrMap = currentAllocatedChannels.updated(conn._1.channel, reqKey)
        println(s"newCurrMap: $newCurrMap")
        val newIdleMap = idleMap.updated(reqKey,conn._2)
        ClientConnectionStateData(Some(conn._1),newCurrMap, newIdleMap)
      }
    }
    println(s"RES: $res")
    res
  }

  def addIdleChannel(connection: Connection, reqKey: ReqKey, currentAllocatedChannels: Map[Channel, ReqKey],
                     idleConnectionsMap: Map[ReqKey, immutable.Queue[Connection]]) =  {
    val currMap = if (currentAllocatedChannels.contains(connection.channel)){
      currentAllocatedChannels.removed(connection.channel)
    } else currentAllocatedChannels
    println(s"${connection.channel.id()} === CURRMAP BEFORE: $currentAllocatedChannels CURRMAP AFTER: $currMap")

    val idleMap =
      if (idleConnectionsMap.get(reqKey).isEmpty) Map(reqKey -> immutable.Queue(connection))
      else {
        val q = idleConnectionsMap(reqKey)
        val isPresent = !q.filter(_.channel.id() == connection.channel.id()).isEmpty
        println(s"q: $q contains $connection ? ${q.contains(connection)} $isPresent")
        if (isPresent) idleConnectionsMap
        else Map(reqKey -> q.enqueue(connection))
      }

    ClientConnectionStateData(None, currMap, idleMap)
  }

//  def getActiveConnections: Task[Int] = for {
//    alloc   <- connectionState.currentAllocatedChannels.get
////    _       <- ZIO.effect(println(s"alloc size: ${alloc.size}"))
//    idleMap <- connectionState.idleConnectionsMap.get
//    idle = idleMap.values.foldLeft(0) { (acc, q) => acc + q.size }
////    _ <- ZIO.effect(println(s"idle size: $idleMap ${idle}"))
//  } yield (alloc.size + idle)

//    def incrementConnection: Unit = ???
//    def decrementConnection = ???
//    def isConnectionExpired = ???
//    def isConnectionWithinLimits = ???
//    def addConnectionToIdleQ = ???
//    def addConnectionToWaitQ = ???
//
//    def releaseConnection = ???
//    def shutdownConnectionManager = ???
//
//    def getActiveConnectionsForReqKey(reqKey: ReqKey): Task[Int] = ???
//    def getIdleConnections: Task[Int] = ???
//    def getIdleConnectionsForReqKey(reqKey: ReqKey): Task[Int] = ???

}

object ClientConnectionManager {}
