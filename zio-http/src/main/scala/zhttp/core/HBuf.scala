package zhttp.core

import io.netty.buffer.{ByteBufUtil => JByteBufUtil}
import zhttp.core.Nat._
import zio.Chunk

/**
 * A wrapper on netty's ByteBuf which maintains reference count in it's type
 */
final class HBuf[C <: Nat, D <: Direction](private[zhttp] val asJByteBuf: JByteBuf) extends AnyVal { self =>

  /**
   * Increases the ref count by 1
   */
  def retain(implicit ev: C > Zero): HBuf[Successor[C], D] = HBuf(asJByteBuf.retain())

  /**
   * Decreases the ref count by 1
   */
  def release[A <: Nat](implicit ev: C > Zero, ev0: Successor[A] =:= C): HBuf[A, D] = HBuf {
    asJByteBuf.release()
    asJByteBuf
  }

  /**
   * Flips the direction of the buffer
   */
  private[zhttp] def flip[D0 <: Direction](implicit ev: Flip[D, D0]): HBuf[C, D0] = HBuf(asJByteBuf)

  /**
   * Reads the data as a chunk of bytes
   */
  def read(implicit c: C =:= Two, d: D =:= Direction.Out): Chunk[Byte] =
    Chunk.fromArray(JByteBufUtil.getBytes(asJByteBuf))
}

object HBuf {
  final case class UnexpectedRefCount(actual: Int, expected: Int) extends Throwable {
    override def getMessage: String = s"Expected ByteBuf.refCount to be ${1} but is ${actual}"
  }

  private[zhttp] def one[D <: Direction](byteBuf: JByteBuf): HBuf[One, D] = any(1, byteBuf)
  private[zhttp] def two[D <: Direction](byteBuf: JByteBuf): HBuf[One, D] = any(2, byteBuf)

  private def apply[C <: Nat, D <: Direction](byteBuf: JByteBuf): HBuf[C, D] = new HBuf(byteBuf)
  private def any[C <: Nat, D <: Direction](cnt: Int, byteBuf: JByteBuf): HBuf[One, D] = {
    if (byteBuf.refCnt() == cnt) new HBuf(byteBuf)
    else throw UnexpectedRefCount(byteBuf.refCnt(), cnt)
  }

  def fromString[D <: Direction](str: String): HBuf[One, D]       = ???
  def fromChunk[D <: Direction](bytes: Chunk[Byte]): HBuf[One, D] = ???
}
