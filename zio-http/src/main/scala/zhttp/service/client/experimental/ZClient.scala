package zhttp.service.client.experimental

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.{ChannelFuture, ChannelFutureListener}
import io.netty.handler.codec.http._
import zio.{Promise, Task, ZIO}
import zhttp.http._
import zhttp.service.HttpMessageCodec
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.experimental.ZClient.Config

import java.net.{InetAddress, InetSocketAddress}
import scala.collection.mutable

trait ZClient[-R, +E] { self =>

  import ZClient._

  def ++[R1 <: R, E1 >: E](other: ZClient[R1, E1]): ZClient[R1, E1] =
    Concat(self, other)

  private def settings(s: Config = Config()): Config = self match {
    case Concat(self, other)        => other.settings(self.settings(s))
    case Address(address)           => s.copy(address = address)
    case TransportConfig(transport) => s.copy(transport = transport)
    case Threads(threads)           => s.copy(threads = threads)
    case _                          => s.copy(threads = 2)
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
  )

  private final case class Concat[R, E](self: ZClient[R, E], other: ZClient[R, E]) extends ZClient[R, E]
  private final case class Address(address: InetSocketAddress)                     extends UClient
  private final case class TransportConfig(transport: Transport)                   extends UClient
  private final case class Threads(threads: Int)                                   extends UClient

  def port(port: Int): UClient                            = ZClient.Address(new InetSocketAddress(port))
  def bind(port: Int): UClient                            = ZClient.Address(new InetSocketAddress(port))
  def bind(hostname: String, port: Int): UClient          = ZClient.Address(new InetSocketAddress(hostname, port))
  def bind(inetAddress: InetAddress, port: Int): UClient  = ZClient.Address(new InetSocketAddress(inetAddress, port))
  def bind(inetSocketAddress: InetSocketAddress): UClient = ZClient.Address(inetSocketAddress)

  def transport(transport: Transport): UClient = ZClient.TransportConfig(transport)
  def threads(threads: Int): UClient           = ZClient.Threads(threads)

  def nio: UClient    = ZClient.TransportConfig(Transport.Nio)
  def epoll: UClient  = ZClient.TransportConfig(Transport.Epoll)
  def kQueue: UClient = ZClient.TransportConfig(Transport.KQueue)
  def uring: UClient  = ZClient.TransportConfig(Transport.URing)
  def auto: UClient   = ZClient.TransportConfig(Transport.Auto)

  def make[R](
               zClient: ZClient[R, Throwable],
  ): Task[DefaultZClient] = {
    val settings = zClient.settings()
    for {
      channelFactory <- settings.transport.clientChannel
      eventLoopGroup <- settings.transport.eventLoopGroupTask(settings.threads)
//      zExec <- zhttp.service.HttpRuntime.default[Any]

      clientBootStrap = new Bootstrap()
        .channelFactory(channelFactory)
        .group(eventLoopGroup)

      _ <- ZIO.effect(println(s"BOOTSTRAP DONE"))
      connRef <- zio.Ref.make(
        mutable.Map.empty[InetSocketAddress, Promise[Throwable,Resp]]
      )

      clientImpl = DefaultZClient(clientBootStrap, settings, connRef)
    } yield {
      clientImpl
    }
  }
}
case class DefaultZClient(boo: Bootstrap
                          , settings: Config
                          , connRef: zio.Ref[mutable.Map[InetSocketAddress, Promise[Throwable,Resp]]]
                         )
    extends HttpMessageCodec {
  def run(req: ReqParams): Task[Resp] =
    for {
      jReq <- Task(encodeClientParams(HttpVersion.HTTP_1_1, req))
//      _       <- Task(asyncRequest()).catchAll(cause => promise.fail(cause))
      pro <- fetchConnection(jReq,req)
      _ <- ZIO.effect(println(s"Now promise. await for $req"))
      res <- pro.await
    } yield res

  def run(str: String): Task[Resp] ={
    for {
      url <- ZIO.fromEither(URL.fromString(str))
      req = ReqParams(url = url)
      res <- run(req)
    } yield (res)
  }

  def buildChannel[R](jReq: FullHttpRequest, scheme: String) = {
    for {
      promise <- Promise.make[Throwable, Resp]
      zExec          <- zhttp.service.HttpRuntime.default[R]
      hand = ZClientInboundHandler(zExec, jReq, promise)
      _ <- ZIO.effect(println(s"HANDLER INITIALIZED"))

      init   = ZClientChannelInitializer(hand, scheme, ClientSSLOptions.DefaultSSL)

      chf = boo.handler(init).connect(settings.address)
      _ <- ZIO
        .effect(
          chf.addListener(new ChannelFutureListener() {
            override def operationComplete(future: ChannelFuture): Unit = {
              val channel = future.channel()
              println(s"CONNECTED USING CHH ID: ${channel.id()}")
              if (!future.isSuccess()) {
                println(s"error: ${future.cause().getMessage}")
                future.cause().printStackTrace()
              } else {
                println("sent request");
              }
            }
          }): Unit,
        )

    } yield (promise)
  }

  def fetchConnection(jReq: FullHttpRequest, req: ReqParams): Task[Promise[Throwable,Resp]] = {
    for {
      mp <- connRef.get
      _ <- ZIO.effect(println(s"CONNECTION MAP : $mp"))
      host   = req.url.host
      port   = req.url.port.getOrElse(80) match {
        case -1   => 80
        case port => port
      }
      _ <- ZIO.effect(println(s"extracted HOST: $host extracted PORT: $port"))
      inetSockAddress <- host match {
        case Some(h) => Task.succeed(new InetSocketAddress (h, port))
        case _ => Task.fail(new Exception("error getting host"))
      }
      conn <- mp.get(inetSockAddress) match {
        case Some(c) =>
          println(s"CONN FOUND REUSING IT: $c")
          Task.succeed(c)
        case _ =>
          println(s"CONN NOT FOUND CREATING NEW")
          buildChannel(jReq,"http")
      }
      _ <- connRef.update { m =>
        m +=(inetSockAddress -> conn)
      }
    } yield (conn)

  }

  def encodeClientParams(jVersion: HttpVersion, req: ReqParams): FullHttpRequest = {
    val method      = req.method.asHttpMethod
    val uri         = req.url.asString
    val content     = req.getBodyAsString match {
      case Some(text) => Unpooled.copiedBuffer(text, HTTP_CHARSET)
      case None       => Unpooled.EMPTY_BUFFER
    }
    val headers     = req.getHeaders.encode.set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE)
    val writerIndex = content.writerIndex()
    if (writerIndex != 0) {
      headers.set(HttpHeaderNames.CONTENT_LENGTH, writerIndex.toString())
    }
    val jReq        = new DefaultFullHttpRequest(jVersion, method, uri, content)
    jReq.headers().set(headers)

    jReq
  }

//  private def asyncRequest(): Unit = {
////    try {
////      //        Thread.sleep(13000): Unit
////    } catch {
////      case _: Throwable =>
////        if (req.refCnt() > 0) {
////          req.release(req.refCnt()): Unit
////        }
////    }
//  }

}


