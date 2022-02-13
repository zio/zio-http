package zhttp.service.client.transport

import io.netty.bootstrap.Bootstrap
import io.netty.channel.Channel
import io.netty.handler.codec.http.FullHttpRequest
import zhttp.http.HeaderNames
import zhttp.service.Client.{ClientRequest, ClientResponse}
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.handler.{EnhancedClientChannelInitializer, EnhancedClientInboundHandler}
import zhttp.service.client.model.ClientConnectionState.ReqKey
import zhttp.service.client.model.{ClientConnectionState, Connection, Timeouts}
import zio.stm.TRef
import zio.{Promise, Task, ZIO}

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
    connection <- {
      getConnectionForRequestKey(jReq, promise, reqKey, isWebSocket, isSSL).flatten
    }
//    _       <- attachHandler(connection, jReq, promise)
  } yield connection

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
  def getConnectionForRequestKey(
    jReq: FullHttpRequest,
    promise: Promise[Throwable, ClientResponse],
    reqKey: ReqKey,
    isWebSocket: Boolean,
    isSSL: Boolean,
  ) = (for {
    // check if idle connection exists for this reqKey
    idleChannelOpt <- getIdleChannelFromQueue(reqKey)

    value <- ( for {
      tRef <- TRef.make(triggerConn(idleChannelOpt,jReq,promise,reqKey,isWebSocket,isSSL))
      v <- tRef.get
    } yield v).commit
    // if it is None, it means no idle channel exists for this request key
//    connection        <- idleChannelOpt match {
//      case Some(ch) =>
////        Task {
//          println(s"IDLE CHANNEL FOUND REUSING ......$ch")
//          if (ch != null) Task{
//            (ch.copy(isReuse = true))
//          }
//          else buildChannel(jReq: FullHttpRequest, promise: Promise[Throwable, ClientResponse], reqKey, isWebSocket, isSSL)
//        //}
//      case None     =>
//        buildChannel(jReq: FullHttpRequest, promise: Promise[Throwable, ClientResponse], reqKey, isWebSocket, isSSL)
//    }
//    _       <- attachHandler(connection, jReq, promise)
//    _       <- addChannelToIdleQueue(reqKey, connection)
  } yield value)

  def triggerConn(idleChannelOpt: Option[Connection],
    jReq: FullHttpRequest,
  promise: Promise[Throwable, ClientResponse],
  reqKey: ReqKey,
  isWebSocket: Boolean,
  isSSL: Boolean,
                 ) = for {
        connection        <- idleChannelOpt match {
          case Some(ch) =>
    //        Task {
              println(s"IDLE CHANNEL FOUND REUSING ......$ch")
              if (ch != null && ch.isReuse) Task{
                (ch.copy(isReuse = true, isFree = false))
              }
              else buildChannel(jReq: FullHttpRequest, promise: Promise[Throwable, ClientResponse], reqKey, isWebSocket, isSSL)
            //}
          case None     =>
            buildChannel(jReq: FullHttpRequest, promise: Promise[Throwable, ClientResponse], reqKey, isWebSocket, isSSL)
        }
        _       <- attachHandler(connection, jReq, promise)
        _       <- addChannelToIdleQueue(reqKey, connection)

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
    jReq: FullHttpRequest,
    promise: Promise[Throwable, ClientResponse],
    reqKey: ReqKey,
    isWebSocket: Boolean = false,
    isSSL: Boolean = false,
  ): Task[Connection] =
    for {
      init <- ZIO.effect(
        EnhancedClientChannelInitializer(
          EnhancedClientInboundHandler(zExec, jReq, promise),
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
    } yield Connection(c,false, false)

  /*
   mostly kept for debugging purposes
   or if we need to do something during creation lifecycle.
   */
  def attachHandler(
                     connection: Connection,
                     jReq: FullHttpRequest,
                     promise: Promise[Throwable, ClientResponse],
  ) = {
    ZIO
      .effect(
        connection.channel.newSucceededFuture().addListener(new io.netty.channel.ChannelFutureListener() {

          override def operationComplete(future: io.netty.channel.ChannelFuture): Unit = {
            if (!future.isSuccess()) {
              println(s"error: ${future.cause().getMessage}")
              future.cause().printStackTrace()
            } else {
              if (
//                chf.channel().pipeline().get(zhttp.service.CLIENT_INBOUND_HANDLER) != null
                connection.isReuse
              ) {
                if (connection.channel.pipeline().get(zhttp.service.CLIENT_INBOUND_HANDLER) != null)
                  connection.channel.pipeline().remove(zhttp.service.CLIENT_INBOUND_HANDLER)
              }
//              val jr = if (connection.isReuse) jReq else jReq.retain()
              connection.channel
                .pipeline()
                .addLast(zhttp.service.CLIENT_INBOUND_HANDLER, EnhancedClientInboundHandler(zExec, jReq, promise)): Unit
              println(s"REUSING ?????? for ${connection.channel.id()} ---> ${connection.isReuse}")
               connection.channel.pipeline().fireChannelActive()
              ()
            }
          }
        }): Unit,
      )
  }

  def getActiveConnections: Task[Int] = for {
    alloc   <- connectionState.currentAllocatedChannels.get
//    _       <- ZIO.effect(println(s"alloc size: ${alloc.size}"))
    idleMap <- connectionState.idleConnectionsMap.get
    idle = idleMap.values.foldLeft(0) { (acc, q) => acc + q.size }
//    _ <- ZIO.effect(println(s"idle size: $idleMap ${idle}"))
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

  def getIdleChannelFromQueue(reqKey: ReqKey) = for {
    idleMap <- connectionState.idleConnectionsMap.get
    idleQOpt = idleMap.get(reqKey)
    // if no queue exists for this req key add an empty queue
    _ <- Task {
      if (idleQOpt.isEmpty) {
//        println(s"NO QUEUE EXISTS FOR REQ KEY : $reqKey ADDING AN EMPTY QUEUE $idleQOpt")
        connectionState.idleConnectionsMap.update(_ => Map(reqKey -> mutable.Queue.empty[Connection]))
      }
    }
    chOpt = idleQOpt.flatMap{ q =>
      if (q.isEmpty) None else Some(q.dequeue())
    }
  } yield chOpt

  def addChannelToIdleQueue(reqKey: ReqKey, connection: Connection) = for {
    idleMap <- connectionState.idleConnectionsMap.get
    idleQueue = idleMap.get(reqKey)
    _ <- idleQueue.fold(
      connectionState.idleConnectionsMap.update(m => m + (reqKey -> mutable.Queue.empty[Connection])),
    ) { q =>
      connectionState.idleConnectionsMap.update { m =>
        q.enqueue(connection.copy(isFree = true))
        m + (reqKey -> q)
      }
    }
//    _ <- ZIO.effect(println(s"IDLE QUEUE for REQKEY: $reqKey AFTER ENQUEUEING ${connectionState.idleConnectionsMap}"))
  } yield idleMap
}

object ClientConnectionManager {}
