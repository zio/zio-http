package zhttp.service.client.experimental

import io.netty.bootstrap.Bootstrap
import io.netty.buffer.{Unpooled}
import io.netty.channel.{ChannelFuture, ChannelFutureListener}
import io.netty.handler.codec.http._
import zhttp.http.URL.Location
import zhttp.http._
import zhttp.service.HttpMessageCodec
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client.experimental.{ReqParams, Resp, Transport, ZClientChannelInitializer, ZClientInboundHandler}
import zio.{ZManaged, _}

import java.net.{InetAddress, InetSocketAddress}

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

  def make(req: ReqParams)(implicit
    ev: E <:< Throwable,
  ): ZManaged[R, Throwable, DefaultZClient] =
    ZClient.make(self.asInstanceOf[ZClient[R, Throwable]], req)

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
               req: ReqParams,
  ): ZManaged[R, Throwable, DefaultZClient] = {
    val settings = zClient.settings()
    for {
      channelFactory <- ZManaged.fromEffect(settings.transport.clientChannel)
      eventLoopGroup <- settings.transport.eventLoopGroup(settings.threads)
      zExec          <- zhttp.service.HttpRuntime.default[R].toManaged_

      jReq = encodeClientParams(HttpVersion.HTTP_1_1, req)
      promise <- ZManaged.fromEffect(Promise.make[Throwable, Resp])
      hand = ZClientInboundHandler(zExec, jReq, promise)
      _ <- ZIO.effect(println(s"HANDLER INITIALIZED")).toManaged_

      scheme = req.url.kind match {
        case Location.Relative               => ""
        case Location.Absolute(scheme, _, _) => scheme.asString
      }
      init   = ZClientChannelInitializer(hand, scheme, ClientSSLOptions.DefaultSSL)

      clientBootStrap = new Bootstrap()
        .channelFactory(channelFactory)
        .group(eventLoopGroup)
        .handler(init)

      _ <- ZIO.effect(println(s"BOOTSTRAP DONE")).toManaged_

      chf = clientBootStrap.connect(settings.address)
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
                println("sent  request");
              }
            }
          }): Unit,
        )
        .toManaged_

      clientImpl = DefaultZClient(jReq, promise, chf)
    } yield {
      clientImpl
    }
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

}
case class DefaultZClient(req: FullHttpRequest, promise: Promise[Throwable, Resp], cf: ChannelFuture)
    extends HttpMessageCodec {
  def run: Task[Resp] =
    for {
//      _       <- Task(asyncRequest()).catchAll(cause => promise.fail(cause))
      res <- promise.await
    } yield res

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


