package zio.http.internal

import zio.Scope
import zio.http.api.Combiner
import zio.http.api.internal.RichTextCodec
import zio.test.Assertion.equalTo
import zio.test._

object RichTextCodecSpec extends ZIOSpecDefault {

  override def spec = suite("Rich Text Codec Spec")(
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
  )
}
