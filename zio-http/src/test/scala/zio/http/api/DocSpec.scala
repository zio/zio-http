package zio.http.api

import zio.http.api.Doc._
import zio.test._

object DocSpec extends ZIOSpecDefault {
  override def spec = suite("DocSpec")(
    test("common mark rendering") {
      val complexDoc = (
        Doc.h1("Awesome Test!")
          + Doc.p("This is a test")
          + Doc.h2("Subsection")
          + Doc.p("This is a subsection")
          + Doc.h3("Subsubsection")
          + Doc.p("This is a subsubsection")
          + Doc.p(Span.uri(java.net.URI.create("https://www.google.com")))
          + Doc.p(Span.error("This is an error"))
          + Doc.p(Span.code("ZIO.succeed(1)"))
          + Doc.p(Span.strong("This is strong"))
          + Doc.p(Span.weak("This is weak"))
          + Doc.descriptionList(
            Doc.Span.text("This is a description list item") -> Doc.p("This is the description"),
          )
          + Doc.enumeration(
            Doc.p("This is an enumeration item"),
            Doc.p("This is another enumeration item"),
          )
      ).toCommonMark
      val expected   = """# Awesome Test!
                       |
                       |This is a test
                       |
                       |## Subsection
                       |
                       |This is a subsection
                       |
                       |### Subsubsection
                       |
                       |This is a subsubsection
                       |
                       |[https://www.google.com](https://www.google.com)
                       |
                       |<span style="color:red">This is an error</span>
                       |
                       |```ZIO.succeed(1)```
                       |
                       |**This is strong**
                       |
                       |<span style="font-weight:lighter">This is weak</span>
                       |
                       |This is a description list item:
                       |This is the description
                       |
                       |- This is an enumeration item
                       |
                       |- This is another enumeration item
                       |
                       |""".stripMargin
      assertTrue(complexDoc == expected)
    },
  )
}
