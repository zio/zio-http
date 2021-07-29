package zhttp.core

import io.netty.buffer
import zio.{Task, UIO}

import java.nio.charset.Charset

/**
 * ZIO API for netty's zero-copy byte buffer
 */
final case class ByteBuf(asJava: buffer.ByteBuf) extends AnyVal {

  /**
   * Returns the number of readable bytes which is equal to
   */
  def readableBytes: UIO[Int] = UIO(asJava.readableBytes())

  /**
   * Returns the number of writable bytes which is equal to
   */
  def writeableBytes: UIO[Int] = UIO(asJava.writableBytes())

  /**
   * Converts this ByteBuf to string
   */
  def asString(charset: Charset = Charset.defaultCharset()): Task[String] = Task(asJava.toString(charset))
}

object ByteBuf {
  def fromString(string: String, charset: Charset = Charset.defaultCharset()): UIO[ByteBuf] =
    UIO(ByteBuf(buffer.Unpooled.copiedBuffer(string, charset)))
}
