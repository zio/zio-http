package zhttp.service

import java.net.InetSocketAddress

import io.netty.bootstrap.Bootstrap
import io.netty.channel.{Channel, ChannelFactory => JChannelFactory, EventLoopGroup => JEventLoopGroup}
import io.netty.handler.codec.http.{FullHttpRequest, FullHttpResponse, HttpVersion}
import zhttp.http.URL.Location
import zhttp.http._
import zhttp.service
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zhttp.service.client._
import zio.stm.TRef
import zio.{Promise, Task, ZIO}

final case class Client(zx: UnsafeChannelExecutor[Any], cf: JChannelFactory[Channel], el: JEventLoopGroup,st:TRef[Int])
    extends HttpMessageCodec {
  private def asyncRequest(
    req: Request,
    jReq: FullHttpRequest,
    promise: Promise[Throwable, FullHttpResponse],
    sslOption: ClientSSLOptions,
    enableHttp2: Boolean,
  ): Task[Unit] =
    ChannelFuture.unit {
      val scheme = req.url.kind match {
        case Location.Relative               => ""
        case Location.Absolute(scheme, _, _) => scheme.asString
      }
      val read   = ClientHttpChannelReader(jReq, promise)
      val hand   = HttpClientResponseHandler(zx, read)
      val host   = req.url.host

      val port   = req.url.port.getOrElse(80) match {
        case -1   => 80
        case port => port
      }
      val hand2  = Http2ClientResponseHandler(zx)
      val ini    = Http2ClientInitializer(sslOption, hand, hand2, scheme, enableHttp2, jReq)
      if (enableHttp2) hand2.put(1, promise)

      val jboo = new Bootstrap().channelFactory(cf).group(el).handler(ini)
      if (host.isDefined) jboo.remoteAddress(new InetSocketAddress(host.get, port))

      jboo.connect()

    }

  def request(request: Request, enableHttp2: Boolean,sslOption: ClientSSLOptions = ClientSSLOptions.DefaultSSL): Task[UHttpResponse] =
    for {
      promise <- Promise.make[Throwable, FullHttpResponse]
      jReq = encodeRequest(HttpVersion.HTTP_1_1, request)
      _    <- asyncRequest(request, jReq, promise, sslOption, enableHttp2).catchAll(cause => promise.fail(cause)).fork
      jRes <- promise.await
      _ = println("printing the jRes from promise")
      _=println(jRes)
      res  <- ZIO.fromEither(decodeJResponse(jRes))
    } yield res

  def multiplexer (sslOption: ClientSSLOptions,scheme:String,host:String,port:Int)={
  val hand2  = Http2ClientResponseHandler(zx)
    val ini    = Http2MultiplexClientInitializer(sslOption,scheme,hand2)
  val  jboo = new Bootstrap().channelFactory(cf).group(el).handler(ini)
    jboo.remoteAddress(new InetSocketAddress(host, port))
val cha=jboo.connect().awaitUninterruptibly().channel()
   val s= ini.getSettingsHandler

    if (s.multiplexpromise()) Right{
      (r:Request,s:Int)=>  for {
          p<-Promise.make[Throwable, FullHttpResponse]

          _<-ZIO.succeed(hand2.put(s,p))
          _=cha.writeAndFlush(encodeRequest(HttpVersion.HTTP_1_1,r,s))
        r<-p.await
        a<-(ZIO.fromEither(decodeJResponse(r)))
      } yield a
    } else Left(false)
  }
}

object Client {
  def make: ZIO[EventLoopGroup with ChannelFactory, Nothing, Client] = for {
    cf <- ZIO.access[ChannelFactory](_.get)
    el <- ZIO.access[EventLoopGroup](_.get)
    zx <- UnsafeChannelExecutor.make[Any]()
    st<-TRef.make(2).commit
  } yield service.Client(zx, cf, el,st)

  def request(
    url: String,
    enableHttp2: Boolean,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] = for {
    url <- ZIO.fromEither(URL.fromString(url))
    res <- request(Method.GET -> url, enableHttp2 )
  } yield res
//1 replace this
  def request(
    url: String,
    sslOptions: ClientSSLOptions,
    enableHttp2: Boolean,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] = for {
    url <- ZIO.fromEither(URL.fromString(url))
    res <- request(Method.GET -> url, sslOptions,enableHttp2 )
  } yield res

  def request(
    url: String,
    headers: List[Header],
    enableHttp2: Boolean ,
    sslOptions: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    for {
      url <- ZIO.fromEither(URL.fromString(url))
      res <- request(Method.GET -> url, headers, sslOptions,enableHttp2)
    } yield res
//don't
  def request(
    endpoint: Endpoint,
    enableHttp2: Boolean,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    request(Request(endpoint), enableHttp2 )
//2.replace this
  def request(
    endpoint: Endpoint,
    sslOptions: ClientSSLOptions,
    enableHttp2: Boolean,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    request(Request(endpoint), sslOptions,enableHttp2 )
// don't
  def request(
    endpoint: Endpoint,
    headers: List[Header],
    sslOptions: ClientSSLOptions,
    enableHttp2: Boolean,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    request(Request(endpoint, headers), sslOptions,enableHttp2 )
//don't
  def request(
    req: Request,
    enableHttp2: Boolean,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    make.flatMap(_.request(req,enableHttp2))
//3.replace this
  def request(
    req: Request,
    sslOptions: ClientSSLOptions,
    enableHttp2: Boolean,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, UHttpResponse] =
    make.flatMap(_.request(req, enableHttp2 ,sslOptions))



  def multi(
               sslOptions: ClientSSLOptions,
             ) = make.map(_.multiplexer(sslOptions,"https","localhost",8090))

}
