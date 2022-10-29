package zio.http.api.internal

import zio.Scope
import zio.http.api.Combiner
import zio.test._

import scala.collection.immutable.BitSet

object RichTextCodecSpec extends ZIOSpecDefault {
  override def spec = suite("descriptions")(
    test("single character") {
      val description = RichTextCodec.char('x').describe.toCommonMark
      val expected    =
        """x
          |
          |""".stripMargin
      assertTrue(description == expected)
    },
    test("digit") {
      val description = RichTextCodec.digit.describe.toCommonMark
      val expected    =
        """```<digit>```
          |
          |""".stripMargin
      assertTrue(description == expected)
    },
    test("literal") {
      val description = RichTextCodec.literal("test").describe.toCommonMark
      val expected =
        """test
          |
          |""".stripMargin
      assertTrue(description == expected)
    },
    test("alternative characters") {
      val codec = RichTextCodec.CharIn(BitSet('a', 'b'))
      val descriptionDoc = codec.describe
      println(descriptionDoc)
      val description = descriptionDoc.toCommonMark
      val expected =
        """``` (```a```|```b```) ```
          |
          |""".stripMargin
      assertTrue(description == expected)
    }
  )
}
