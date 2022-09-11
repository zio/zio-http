package zio.http.html

import zio.test.Gen

object HtmlGen {
  val voidTagGen: Gen[Any, CharSequence] = Gen.fromIterable(Element.voidElementNames)
  val tagGen: Gen[Any, String]           =
    Gen.stringBounded(1, 5)(Gen.alphaChar).filterNot(Element.voidElementNames.contains)
}
