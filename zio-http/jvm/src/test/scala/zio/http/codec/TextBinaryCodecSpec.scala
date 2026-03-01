package zio.http.codec

import zio._
import zio.test._

import zio.schema._
import zio.schema.annotation.simpleEnum

import zio.http.ZIOHttpSpec

object TextBinaryCodecSpec extends ZIOHttpSpec {

  @simpleEnum sealed trait Fruit
  object Fruit {
    case object Apple extends Fruit

    @simpleEnum sealed trait Citrus extends Fruit
    object Citrus {
      case object Orange extends Citrus
      case object Lemon  extends Citrus
    }

    implicit val schema: Schema[Fruit] = DeriveSchema.gen[Fruit]
  }

  @simpleEnum sealed trait Color
  object Color {
    case object Red   extends Color
    case object Green extends Color
    case object Blue  extends Color

    implicit val schema: Schema[Color] = DeriveSchema.gen[Color]
  }

  override def spec = suite("TextBinaryCodecSpec")(
    suite("nested @simpleEnum sealed trait")(
      test("encode Apple") {
        val codec  = TextBinaryCodec.fromSchema[Fruit]
        val result = codec.encode(Fruit.Apple)
        assertTrue(result == Chunk.fromArray("Apple".getBytes))
      },
      test("encode Orange (nested)") {
        val codec  = TextBinaryCodec.fromSchema[Fruit]
        val result = codec.encode(Fruit.Citrus.Orange)
        assertTrue(result == Chunk.fromArray("Orange".getBytes))
      },
      test("encode Lemon (nested)") {
        val codec  = TextBinaryCodec.fromSchema[Fruit]
        val result = codec.encode(Fruit.Citrus.Lemon)
        assertTrue(result == Chunk.fromArray("Lemon".getBytes))
      },
      test("decode Apple") {
        val codec  = TextBinaryCodec.fromSchema[Fruit]
        val result = codec.decode(Chunk.fromArray("Apple".getBytes))
        assertTrue(result == Right(Fruit.Apple))
      },
      test("decode Orange (nested)") {
        val codec  = TextBinaryCodec.fromSchema[Fruit]
        val result = codec.decode(Chunk.fromArray("Orange".getBytes))
        assertTrue(result == Right(Fruit.Citrus.Orange))
      },
      test("decode Lemon (nested)") {
        val codec  = TextBinaryCodec.fromSchema[Fruit]
        val result = codec.decode(Chunk.fromArray("Lemon".getBytes))
        assertTrue(result == Right(Fruit.Citrus.Lemon))
      },
    ),
    suite("flat @simpleEnum sealed trait (backward compatibility)")(
      test("encode Red") {
        val codec  = TextBinaryCodec.fromSchema[Color]
        val result = codec.encode(Color.Red)
        assertTrue(result == Chunk.fromArray("Red".getBytes))
      },
      test("decode Green") {
        val codec  = TextBinaryCodec.fromSchema[Color]
        val result = codec.decode(Chunk.fromArray("Green".getBytes))
        assertTrue(result == Right(Color.Green))
      },
      test("roundtrip Blue") {
        val codec   = TextBinaryCodec.fromSchema[Color]
        val encoded = codec.encode(Color.Blue)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(Color.Blue))
      },
    ),
  )
}
