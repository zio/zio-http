package zhttp.service.server

import io.netty.handler.codec.http.HttpObjectDecoder.{
  DEFAULT_MAX_CHUNK_SIZE,
  DEFAULT_MAX_HEADER_SIZE,
  DEFAULT_MAX_INITIAL_LINE_LENGTH,
}
import io.netty.handler.codec.http.HttpServerKeepAliveHandler
import zhttp.core._
import zhttp.service._

/**
 * Initializes the netty channel with default handlers
 */
@JSharable
final case class ServerChannelInitializer(httpH: JChannelHandler, hhtpMfdH: JChannelHandler, maxSize: Int)
    extends JChannelInitializer[JChannel] {
  override def initChannel(channel: JChannel): Unit = {
    channel
      .pipeline()
      // Without increasing the DEFAULT_MAX_CHUNK_SIZE uploads are rather slow. Probably because Netty is chunking up messages
      // itself. With a file upload, that causes lots of overhead. A factor >8 (resulting in 65K chunks) is ignored so it seems.
      .addLast(
        SERVER_CODEC_HANDLER,
        new JHttpServerCodec(DEFAULT_MAX_INITIAL_LINE_LENGTH, DEFAULT_MAX_HEADER_SIZE, DEFAULT_MAX_CHUNK_SIZE * 8),
      )
      .addLast(HTTP_KEEPALIVE_HANDLER, new HttpServerKeepAliveHandler)
      // This handler should always be in front of the `JHttpObjectAggregator` or we won't be able to
      // decode multipart/form-data requests correctly.
      .addLast(MULTIPART_FORMDATA_HANDLER, hhtpMfdH)
      .addLast(OBJECT_AGGREGATOR, new JHttpObjectAggregator(maxSize))
      .addLast(HTTP_REQUEST_HANDLER, httpH)
    ()
  }
}
