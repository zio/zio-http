package zio.web.http.internal

import java.io.IOException

import zio.{ Chunk, IO, ZIO }
import zio.nio.core.channels.SocketChannel
import zio.web.http.{ EMPTY_LINE, NEW_LINE }
import zio.nio.core.channels.AsynchronousSocketChannel
import zio.duration.Duration

abstract private[http] class ChannelReader[Error] {

  protected val CHUNK_SIZE: Int

  protected def readChunk(size: Int): IO[Error, Chunk[Byte]]

  def readUntil(delim: Chunk[Byte], prefix: Chunk[Byte] = Chunk.empty): IO[Error, ChannelReader.Data] = {
    val empty: Chunk[Byte] = Chunk.empty

    def partition(chunk: Chunk[Byte], acc: Chunk[Byte], buf: Chunk[Byte]): (Chunk[Byte], Chunk[Byte], Chunk[Byte]) =
      chunk.foldLeft((acc, buf, empty)) {
        case ((left, sep, right), byte) if sep.size < delim.size =>
          if (byte == delim(sep.size)) (left, sep :+ byte, right)
          else (left ++ sep :+ byte, empty, right)
        case ((left, sep, right), byte) =>
          (left, sep, right :+ byte)
      }

    def loop(acc: Chunk[Byte], buf: Chunk[Byte]): IO[Error, ChannelReader.Data] =
      readChunk(CHUNK_SIZE).flatMap(run(_, acc, buf))

    def run(chunk: Chunk[Byte], acc: Chunk[Byte], buf: Chunk[Byte]): IO[Error, ChannelReader.Data] =
      partition(chunk, acc, buf) match {
        case (left, sep, right) =>
          if (sep.size == delim.size) ZIO.succeedNow(ChannelReader.Data(left ++ sep, right))
          else loop(left, sep)
      }

    run(prefix, empty, empty)
  }

  def readUntilNewLine(prefix: Chunk[Byte] = Chunk.empty): IO[Error, ChannelReader.Data] =
    readUntil(NEW_LINE, prefix)

  def readUntilEmptyLine(prefix: Chunk[Byte] = Chunk.empty): IO[Error, ChannelReader.Data] =
    readUntil(EMPTY_LINE, prefix)

  def read(num: Int, prefix: Chunk[Byte] = Chunk.empty): IO[Error, Chunk[Byte]] =
    if (num > prefix.size) readChunk(num - prefix.size).map(chunk => prefix ++ chunk)
    else ZIO.succeedNow(prefix)
}

private[http] object ChannelReader {

  def apply(channel: SocketChannel, chunkSize: Int): ChannelReader[IOException] = new ChannelReader[IOException] {

    protected val CHUNK_SIZE: Int = chunkSize

    protected def readChunk(size: Int): IO[IOException, Chunk[Byte]] =
      channel.readChunk(size)
  }

  def apply(channel: AsynchronousSocketChannel, chunkSize: Int, timeout: Duration): ChannelReader[Exception] =
    new ChannelReader[Exception] {

      protected val CHUNK_SIZE: Int = chunkSize

      protected def readChunk(size: Int): IO[Exception, Chunk[Byte]] =
        channel.readChunk(size, timeout)
    }

  final case class Data(value: Chunk[Byte], tail: Chunk[Byte])
}
