package zio.http.api.internal

import zio.http.api._
import zio.test.Assertion.equalTo
import zio.test._

object RichTextCodecSpec extends ZIOSpecDefault {

  def success[A](a: A): Either[String, A] = Right(a)

  def textOf(doc: Doc): Option[String] =
    doc match {
      case Doc.Paragraph(Doc.Span.Code(text)) => Some(text)
      case _                                  => None
    }

  override def spec = suite("Rich Text Codec Spec")(
    suite("describe spec")(
      test("describe of specific char") {
        val codec = RichTextCodec.char('x')

        assertTrue(textOf(codec.describe).get == "“x”")
      },
      test("describe of char range") {
        val codec = RichTextCodec.filter(c => c >= 'A' && c <= 'Z')

        assertTrue(textOf(codec.describe).get == "“[A-Z]”")
      },
      test("describe of char ranges") {
        val codec = RichTextCodec.filter(c => c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z')

        assertTrue(textOf(codec.describe).get == "“[A-Za-z]”")
      },
      test("describe of char ranges containing the dash") {
        // The dash should be the 1st character, to be distinguished from a range separator
        val codec = RichTextCodec.filter(c => c >= 'A' && c <= 'Z' || c == '-' || c == '!')
        assertTrue(textOf(codec.describe).get == "“[-!A-Z]”")
      },
      test("describe of literal") {
        val codec = RichTextCodec.literal("hello")

        assertTrue(textOf(codec.describe).get == "“hello”")
      },
      test(label = "describe literal with special characters") {
        val codec = RichTextCodec.literal("""[a-z"”]\""")

        assertTrue(textOf(codec.describe).get == """“\[a-z\"\”\]\\”""")
      },
      test(label = "describe literal with new line characters") {
        val codec = RichTextCodec.literal("\n")

        assertTrue(textOf(codec.describe).get == """“\n”""")
      },
      test("describe of CI literal") {
        val codec = RichTextCodec.literalCI("hello")

        assertTrue(textOf(codec.describe).get == "“[Hh][Ee][Ll][Ll][Oo]”")
      },
      test("describe letter as a simple name") {
        val codec = RichTextCodec.letter

        assertTrue(textOf(codec.describe).get == "«letter»")
      },
      test("describe sequence of char alternatives") {
        val a     = RichTextCodec.char('a')
        val b     = RichTextCodec.char('b')
        val c     = RichTextCodec.char('c')
        val d     = RichTextCodec.char('d')
        val codec = (a | b) ~ (c | d)

        assertTrue(textOf(codec.describe).get == "“[ab][cd]”")
      },
      test("describe sequence of alternative literals") {
        val hello    = RichTextCodec.literal("hello")
        val hi       = RichTextCodec.literal("hi")
        val world    = RichTextCodec.literal("world")
        val universe = RichTextCodec.literal("universe")
        val codec    = (hello | hi) ~ (world | universe)

        assertTrue(textOf(codec.describe).get == "(“hello” | “hi”) (“world” | “universe”)")
      },
      test("describe sequence of alternative literals and chars") {
        val a     = RichTextCodec.literal("a")
        val bb    = RichTextCodec.literal("bb")
        val cc    = RichTextCodec.literal("cc")
        val d     = RichTextCodec.literal("d")
        val codec = (a | bb) ~ (cc | d)

        assertTrue(textOf(codec.describe).get == "(“a” | “bb”) (“cc” | “d”)")
      },
      test("describe tagged (non recursive)") {
        val greeting = (RichTextCodec.literal("hello") | RichTextCodec.literal("hi")) ?? "greeting"
        val planet   = (RichTextCodec.literal("Earth") | RichTextCodec.literal("Mars")) ?? "planet"
        val codec    = greeting ~ planet
        assertTrue(
          textOf(codec.describe).get ==
            """«greeting» «planet»
              |«greeting» ⩴ “hello” | “hi”
              |«planet» ⩴ “Earth” | “Mars”""".stripMargin,
        )
      },
      test("describe simple recursion") {
        val codec = RichTextCodec.char('x').repeat
        // This would be perhaps nicer as «1» ⩴ “x”* or even without the label.
        assertTrue(textOf(codec.describe).get == "«1» ⩴ (“x” «1»)?")
      },
      test("describe tagged simple recursion") {
        val codec = RichTextCodec.char('x').repeat ?? "xs"
        // This would be perhaps nicer as «xs» ⩴ “x”*
        assertTrue(textOf(codec.describe).get == "«xs» ⩴ (“x” «xs»)?")
      },
      test("describe tagged with recursion") {
        lazy val integer: RichTextCodec[_] = (RichTextCodec.digit ~ (RichTextCodec.empty | integer)) ?? "integer"
        val decimal                        = (integer | integer ~ RichTextCodec.char('.') ~ integer) ?? "decimal"
        assertTrue(
          textOf(decimal.describe).get ==
            """|«decimal» ⩴ «integer» | «integer» “.” «integer»
               |«integer» ⩴ “[0-9]” «integer»?""".stripMargin,
        )
      },
      test("describe labelled mutual recursion") {
        lazy val a: RichTextCodec[_] = (RichTextCodec.char('a') | b) ?? "a"
        lazy val b: RichTextCodec[_] = (RichTextCodec.char('b') | a) ?? "b"
        assertTrue(
          textOf(a.describe).get ==
            """«a» ⩴ “a” | «b»
              |«b» ⩴ “b” | «a»""".stripMargin,
        )
      },
      test("describe unlabelled mutual recursion") {
        lazy val a: RichTextCodec[_] = RichTextCodec.char('a') | b
        lazy val b: RichTextCodec[_] = RichTextCodec.char('b') | a
        assertTrue(
          textOf(a.describe).get ==
            """«1» ⩴ “a” | “b” | «1»""".stripMargin,
        )
      },
    ),
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
      test("double decoder") {
        val codec = RichTextCodec.double
        assertTrue(success(123.0) == codec.decode("123")) &&
        assertTrue(success(123.45) == codec.decode("123.45")) &&
        assertTrue(codec.decode("abc").isRight)
      },
    ),
  )
}
