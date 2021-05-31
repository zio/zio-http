package zhttp.core

import io.netty.buffer.{Unpooled, ByteBufUtil => JByteBufUtil}
import zhttp.core.Direction.{Flip, Out, In}
import zhttp.core.Nat._
import zio.Chunk

import java.nio.charset.Charset

/**
 * A wrapper on netty's ByteBuf which maintains reference count in it's type
 */
final class HBuf[C <: Nat, D](private[zhttp] val asJava: JByteBuf) extends AnyVal { self =>

  /**
   * Increases the ref count by 1
   */
  def retain(implicit ev: C > Zero): HBuf[Successor[C], D] = HBuf(asJava.retain())

  /**
   * Decreases the ref count by 1
   */
  def release[A <: Nat](implicit ev: C > Zero, ev0: Successor[A] =:= C): HBuf[A, D] = HBuf {
    asJava.release()
    asJava
  }

  /**
   * Flips the direction of the buffer
   */
  private[zhttp] def flip[D0](implicit ev: Flip[D, D0]): HBuf[C, D0] = HBuf(asJava)

  /**
   * Reads the data as a chunk of bytes
   */
  def toByteChunk(implicit c: C =:= Two, d: D =:= Out): Chunk[Byte] =
    Chunk.fromArray(JByteBufUtil.getBytes(asJava))
}

object HBuf {
  final case class UnexpectedRefCount(actual: Int, expected: Int) extends Throwable {
    override def getMessage: String = s"Expected ByteBuf.refCount to be ${1} but is ${actual}"
  }

  private[zhttp] def one[D](byteBuf: JByteBuf): HBuf[One, D] = any(1, byteBuf)
  private[zhttp] def two[D](byteBuf: JByteBuf): HBuf[One, D] = any(2, byteBuf)

  private def apply[C <: Nat, D](byteBuf: JByteBuf): HBuf[C, D] = new HBuf(byteBuf)
  private def any[C <: Nat, D](cnt: Int, byteBuf: JByteBuf): HBuf[One, D] = {
    if (byteBuf.refCnt() == cnt) new HBuf(byteBuf)
    else throw UnexpectedRefCount(byteBuf.refCnt(), cnt)
  }

  /**
   * Creates an HBuf from the provided string
   */
  def fromString(str: String, charset: Charset): HBuf[One, Out] =
    fromArray(str.getBytes(charset))

  /**
   * Creates an HBuf from a chunk of bytes
   */
  def fromChunk(bytes: Chunk[Byte]): HBuf[One, Out] =
    fromArray(bytes.toArray)

  /**
   * Creates an HBuf from an array of bytes
   */
  def fromArray(array: Array[Byte]): HBuf[One, Out] =
    HBuf(Unpooled.copiedBuffer(array))

  /**
   * Creates an empty HBuf
   */
  def empty: HBuf[One, In] = HBuf(Unpooled.EMPTY_BUFFER)
}
