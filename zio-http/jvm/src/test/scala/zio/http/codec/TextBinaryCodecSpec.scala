package zio.http.codec

import zio.Chunk
import zio.test._

import zio.schema.annotation.simpleEnum
import zio.schema.{DeriveSchema, Schema}

import zio.http.ZIOHttpSpec

object TextBinaryCodecSpec extends ZIOHttpSpec {

  @simpleEnum
  sealed trait SimpleEnum
  object SimpleEnum {
    case object One   extends SimpleEnum
    case object Two   extends SimpleEnum
    case object Three extends SimpleEnum

    implicit val schema: Schema[SimpleEnum] = DeriveSchema.gen[SimpleEnum]
  }

  @simpleEnum
  sealed trait NestedEnumWithSimpleEnum
  object NestedEnumWithSimpleEnum {
    case object Foo1 extends NestedEnumWithSimpleEnum

    sealed trait Bar extends NestedEnumWithSimpleEnum
    object Bar {
      case object Bar1 extends Bar
    }

    implicit val schema: Schema[NestedEnumWithSimpleEnum] = DeriveSchema.gen[NestedEnumWithSimpleEnum]
  }

  def spec = suite("TextBinaryCodecSpec")(
    suite("fromSchema")(
      test("handles simple enum with case objects") {
        val codec   = TextBinaryCodec.fromSchema[SimpleEnum]
        val encoded = codec.encode(SimpleEnum.One)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(SimpleEnum.One))
      },
      test("handles nested sealed trait hierarchy with simpleEnum") {
        val codec   = TextBinaryCodec.fromSchema[NestedEnumWithSimpleEnum]
        val encoded = codec.encode(NestedEnumWithSimpleEnum.Foo1)
        val decoded = codec.decode(encoded)
        assertTrue(decoded == Right(NestedEnumWithSimpleEnum.Foo1))
      },
    ),
  )
}
