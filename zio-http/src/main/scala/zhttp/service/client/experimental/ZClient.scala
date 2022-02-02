package zhttp.service.client.experimental

import io.netty.bootstrap.Bootstrap
import zhttp.service.client.experimental.transport.Transport
//import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.handler.codec.http._
//import zhttp.http._
//import zhttp.service.HttpMessageCodec
//import zhttp.service.client.experimental.ZClient.Config
import zio.duration.Duration
import zio.{Task}

import java.net.{InetAddress, InetSocketAddress}
import scala.collection.mutable
import scala.concurrent.duration.DurationInt

trait ZClient[-R, +E] { self =>

  import ZClient._

  def ++[R1 <: R, E1 >: E](other: ZClient[R1, E1]): ZClient[R1, E1] =
    Concat(self, other)

  private def settings(s: Config = Config()): Config = self match {
    case Concat(self, other)                        => other.settings(self.settings(s))
    case Address(address)                           => s.copy(address = address)
    case TransportConfig(transport)                 => s.copy(transport = transport)
    case Threads(threads)                           => s.copy(threads = threads)
    case ResponseHeaderTimeout(rht)                 => s.copy(responseHeaderTimeout = rht)
    case IdleTimeout(idlt)                          => s.copy(idleTimeout = idlt)
    case RequestTimeout(rqt)                        => s.copy(requestTimeout = rqt)
    case ConnectionTimeout(connt)                   => s.copy(connectionTimeout = connt)
    case UserAgent(ua)                              => s.copy(userAgent = ua)
    case MaxTotalConnections(maxTotConn)            => s.copy(maxTotalConnections = maxTotConn)
    case MaxWaitQueueLimit(mwql)                    => s.copy(maxWaitQueueLimit = mwql)
    case MaxConnectionsPerRequestKey(maxConnPerReq) => s.copy(maxConnectionsPerRequestKey = maxConnPerReq)
    // case _ To be deleted
    case _                                          => s.copy(threads = 2)
  }

  def make(implicit
    ev: E <:< Throwable,
  ): Task[DefaultZClient] =
    ZClient.make(self.asInstanceOf[ZClient[R, Throwable]])

}

object ZClient {
  type UClient = ZClient[Any, Nothing]
  private[zhttp] final case class Config(
    address: InetSocketAddress = new InetSocketAddress(8080),
    transport: Transport = Transport.Auto,
    threads: Int = 0,
    responseHeaderTimeout: Duration =
      Duration.Infinity, // duration between the submission of request and the completion of the response header
    // Does not include time to read the response body
    idleTimeout: Duration = Duration.fromScala(1.minute),
    requestTimeout: Duration = Duration.fromScala(1.minute),      //
    connectionTimeout: Duration = Duration.fromScala(10.seconds), //
    userAgent: Option[String] = Some("ZClient"),                  //
    maxTotalConnections: Int = 10,                                //
    maxWaitQueueLimit: Int = 256,                                 //
    maxConnectionsPerRequestKey: Int = 20,                        //
//    sslContext: ClientSSLOptions,      //

  )

  private final case class Concat[R, E](self: ZClient[R, E], other: ZClient[R, E]) extends ZClient[R, E]
  private final case class Address(address: InetSocketAddress)                     extends UClient
  private final case class TransportConfig(transport: Transport)                   extends UClient
  private final case class Threads(threads: Int)                                   extends UClient
  private final case class ResponseHeaderTimeout(rht: Duration)                    extends UClient
  private final case class IdleTimeout(idlt: Duration)                             extends UClient
  private final case class RequestTimeout(reqt: Duration)                          extends UClient
  private final case class ConnectionTimeout(connt: Duration)                      extends UClient
  private final case class UserAgent(ua: Option[String])                           extends UClient
  private final case class MaxTotalConnections(maxTotConn: Int)                    extends UClient
  private final case class MaxWaitQueueLimit(mwql: Int)                            extends UClient
  private final case class MaxConnectionsPerRequestKey(maxConnPerReq: Int)         extends UClient
//  private final case class SSLContext(ssl: ClientSSLOptions)                                   extends UClient

  def port(port: Int): UClient                            = ZClient.Address(new InetSocketAddress(port))
  def bind(port: Int): UClient                            = ZClient.Address(new InetSocketAddress(port))
  def bind(hostname: String, port: Int): UClient          = ZClient.Address(new InetSocketAddress(hostname, port))
  def bind(inetAddress: InetAddress, port: Int): UClient  = ZClient.Address(new InetSocketAddress(inetAddress, port))
  def bind(inetSocketAddress: InetSocketAddress): UClient = ZClient.Address(inetSocketAddress)

  def transport(transport: Transport): UClient = ZClient.TransportConfig(transport)
  def threads(threads: Int): UClient           = ZClient.Threads(threads)

