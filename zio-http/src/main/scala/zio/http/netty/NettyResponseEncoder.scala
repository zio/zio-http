package zio.http.netty

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http._
import zio._
import zio.http._
private[zio] object NettyResponseEncoder {

  private[zio] final case class NettyEncodedResponse(jResponse: HttpResponse)
      extends AnyVal
      with Response.EncodedResponse

  def encode(response: Response)(implicit unsafe: Unsafe): NettyEncodedResponse = {
    val encodedResponse = response.encodedResponse.get

    (response.frozen, encodedResponse) match {
      case (true, Some(encoded)) => encoded.asInstanceOf[NettyEncodedResponse]

      case (true, None) =>
        val encoded = doEncode(response)
        response.withEncodedResponse(Some(encoded))
        encoded
      case (false, _)   => doEncode(response)
    }
  }

  private def doEncode(response: Response)(implicit unsafe: Unsafe): NettyEncodedResponse = {
    val body             = response.body
    val jHeaders         = response.headers.encode
    val hasContentLength = jHeaders.contains(HttpHeaderNames.CONTENT_LENGTH)

    if (body.isComplete) {

      val jContent  = Unpooled.wrappedBuffer(body.asChunkUnsafe.toArray)
      val jResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, response.status.asJava, jContent, false)

      // TODO: Unit test for this
      // Client can't handle chunked responses and currently treats them as a FullHttpResponse.
      // Due to this client limitation it is not possible to write a unit-test for this.
      // Alternative would be to use sttp client for this use-case.
      if (!hasContentLength) jHeaders.set(HttpHeaderNames.CONTENT_LENGTH, jContent.readableBytes())
      jResponse.headers().add(jHeaders)
      NettyEncodedResponse(jResponse)

    } else {

      if (!hasContentLength) jHeaders.set(HttpHeaderNames.TRANSFER_ENCODING, HttpHeaderValues.CHUNKED)
      NettyEncodedResponse(new DefaultHttpResponse(HttpVersion.HTTP_1_1, response.status.asJava, jHeaders))
    }

  }

}
