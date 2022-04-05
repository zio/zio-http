package zhttp.service
import io.netty.bootstrap.Bootstrap
import io.netty.channel.pool.{ChannelPoolHandler, FixedChannelPool}
import io.netty.channel.{Channel, ChannelFactory => JChannelFactory, EventLoopGroup => JEventLoopGroup}
import io.netty.handler.codec.http.{FullHttpRequest, HttpClientCodec, HttpObjectAggregator}
import io.netty.util.concurrent.Future
import zhttp.http._
import zhttp.service
import zhttp.service.client.ClientInboundHandler
import zio.{Promise, ZIO}

final class ClientWithPool[R](
  host: String,
  port: Int,
  cf: JChannelFactory[Channel],
  group: JEventLoopGroup,
  rtm: HttpRuntime[R],
) extends HttpMessageCodec {

  private val bootstrap =
    new Bootstrap().group(group).channelFactory(cf).remoteAddress(host, port)

  private val pool = new FixedChannelPool(
    bootstrap,
    new ChannelPoolHandler {
      override def channelReleased(ch: Channel): Unit = {
        println(s"chanel release: ${ch.toString} ")

      }
      override def channelAcquired(ch: Channel): Unit = {
        println(s"chanel aquired: ${ch.toString}")
      }
      override def channelCreated(ch: Channel): Unit  = {
        println(s"chanel created: ${ch.toString}")
        val pipeline = ch.pipeline()
        pipeline.addLast(HTTP_CLIENT_CODEC, new HttpClientCodec)

        // ObjectAggregator is used to work with FullHttpRequests and FullHttpResponses
        // This is also required to make WebSocketHandlers work
        pipeline.addLast(HTTP_OBJECT_AGGREGATOR, new HttpObjectAggregator(Int.MaxValue)): Unit

      }
    },
    10,
    50,
  )

  private def unsafeRequest(
    jReq: FullHttpRequest,
    promise: Promise[Throwable, Response],
    isWebSocket: Boolean,
  ): Unit = {
    println(s"Pool content: [${pool.acquiredChannelCount()}]")
    pool.acquire().addListener { (f: Future[Channel]) =>
      if (f.isSuccess) {
        val channel = f.getNow
        if (channel.pipeline().toMap.containsKey(CLIENT_INBOUND_HANDLER)) {
          channel.pipeline().remove(CLIENT_INBOUND_HANDLER)
        }
        channel.pipeline().addLast(CLIENT_INBOUND_HANDLER, new ClientInboundHandler(rtm, jReq, promise, isWebSocket))
        channel.writeAndFlush(jReq).addListener { (_: Future[Channel]) =>
          pool.release(channel): Unit
        }: Unit
      }
    }: Unit
  }

  def release() = pool.close()

  def requestWithPool(
    url: String,
    method: Method = Method.GET,
    headers: Headers = Headers.empty,
    content: HttpData = HttpData.empty,
    //   ssl: ClientSSLOptions = ClientSSLOptions.DefaultSSL,
    isWebSocket: Boolean,
  ): ZIO[EventLoopGroup with ChannelFactory, Throwable, Response] =
    for {
      uri     <- ZIO.fromEither(URL.fromString(url))
      promise <- Promise.make[Throwable, Response]
      jReq    <- encode(
        Request(Version.Http_1_1, method, uri, headers ++ Headers.connection(HeaderValues.keepAlive), data = content),
      )
      _       <- ZIO
        .effect(
          unsafeRequest(jReq, promise, isWebSocket),
        )
        .catchAll(cause => promise.fail(cause))
      res     <- promise.await

    } yield res

}

object ClientWithPool {

  def make[R](
    host: String,
    port: Int,
  ): ZIO[R with EventLoopGroup with ChannelFactory, Nothing, ClientWithPool[R]] = for {
    cf <- ZIO.service[JChannelFactory[Channel]]
    el <- ZIO.service[JEventLoopGroup]
    zx <- HttpRuntime.default[R]
  } yield new service.ClientWithPool(host, port, cf, el, zx)

}
