package zhttp.service

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.{DefaultFullHttpRequest, FullHttpRequest, HttpHeaderNames}
import zhttp.http.Request
import zio.Task

trait EncodeRequest {

  /**
   * Converts client params to JFullHttpRequest
   */
  def encode(req: Request): Task[FullHttpRequest] =
    req.body.asChunk.map { chunk =>
      val content  = Unpooled.wrappedBuffer(chunk.toArray)
      val method   = req.method.toJava
      val jVersion = req.version.toJava

      // As per the spec, the path should contain only the relative path.
      // Host and port information should be in the headers.
      val path = req.url.relative.encode

      val encodedReqHeaders = req.headers.encode

      val headers = req.url.host match {
        case Some(host) => encodedReqHeaders.set(HttpHeaderNames.HOST, req.url.port.fold(host)(port => s"$host:$port"))
        case _          => encodedReqHeaders
      }

      val writerIndex = content.writerIndex()
      headers.set(HttpHeaderNames.CONTENT_LENGTH, writerIndex.toString)

      // TODO: we should also add a default user-agent req header as some APIs might reject requests without it.
      val jReq = new DefaultFullHttpRequest(jVersion, method, path, content)
      jReq.headers().set(headers)

      jReq
    }
}
