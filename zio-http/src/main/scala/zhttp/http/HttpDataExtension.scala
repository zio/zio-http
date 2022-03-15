package zhttp.http

import io.netty.buffer.{ByteBuf, ByteBufUtil}
import zhttp.http.headers.HeaderExtension
import zio.stream.ZStream
import zio.{Chunk, Task, UIO}

private[zhttp] trait HttpDataExtension[+A] extends HeaderExtension[A] { self: A =>
  def data: HttpData

  private[zhttp] final def bodyAsByteBuf: Task[ByteBuf] = data.toByteBuf

  final def bodyAsByteArray: Task[Array[Byte]] =
    bodyAsByteBuf.flatMap(buf => Task(ByteBufUtil.getBytes(buf)).ensuring(UIO(buf.release(buf.refCnt()))))

  /**
   * Decodes the content of request as a Chunk of Bytes
   */
  final def body: Task[Chunk[Byte]] =
    bodyAsByteArray.map(Chunk.fromArray)

  /**
   * Decodes the content of request as string
   */
  final def bodyAsString: Task[String] =
    bodyAsByteArray.map(new String(_, charset))

  /**
   * Decodes the content of request as stream of bytes
   */
  final def bodyAsStream: ZStream[Any, Throwable, Byte] = data.toByteBufStream
    .mapM[Any, Throwable, Chunk[Byte]] { buf =>
      Task {
        val bytes = Chunk.fromArray(ByteBufUtil.getBytes(buf))
        buf.release(buf.refCnt())
        bytes
      }
    }
    .flattenChunks
}
