package zhttp.http

import io.netty.buffer.{ByteBuf, ByteBufUtil}
import io.netty.util.AsciiString
import zhttp.http.headers.HeaderExtension
import zio.stream.ZStream
import zio.{Chunk, Task, ZIO}

private[zhttp] trait HttpDataExtension[+A] extends HeaderExtension[A] { self: A =>
  private[zhttp] final def bodyAsByteBuf: Task[ByteBuf] = data.toByteBuf

  def data: HttpData

  /**
   * Decodes the content of request as a Chunk of Bytes
   */
  final def body: Task[Chunk[Byte]] =
    bodyAsByteArray.map(Chunk.fromArray)

  final def bodyAsByteArray: Task[Array[Byte]] =
    bodyAsByteBuf.flatMap(buf =>
      ZIO.attempt(ByteBufUtil.getBytes(buf)).ensuring(ZIO.succeed(buf.release(buf.refCnt()))),
    )

  /**
   * Decodes the content of request as CharSequence
   */
  final def bodyAsCharSequence: ZIO[Any, Throwable, CharSequence] =
    bodyAsByteArray.map { buf => new AsciiString(buf, false) }

  /**
   * Decodes the content of request as stream of bytes
   */
  final def bodyAsStream: ZStream[Any, Throwable, Byte] = data.toByteBufStream
    .mapZIO[Any, Throwable, Chunk[Byte]] { buf =>
      ZIO.attempt {
        val bytes = Chunk.fromArray(ByteBufUtil.getBytes(buf))
        buf.release(buf.refCnt())
        bytes
      }
    }
    .flattenChunks

  /**
   * Decodes the content of request as string
   */
  final def bodyAsString: Task[String] =
    bodyAsByteArray.map(new String(_, charset))
}
