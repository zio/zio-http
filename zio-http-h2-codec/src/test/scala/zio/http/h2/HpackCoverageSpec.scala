package zio.http.h2

import scala.annotation.experimental

import zio.blocks.chunk.Chunk
import zio.test._

import zio.http.h2.hpack.{HeaderField, Hpack, HpackDecoder, HpackEncoder}

@experimental
object HpackCoverageSpec extends ZIOSpecDefault {
  override def spec =
    suite("HpackCoverageSpec")(
      test("encoder handles zero-sized dynamic tables and raw string literals") {
        val header  = HeaderField("x-raw", "a")
        val encoder = new HpackEncoder(initialMaxTableSize = 0)
        val encoded = encoder.encode(List(header))

        assertTrue(Hpack.decode(encoded) == Right(List(header)))
      },
      test("encoder emits table size updates before headers and decoder accepts them") {
        val header  = HeaderField("x-table", "value")
        val encoder = new HpackEncoder()
        val decoder = new HpackDecoder()

        encoder.setMaxTableSize(128)
        encoder.setMaxTableSize(64)

        val encoded = encoder.encode(List(header))

        assertTrue(
          (encoded(0) & 0x20) != 0,
          decoder.decode(encoded) == Right(List(header)),
        )
      },
      test("decoder rejects late or oversized table size updates") {
        val headerThenUpdate = Hpack.encode(List(HeaderField(":method", "GET"))) ++ bytes(0x20)
        val resizingEncoder  = new HpackEncoder()
        resizingEncoder.setMaxTableSize(4097)
        val oversizedUpdate  = resizingEncoder.encode(Nil)

        assertTrue(
          new HpackDecoder().decode(headerThenUpdate) == Left(
            "HPACK dynamic table size update must appear before header fields in a block",
          ),
          new HpackDecoder(maxAllowedTableSize = 4096).decode(oversizedUpdate) ==
            Left("HPACK dynamic table size update exceeds limit: 4097"),
        )
      },
      test("decoder rejects invalid indexed and truncated literal encodings") {
        assertTrue(
          Hpack.decode(bytes(0x80)) == Left("Indexed header field representation cannot use index 0"),
          Hpack.decode(bytes(0x00)) == Left("Truncated HPACK string literal"),
          Hpack.decode(bytes(0x00, 0x01)) == Left("Truncated HPACK string literal data"),
        )
      },
      test("encoder and decoder reuse dynamic table names across different values") {
        val firstHeader  = HeaderField("x-shared-name", "one")
        val secondHeader = HeaderField("x-shared-name", "two")
        val encoder      = new HpackEncoder()
        val decoder      = new HpackDecoder()
        val firstBlock   = encoder.encode(List(firstHeader))
        val secondBlock  = encoder.encode(List(secondHeader))

        assertTrue(
          decoder.decode(firstBlock) == Right(List(firstHeader)),
          decoder.decode(secondBlock) == Right(List(secondHeader)),
          secondBlock.length < firstBlock.length,
        )
      },
    )

  private def bytes(values: Int*): Chunk[Byte] =
    Chunk.fromArray(values.toArray.map(_.toByte))
}
