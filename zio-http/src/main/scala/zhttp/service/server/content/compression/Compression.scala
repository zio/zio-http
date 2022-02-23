package zhttp.service.server.content.compression

import io.netty.buffer.ByteBuf
import io.netty.channel.embedded.EmbeddedChannel
import io.netty.handler.codec.http.{HttpContentEncoder, HttpObject, HttpVersion}
import io.netty.util.ReferenceCountUtil

import java.util.{List => JList}

trait Compression { self: HttpContentEncoder =>

  protected def isPassthru(version: HttpVersion, code: Int): Boolean =
    code < 200 || code == 204 || code == 304 || (version == HttpVersion.HTTP_1_0)

  protected def encode(eCh: EmbeddedChannel, buf: ByteBuf): ByteBuf     = ???
  protected def unsafeAdd(msg: HttpObject, out: JList[AnyRef]): Boolean = out.add(ReferenceCountUtil.retain(msg))
}
