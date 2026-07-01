package zio.http.h2

import java.nio.charset.StandardCharsets

import scala.annotation.experimental

import zio.blocks.chunk.Chunk
import zio.test._

import zio.http.h2.hpack.{HeaderField, Hpack, HpackDecoder, HpackEncoder, HuffmanCodec, StaticTable}

@experimental
object HpackSpec extends ZIOSpecDefault {
  override def spec =
    suite("HpackSpec")(
      test("one-shot HPACK roundtrip preserves pseudo-headers and regular headers") {
        val headers = List(
          HeaderField(":method", "GET"),
          HeaderField(":scheme", "https"),
          HeaderField(":authority", "example.com"),
          HeaderField(":path", "/search?q=zio-http"),
          HeaderField(":status", "200"),
          HeaderField("accept", "application/json"),
          HeaderField("x-request-id", "abc-123"),
        )

        assertTrue(Hpack.decode(Hpack.encode(headers)) == Right(headers))
      },
      test("static table entries encode to indexed bytes and roundtrip") {
        val headers = List(
          HeaderField(":method", "GET"),
          HeaderField(":path", "/"),
          HeaderField(":status", "200"),
        )
        val encoded = Hpack.encode(headers)

        assertTrue(
          encoded == bytes(0x82, 0x84, 0x88),
          Hpack.decode(encoded) == Right(headers),
        )
      },
      test("stateful encoder and decoder reuse dynamic table entries across header blocks") {
        val header       = HeaderField("x-custom-header", "same-value")
        val encoder      = new HpackEncoder()
        val decoder      = new HpackDecoder()
        val firstBlock   = encoder.encode(List(header))
        val secondBlock  = encoder.encode(List(header))
        val indexedBlock = bytes(0x80 | (StaticTable.length + 1))

        assertTrue(
          firstBlock.length > secondBlock.length,
          secondBlock == indexedBlock,
          decoder.decode(firstBlock) == Right(List(header)),
          decoder.decode(secondBlock) == Right(List(header)),
        )
      },
      test("Huffman codec roundtrip matches the original text and HPACK uses Huffman when shorter") {
        val path        = "/www.example.com/static/assets/compressible-content"
        val encodedText = HuffmanCodec.encode(path)
        val encoded     = Hpack.encode(List(HeaderField(":path", path)))

        assertTrue(
          encodedText.length < path.getBytes(StandardCharsets.UTF_8).length,
          HuffmanCodec.decode(encodedText) == Right(path),
          encoded.length >= 2,
          (encoded(1) & 0x80) != 0,
          Hpack.decode(encoded) == Right(List(HeaderField(":path", path))),
        )
      },
      test("sensitive headers roundtrip without losing the never-indexed flag") {
        val header  = HeaderField("authorization", "Bearer very-secret-token", sensitive = true)
        val encoded = Hpack.encode(List(header))

        assertTrue(
          encoded.nonEmpty,
          (encoded(0) & 0x10) != 0,
          Hpack.decode(encoded) == Right(List(header)),
        )
      },
      test("empty header blocks roundtrip") {
        assertTrue(Hpack.decode(Hpack.encode(Nil)) == Right(Nil))
      },
    )

  private def bytes(values: Int*): Chunk[Byte] =
    Chunk.fromArray(values.toArray.map(_.toByte))
}
