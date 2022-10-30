package zio.http.internal

import zio.Scope
import zio.http.api.Combiner
import zio.http.api.internal.RichTextCodec
import zio.test.Assertion.equalTo
import zio.test._

object RichTextCodecSpec extends ZIOSpecDefault {

  def success[A](a: A): Either[String, A] = Right(a)

  override def spec = suite("Rich Text Codec Spec")(
    suite("encode spec")(
      test("encode empty spec") {
        assert(RichTextCodec.empty.encode(()))(equalTo(Right("")))
      },
      test("encode char spec") {
        check(Gen.char) { c =>
          val charCodec = RichTextCodec.char(c)
          assertTrue(charCodec.encode(c) == Right(c.toString))
        }
      },
      test("encode transform or fail") {
        check(Gen.int(0, 9)) { int =>
          val transformCodec = RichTextCodec.digit
          assertTrue(transformCodec.encode(int) == Right(int.toString()))
        }
      },
      test("encode alt Right spec") {
        check(Gen.char) { c =>
          val altCodec = RichTextCodec.empty | RichTextCodec.char(c)
          val result   = altCodec.encode(Right(c))
          assertTrue(result == Right(c.toString))
        }
      },
      test("encode alt left spec") {
        check(Gen.char('a', 'z')) { s =>
          val altCodec = RichTextCodec.letter | RichTextCodec.digit
          val result   = altCodec.encode(Left(s))
          assertTrue(result == Right(s.toString))
        }
      },
      test("encode lazy whitespace codec") {
        check(Gen.whitespaceChars) { s =>
          val whitespaceCodec = RichTextCodec.whitespaceChar
          val result          = whitespaceCodec.encode(())
          assertTrue(result == Right(" "))
        }
      },
      test("encode lazy spec") {
        check(Gen.char) { c =>
          val altCodec = RichTextCodec.defer(RichTextCodec.empty | RichTextCodec.char(c))
          val result   = altCodec.encode(Right(c))
          assertTrue(result == Right(c.toString))
        }
      },
      test("encode combined codec") {
        check(Gen.char('a', 'z'), Gen.int(0, 9)) { (c, i) =>
//        implicit val combiner                         = Combiner.combine
          val combinedCodec: RichTextCodec[(Char, Int)] =
            RichTextCodec.letter ~ RichTextCodec.digit
          val result                                    = combinedCodec.encode((c, i))
          assertTrue(result == Right(c.toString + i.toString))
        }
      },
    ),
    suite("decode spec")(
      test("empty decoder") {
        assertTrue(success(()) == RichTextCodec.empty.decode("abc")) &&
        assertTrue(success(()) == RichTextCodec.empty.decode(""))
      },
      test("char decoder") {
        check(Gen.char, Gen.string) { (c, str) =>
          val codec  = RichTextCodec.char(c)
          val result = codec.decode(str)
          if (str.nonEmpty && str(0) == c)
            assertTrue(success(c) == result)
          else
            assertTrue(result.isLeft)
        }
      },
      test("literal decoder") {
        val codec = RichTextCodec.literal("abc")
        assertTrue(success("abc") == codec.decode("abc")) &&
        assertTrue(success("abc") == codec.decode("abc-123")) &&
        assertTrue(codec.decode("123").isLeft)
      },
      test("zip decoder") {
        val codec = RichTextCodec.char('a').unit('a') ~> RichTextCodec.char('b').unit('b')
        assertTrue(success(()) == codec.decode("ab...")) &&
        assertTrue(codec.decode("..ab..").isLeft)
      },
      test("zip decoder 2") {
        val codec = RichTextCodec.literal("abc") ~ RichTextCodec.literal("123")
        assertTrue(success(("abc", "123")) == codec.decode("abc123")) &&
        assertTrue(codec.decode("abc-123").isLeft)
      },
      test("alt decoder") {
        val codec = RichTextCodec.literal("abc") | RichTextCodec.literal("123")

        val expectedL: Either[String, Either[String, String]] = Right(Left("abc"))
        val expectedR: Either[String, Either[String, String]] = Right(Right("123"))
        assertTrue(expectedL == codec.decode("abc---")) &&
        assertTrue(expectedR == codec.decode("123---")) &&
        assertTrue(codec.decode("---123---").isLeft)
      },
      test("transformOrFail decoder") {
        val codec = RichTextCodec.literal("123").transform[Int](_.toInt, _.toString)
        assertTrue(success(123) == codec.decode("123--")) &&
        assertTrue(codec.decode("4123").isLeft)
      },
    ),
    suite("describe spec")(
      test("single character") {
        val description = RichTextCodec.char('x').describe.toCommonMark
        val expected    =
          """```<main>```:
            |x
            |
            |""".stripMargin
        assertTrue(description == expected)
      },
      test("single character2") {
        val description = RichTextCodec.char('x').repeat.describe.toCommonMark
        val expected    =
          """```<main>```:
            |x ```<main>```
            |
            |""".stripMargin
        assertTrue(description == expected)
      },
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
        val codec       = RichTextCodec.filter(c => c == 'a' || c == 'b')
        val description = codec.describe.toCommonMark
        val expected    =
          """```<main>```:
            |```[```ab```]```
            |
            |""".stripMargin
        assertTrue(description == expected)
      },
      test("digits 0 to 9") {
        val codec       = RichTextCodec.filter(c => c >= '0' && c <= '9')
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
        val expected    =
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
      test("whitespaces") {
        val whitespaces = RichTextCodec.whitespaces
        val description = whitespaces.describe.toCommonMark
        val expected    =
          """```<main>```:
            |```<whitespace>``` ```<main>```
            |
            |""".stripMargin
        assertTrue(description == expected)
      },
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
    ),
  )
}
