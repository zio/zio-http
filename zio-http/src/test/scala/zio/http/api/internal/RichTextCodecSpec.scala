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
        """```<main>```:
          |x
          |
          |""".stripMargin
      assertTrue(description == expected)
    },
//    test("single character2") {
//      val description = RichTextCodec.char('x').repeat.describe.toCommonMark
//      val expected =
//        """```<main>```:
//          |x
//          |
//          |""".stripMargin
//      assertTrue(description == expected)
//    },

    test("digit") {
      val description = RichTextCodec.digit.describe.toCommonMark
      val expected    =
        """```<main>```:
          |```<digit>```
          |
          |""".stripMargin
      assertTrue(description == expected)
    },
    test("letter") {
      val description = RichTextCodec.letter.describe.toCommonMark
      val expected    =
        """```<main>```:
          |```<letter>```
          |
          |""".stripMargin
      assertTrue(description == expected)
    },
    test("whitespace") {
      val description = RichTextCodec.whitespaceChar.describe.toCommonMark
      val expected    =
        """```<main>```:
          |```<whitespace>```
          |
          |""".stripMargin
      assertTrue(description == expected)
    },
    test("literal") {
      val description = RichTextCodec.literal("test").describe.toCommonMark
      val expected    =
        """```<main>```:
          |test
          |
          |""".stripMargin
      assertTrue(description == expected)
    },
    test("alternative characters") {
      val codec       = RichTextCodec.CharIn(BitSet('a', 'b'))
      val description = codec.describe.toCommonMark
      val expected    =
        """```<main>```:
          |```[```ab```]```
          |
          |""".stripMargin
      assertTrue(description == expected)
    },
    test("digits 0 to 9") {
      val codec       = RichTextCodec.CharIn(BitSet(('0'.toInt to '9'.toInt): _*))
      val description = codec.describe.toCommonMark
      val expected    =
        """```<main>```:
          |```[```0```-```9```]```
          |
          |""".stripMargin
      assertTrue(description == expected)
    },
    test("Latin alpha numerics") {
      val codec = RichTextCodec.filter(c => c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9')
      val description = codec.describe.toCommonMark
      val expected =
        """```<main>```:
          |```[```0```-```9A```-```Za```-```z```]```
          |
          |""".stripMargin
      assertTrue(description == expected)
    },
    test("alternative literals") {
      val codec       = RichTextCodec.literal("Hello") | RichTextCodec.literal("Hi")
      val description = codec.describe.toCommonMark
      val expected    =
        """```<main>```:
          |Hello``` | ```Hi
          |
          |""".stripMargin
      assertTrue(description == expected)
    },
    test("sequence of alternatives") {
      val codec       = (RichTextCodec.literal("Hello") | RichTextCodec.literal("Hi")) ~ (RichTextCodec.literal(
        "ZIO",
      ) | RichTextCodec.literal("HTTP"))
      val description = codec.describe.toCommonMark
      val expected    =
        """```<main>```:
          |```(```Hello``` | ```Hi```)``` ```(```ZIO``` | ```HTTP```)```
          |
          |""".stripMargin
      assertTrue(description == expected)
    },
//    test ("whitespaces") {
//      TODO - construction of RichTextCodec.whitespaces never ends / stack overflow
//      val whitespaces = RichTextCodec.whitespaces
//      val description = whitespaces.describe.toCommonMark
//      val expected =
//        """```<main>```:
//          |<whitespace> <main>?
//          |
//          |""".stripMargin
//      assertTrue(description == expected)
//    },
    test("integer") {
      lazy val codec: RichTextCodec[_] = RichTextCodec.digit ~ (RichTextCodec.empty | RichTextCodec.defer(codec))
      val description                  = codec.describe.toCommonMark
      val expected =
//        TODO
//         This simple form would be nicer.
//        """```<main>```:
//          |```<digit>``` ```<main>``````)?```
//          |
//          |""".stripMargin
//        Even nicer were repetition signs (* and +)
//        """```<main>```:
//          |```<digit>``````*```
//          |
//          |""".stripMargin
//        But the following is still correct
        """```<main>```:
          |```<digit>``` ```(``````<digit>``` ```<1>``````)?```
          |
          |```<1>```:
          |```(``````<digit>``` ```<1>``````)?```
          |
          |""".stripMargin
      assertTrue(description == expected)
    },
  )
}
