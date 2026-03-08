package zio.http.codec

import zio._
import zio.test._

import zio.schema._
import zio.schema.annotation.simpleEnum

import zio.http.ZIOHttpSpec

object TextBinaryCodecSpec extends ZIOHttpSpec {

  @simpleEnum sealed trait Color
  object Color {
    case object Red   extends Color
    case object Green extends Color
    case object Blue  extends Color

    implicit val schema: Schema[Color] = DeriveSchema.gen[Color]
  }

  override def spec = suite("TextBinaryCodecSpec")(
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
