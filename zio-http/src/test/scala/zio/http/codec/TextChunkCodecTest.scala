package zio.http.codec

import zio._
import zio.test._

object TextChunkCodecTest extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("Text chunk codec") {
    suite("success") {
      test("one encode and decode") {
        val codec = TextChunkCodec.one(TextCodec.boolean)
        assertTrue(codec.decode(Chunk("true")) == TextChunkCodec.DecodeSuccess(true))
        assertTrue(codec.encode(true) == Chunk("true"))
      } +
        test("one encode and decode") {
          val codec = TextChunkCodec.optional(TextCodec.long)
          assertTrue(codec.decode(Chunk.empty) == TextChunkCodec.DecodeSuccess(None))
          assertTrue(codec.encode(None) == Chunk.empty)
          assertTrue(codec.decode(Chunk("42")) == TextChunkCodec.DecodeSuccess(Some(42L)))
          assertTrue(codec.encode(Some(42L)) == Chunk("42"))
        } +
        test("oneOrMore encode and decode") {
          val codec = TextChunkCodec.oneOrMore(TextCodec.int)
          assertTrue(codec.decode(Chunk("42")) == TextChunkCodec.DecodeSuccess(NonEmptyChunk(42)))
          assertTrue(codec.encode(NonEmptyChunk(42)) == Chunk("42"))
          assertTrue(codec.decode(Chunk("1", "2", "3")) == TextChunkCodec.DecodeSuccess(NonEmptyChunk(1, 2, 3)))
          assertTrue(codec.encode(NonEmptyChunk(1, 2, 3)) == Chunk("1", "2", "3"))
        } +
        test("any encode and decode") {
          val codec = TextChunkCodec.any(TextCodec.string)
          assertTrue(codec.decode(Chunk.empty) == TextChunkCodec.DecodeSuccess(Chunk.empty))
          assertTrue(codec.encode(Chunk.empty) == Chunk.empty)
          assertTrue(codec.decode(Chunk("Elm")) == TextChunkCodec.DecodeSuccess(Chunk("Elm")))
          assertTrue(codec.encode(Chunk("Street")) == Chunk("Street"))
          assertTrue(
            codec.decode(Chunk("One", "Two", "Freddy's Coming For You")) ==
              TextChunkCodec.DecodeSuccess(Chunk("One", "Two", "Freddy's Coming For You")),
          )
          assertTrue(
            codec.encode(Chunk("Three", "Four", "Better Lock Your Door")) ==
              Chunk("Three", "Four", "Better Lock Your Door"),
          )
        }
    } +
      suite("failure") {
        suite("malformed data") {
          test("one decode") {
            assertTrue(
              TextChunkCodec.one(TextCodec.boolean).decode(Chunk("")) == TextChunkCodec.MalformedData(TextCodec.boolean),
            )
          } +
            test("optional decode") {
              assertTrue(
                TextChunkCodec.optional(TextCodec.int).decode(Chunk("abc")) ==
                  TextChunkCodec.MalformedData(TextCodec.int),
              )
            } +
            test("oneOrMore decode") {
              val codec = TextChunkCodec.oneOrMore(TextCodec.long)
              assertTrue(codec.decode(Chunk("#$@")) == TextChunkCodec.MalformedData(TextCodec.long))
              assertTrue(codec.decode(Chunk("123", "#$@", "567")) == TextChunkCodec.MalformedData(TextCodec.long))
            } +
            test("any decode") {
              val codec = TextChunkCodec.any(TextCodec.uuid)
              assertTrue(codec.decode(Chunk("42")) == TextChunkCodec.MalformedData(TextCodec.uuid))
              assertTrue(
                codec.decode(Chunk("7", "00000000-feed-dada-iced-c0ffee000000", "3")) ==
                  TextChunkCodec.MalformedData(TextCodec.uuid),
              )
            }
        } +
          suite("invalid cardinality") {
            test("one decode") {
              val codec = TextChunkCodec.one(TextCodec.string)
              assertTrue(codec.decode(Chunk.empty) == TextChunkCodec.MissedData)
              assertTrue(codec.decode(Chunk("a", "b", "c")) == TextChunkCodec.InvalidCardinality(3, "exactly one"))
            } +
              test("optional decode") {
                assertTrue(
                  TextChunkCodec.optional(TextCodec.string).decode(Chunk("x", "y")) ==
                    TextChunkCodec.InvalidCardinality(2, "one or none"),
                )
              } +
              test("oneOrMore decode") {
                assertTrue(
                  TextChunkCodec.oneOrMore(TextCodec.string).decode(Chunk.empty) == TextChunkCodec.MissedData,
                )
              }
          }
      }
  }
}
