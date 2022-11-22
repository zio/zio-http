package zio.http.netty

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http._
import zio._
import zio.http._

import java.util.concurrent.ConcurrentHashMap

private[zio] object NettyResponseEncoder {

  private val frozenCache    = new ConcurrentHashMap[Response, HttpResponse]()
  private val frozenZioCache = new ConcurrentHashMap[Response, UIO[HttpResponse]]()

  def encode(response: Response): ZIO[Any, Throwable, HttpResponse] = {
    val body = response.body
    if (body.isComplete) {
      val cachedValue = frozenZioCache.get(response)
      if (cachedValue != null) cachedValue
      else
        body.asArray.flatMap(bytes => ZIO.attemptUnsafe(implicit unsafe => fastEncode(response, bytes)))
    } else {
      val jHeaders         = response.headers.encode
      val hasContentLength = jHeaders.contains(HttpHeaderNames.CONTENT_LENGTH)
      if (!hasContentLength) jHeaders.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
      ZIO.succeedNow(new DefaultHttpResponse(HttpVersion.HTTP_1_1, response.status.asJava, jHeaders))
    }
  }

  def fastEncode(response: Response, bytes: Array[Byte])(implicit unsafe: Unsafe): HttpResponse =
    if (response.frozen) {
      val encodedResponse = frozenCache.get(response)

      if (encodedResponse != null)
        encodedResponse
      else {
        val encoded    = doEncode(response, bytes)
        val encodedZio = ZIO.succeedNow(encoded)
        frozenZioCache.put(response, encodedZio)
        frozenCache.put(response, encoded)
        encoded
      }
    } else doEncode(response, bytes)

  private def doEncode(response: Response, bytes: Array[Byte]): HttpResponse = {
    val jHeaders         = response.headers.encode
    val hasContentLength = jHeaders.contains(HttpHeaderNames.CONTENT_LENGTH)

    val jContent  = Unpooled.wrappedBuffer(bytes)
    val jResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, response.status.asJava, jContent, false)

    // TODO: Unit test for this
    // Client can't handle chunked responses and currently treats them as a FullHttpResponse.
    // Due to this client limitation it is not possible to write a unit-test for this.
    // Alternative would be to use sttp client for this use-case.
    if (!hasContentLength) jHeaders.set(HttpHeaderNames.CONTENT_LENGTH, jContent.readableBytes())
    jResponse.headers().add(jHeaders)
    jResponse

  }

}