  def responseHeaderTimeout(rht: Duration): UClient               = ZClient.ResponseHeaderTimeout(rht)
  def idleTimeout(idt: Duration): UClient                         = ZClient.IdleTimeout(idt)
  def requestTimeout(rqt: Duration): UClient                      = ZClient.RequestTimeout(rqt)
  def connectionTimeout(connT: Duration): UClient                 = ZClient.ConnectionTimeout(connT)
  def userAgent(ua: Option[String]): UClient                      = ZClient.UserAgent(ua)
  def maxTotalConnections(maxTotConn: Int): UClient               = ZClient.MaxTotalConnections(maxTotConn)
  def maxWaitQueueLimit(maxWQLt: Int): UClient                    = ZClient.MaxWaitQueueLimit(maxWQLt)
  def maxConnectionsPerRequestKey(maxConnPerReqKey: Int): UClient =
    ZClient.MaxConnectionsPerRequestKey(maxConnPerReqKey)

  def nio: UClient    = ZClient.TransportConfig(Transport.Nio)
  def epoll: UClient  = ZClient.TransportConfig(Transport.Epoll)
  def kQueue: UClient = ZClient.TransportConfig(Transport.KQueue)
  def uring: UClient  = ZClient.TransportConfig(Transport.URing)
  def auto: UClient   = ZClient.TransportConfig(Transport.Auto)

  def make[R](zClient: ZClient[R, Throwable]): Task[DefaultZClient] = {
    val settings = zClient.settings()
    for {
      channelFactory <- settings.transport.clientChannel
      eventLoopGroup <- settings.transport.eventLoopGroupTask(settings.threads)
      zExec          <- zhttp.service.HttpRuntime.default[Any]

      clientBootStrap = new Bootstrap()
        .channelFactory(channelFactory)
        .group(eventLoopGroup)
      connRef <- zio.Ref.make(
        mutable.Map.empty[InetSocketAddress, Channel],
      )
      execPromiseRef = mutable.Map.empty[Channel, (zio.Promise[Throwable, Resp], FullHttpRequest)]
      connManager    = ZConnectionManager(connRef, execPromiseRef, clientBootStrap, zExec)
      clientImpl     = DefaultZClient(settings, connManager)
    } yield {
      clientImpl
    }
  }
}

//case class DefaultZClient(
//  settings: Config,
//  connectionManager: ZConnectionManager,
//) extends HttpMessageCodec {
//  def run(req: ReqParams): Task[Resp] =
//    for {
//      jReq    <- Task(encodeClientParams(HttpVersion.HTTP_1_1, req))
//      channel <- connectionManager.fetchConnection(jReq)
//      prom    <- zio.Promise.make[Throwable, Resp]
//      _       <- ZIO.effect(connectionManager.currentExecRef += (channel -> (prom, jReq)))
//      _       <- ZIO.effect { channel.pipeline().fireChannelActive() }
//      resp    <- prom.await
//    } yield resp
//
//  def run(req: Request): Task[Resp] =
//    for {
//      jReq    <- encodeClientParams(req)
//      channel <- connectionManager.fetchConnection(jReq)
//      prom    <- zio.Promise.make[Throwable, Resp]
//      _       <- ZIO.effect(connectionManager.currentExecRef += (channel -> (prom, jReq)))
//      _       <- ZIO.effect { channel.pipeline().fireChannelActive() }
//      resp    <- prom.await
//    } yield resp
//
//  def run(str: String): Task[Resp] = {
//    for {
//      url <- ZIO.fromEither(URL.fromString(str))
//      req = ReqParams(url = url)
//      res <- run(req)
//    } yield res
//  }
//
//  def encodeClientParams(jVersion: HttpVersion, req: ReqParams): FullHttpRequest = {
//    val method      = req.method.asHttpMethod
//    val uri         = req.url.encode
//    val content     = req.getBodyAsString match {
//      case Some(text) => Unpooled.copiedBuffer(text, HTTP_CHARSET)
//      case None       => Unpooled.EMPTY_BUFFER
//    }
//    val headers     = req.getHeaders.encode.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
//    val writerIndex = content.writerIndex()
//    if (writerIndex != 0) {
//      headers.set(HttpHeaderNames.CONTENT_LENGTH, writerIndex.toString())
//    }
//    val jReq        = new DefaultFullHttpRequest(jVersion, method, uri, content)
//    jReq.headers().set(headers)
//
//    jReq
//  }
//
//  def encodeClientParams(req: Request): Task[FullHttpRequest] = {
//    val jVersion = HttpVersion.HTTP_1_1
//    val method      = req.method.asHttpMethod
//    val uri         = req.url.encode
//    for {
//      reqContent <- req.getBodyAsString
//      content = Unpooled.copiedBuffer(reqContent,HTTP_CHARSET)
//    } yield (new DefaultFullHttpRequest(jVersion, method, uri, content))
//  }
//
////  private def asyncRequest(): Unit = {
//////    try {
//////      //        Thread.sleep(13000): Unit
//////    } catch {
//////      case _: Throwable =>
//////        if (req.refCnt() > 0) {
//////          req.release(req.refCnt()): Unit
//////        }
//////    }
////  }
//
//}
