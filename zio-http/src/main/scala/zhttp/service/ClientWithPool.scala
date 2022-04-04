package zhttp.service
import io.netty.bootstrap.Bootstrap
import io.netty.channel.{Channel, ChannelFactory => JChannelFactory, EventLoopGroup => JEventLoopGroup}
import io.netty.channel.pool.{ChannelPoolHandler, SimpleChannelPool}
import io.netty.handler.codec.http.{FullHttpRequest, HttpClientCodec, HttpObjectAggregator}
import io.netty.util.concurrent.Future
import zhttp.http.Response
import zhttp.service.client.ClientInboundHandler
import zio.Promise

final class ClientWithPool[R](
  host: String,
  port: Int,
  cf: JChannelFactory[Channel],
  group: JEventLoopGroup,
  rtm: HttpRuntime[R],
) extends HttpMessageCodec {

  private val bootstrap =
    new Bootstrap().group(group).channelFactory(cf).remoteAddress(host, port)

  private val pool = new SimpleChannelPool(
    bootstrap,
    new ChannelPoolHandler {
      override def channelReleased(ch: Channel): Unit = {
        println(s"chanel release: ${ch.toString}")

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
  )

  def unsafeRequest(
    jReq: FullHttpRequest,
    promise: Promise[Throwable, Response],
    isWebSocket: Boolean,
  ): Unit = {
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

}
