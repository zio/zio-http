package zio.http.h2.hpack

import scala.annotation.experimental

import zio.test._

@experimental
object HpackInternalsSpec extends ZIOSpecDefault {
  override def spec =
    suite("HpackInternalsSpec")(
      suite("DynamicTable")(
        test("evicts older entries, supports case-insensitive lookup, and clears oversized entries") {
          val table       = new DynamicTable(initialMaxSize = 64)
          val firstEntry  = HeaderField("X-One", "value")
          val secondEntry = HeaderField("X-Two", "other")

          table.add(firstEntry)
          table.add(secondEntry)

          val afterEviction =
            table.length == 1 &&
              table.get(1).contains(secondEntry.copy(name = "x-two")) &&
              table.indexOfName("X-TWO") == 1 &&
              table.indexOf(firstEntry) == -1 &&
              table.get(2).isEmpty

          val tooLargeEntry = HeaderField("X-Too-Large", "x" * 128)
          table.add(tooLargeEntry)

          assertTrue(
            afterEviction,
            table.length == 0,
            table.currentSize == 0,
          )
        },
        test("setMaxSize evicts down to the new limit") {
          val table = new DynamicTable(initialMaxSize = 256)
          table.add(HeaderField("a", "1"))
          table.add(HeaderField("b", "2"))

          table.setMaxSize(0)

          assertTrue(table.length == 0, table.currentSize == 0, table.maximumSize == 0)
        },
      ),
      suite("IntegerCodec")(
        test("encodes and decodes values that require continuation bytes") {
          val encoded = IntegerCodec.encodeInt(value = 1337, prefixBits = 5)

          assertTrue(IntegerCodec.decodeInt(encoded, offset = 0, prefixBits = 5) == Right((1337, encoded.length)))
        },
        test("rejects invalid prefix sizes and offsets") {
          val bytes = Array(0x00.toByte)

          assertTrue(
            IntegerCodec.decodeInt(bytes, offset = 0, prefixBits = 0) == Left("Invalid HPACK integer prefix size: 0"),
            IntegerCodec.decodeInt(bytes, offset = 1, prefixBits = 5) == Left("Invalid HPACK integer offset: 1"),
          )
        },
        test("rejects truncated integers, excessive continuation bytes, and overflow") {
          val truncated = Array(0x1f.toByte)
          val tooLong = Array(0x1f.toByte, 0x80.toByte, 0x80.toByte, 0x80.toByte, 0x80.toByte, 0x80.toByte, 0x00.toByte)
          val overflow = Array(0x1f.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0xff.toByte, 0x7f.toByte)

          assertTrue(
            IntegerCodec.decodeInt(truncated, offset = 0, prefixBits = 5) == Left("Truncated HPACK integer"),
            IntegerCodec.decodeInt(tooLong, offset = 0, prefixBits = 5) == Left(
              "HPACK integer uses too many continuation bytes",
            ),
            IntegerCodec.decodeInt(overflow, offset = 0, prefixBits = 5) == Left("HPACK integer overflow"),
          )
        },
      ),
      suite("HuffmanCodec")(
        test("rejects EOS symbols and invalid padding") {
          val eosEncoded    = Array(0xff.toByte, 0xff.toByte, 0xff.toByte, 0xfc.toByte)
          val validWithPad  = HuffmanCodec.encode("a").toArray
          val invalidPadded = validWithPad.updated(validWithPad.length - 1, (validWithPad.last & 0xfe).toByte)

          assertTrue(
            HuffmanCodec.decode(eosEncoded) == Left("Invalid HPACK Huffman bit sequence"),
            HuffmanCodec.decode(invalidPadded) == Left("HPACK Huffman padding is not a prefix of EOS"),
          )
        },
      ),
    )
}
