package zio.http.h2.hpack

import scala.annotation.experimental

import zio.blocks.chunk.Chunk

@experimental
object IntegerCodec {
  private val MaxContinuationBytes = 5

  def encodeInt(value: Int, prefixBits: Int): Chunk[Byte] = {
    require(prefixBits >= 1 && prefixBits <= 8, s"prefixBits must be between 1 and 8: $prefixBits")
    require(value >= 0, s"HPACK integers must be non-negative: $value")

    val maxPrefixValue = (1 << prefixBits) - 1
    val builder        = Chunk.newBuilder[Byte]

    if (value < maxPrefixValue) builder += value.toByte
    else {
      builder += maxPrefixValue.toByte

      var remainder = value - maxPrefixValue
      while (remainder >= 128) {
        builder += ((remainder & 0x7f) | 0x80).toByte
        remainder >>>= 7
      }
      builder += remainder.toByte
    }

    builder.result()
  }

  def decodeInt(bytes: Chunk[Byte], offset: Int, prefixBits: Int): Either[String, (Int, Int)] =
    decodeInt(bytes.toArray, offset, prefixBits)

  def decodeInt(bytes: Array[Byte], offset: Int, prefixBits: Int): Either[String, (Int, Int)] = {
    if (prefixBits < 1 || prefixBits > 8) Left(s"Invalid HPACK integer prefix size: $prefixBits")
    else if (offset < 0 || offset >= bytes.length) Left(s"Invalid HPACK integer offset: $offset")
    else {
      val mask           = (1 << prefixBits) - 1
      val firstByte      = bytes(offset) & 0xff
      val initialValue   = firstByte & mask
      val maxPrefixValue = mask

      if (initialValue < maxPrefixValue) Right((initialValue, 1))
      else {
        var value        = maxPrefixValue
        var shift        = 0
        var index        = offset + 1
        var consumed     = 1
        var continuation = true
        var loops        = 0

        while (continuation) {
          if (index >= bytes.length) return Left("Truncated HPACK integer")
          if (loops >= MaxContinuationBytes) return Left("HPACK integer uses too many continuation bytes")

          val nextByte = bytes(index) & 0xff
          val addend   = (nextByte & 0x7f) << shift

          if (shift >= 31 || addend < 0 || Int.MaxValue - value < addend)
            return Left("HPACK integer overflow")

          value += addend
          continuation = (nextByte & 0x80) != 0
          shift += 7
          index += 1
          consumed += 1
          loops += 1
        }

        Right((value, consumed))
      }
    }
  }
}
