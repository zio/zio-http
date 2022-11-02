package zio.http.netty

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http._
import zio._
import zio.http._

import scala.collection.concurrent.TrieMap
private[zio] object NettyResponseEncoder {

  private val frozenCache = TrieMap.empty[Response, HttpResponse]

  def encode(response: Response)(implicit unsafe: Unsafe): HttpResponse = {

    if (response.frozen) {
      val encodedResponse = frozenCache.get(response)

      encodedResponse match {
        case Some(encoded) => encoded

        case None =>
          val encoded = doEncode(response)
          frozenCache.put(response, encoded)
          encoded
      }
    } else {
      doEncode(response)
    }

  }

  private def doEncode(response: Response)(implicit unsafe: Unsafe): HttpResponse = {
    val body             = response.body
    val jHeaders         = response.headers.encode
    val hasContentLength = jHeaders.contains(HttpHeaderNames.CONTENT_LENGTH)

    if (body.isComplete) {

      val jContent  = Unpooled.wrappedBuffer(body.unsafeAsChunk.toArray)
      val jResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, response.status.asJava, jContent, false)

      // TODO: Unit test for this
      // Client can't handle chunked responses and currently treats them as a FullHttpResponse.
      // Due to this client limitation it is not possible to write a unit-test for this.
      // Alternative would be to use sttp client for this use-case.
      if (!hasContentLength) jHeaders.set(HttpHeaderNames.CONTENT_LENGTH, jContent.readableBytes())
      jResponse.headers().add(jHeaders)
      jResponse

    } else {

      if (!hasContentLength) jHeaders.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
      new DefaultHttpResponse(HttpVersion.HTTP_1_1, response.status.asJava, jHeaders)
    }

  }

}
