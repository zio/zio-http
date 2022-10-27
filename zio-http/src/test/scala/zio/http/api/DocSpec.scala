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
          + Doc.p(Span.link(java.net.URI.create("https://www.google.com")))
          + Doc.p(Span.error("This is an error"))
          + Doc.p(Span.code("ZIO.succeed(1)"))
          + Doc.p(Span.bold("This is strong"))
          + Doc.p(Span.italic("This is italic"))
          + Doc.descriptionList(
            Doc.Span.text("This is a description list item") -> Doc.p("This is the description"),
          )
          + Doc.orderedListing(
            Doc.p("This is an enumeration item"),
            Doc.p("This is another enumeration item") +
              Doc.unorderedListing(
                Doc.p("This is a nested enumeration item"),
                Doc.p("This is another nested enumeration item"),
              ),
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
                       |*This is italic*
                       |
                       |This is a description list item:
                       |This is the description
                       |
                       |1. This is an enumeration item
                       |2. This is another enumeration item
                       |  -   This is a nested enumeration item
                       |  -   This is another nested enumeration item""".stripMargin
      assertTrue(complexDoc == expected)
    },
    test("html rendering") {
      val complexDoc = (
        Doc.h1("Awesome Test!")
          + Doc.p("This is a test")
          + Doc.h2("Subsection")
          + Doc.p("This is a subsection")
          + Doc.h3("Subsubsection")
          + Doc.p("This is a subsubsection")
          + Doc.p(Span.link(java.net.URI.create("https://www.google.com")))
          + Doc.p(Span.error("This is an error"))
          + Doc.p(Span.code("ZIO.succeed(1)"))
          + Doc.p(Span.bold("This is strong"))
          + Doc.p(Span.italic("This is italic"))
          + Doc.descriptionList(
            Doc.Span.text("This is a description list item") -> Doc.p("This is the description"),
          )
          + Doc.orderedListing(
            Doc.p("This is an enumeration item"),
            Doc.p("This is another enumeration item") +
              Doc.unorderedListing(
                Doc.p("This is a nested enumeration item"),
                Doc.p("This is another nested enumeration item"),
              ),
          )
      ).toHtmlSnippet
      val expected   = """|<h1>Awesome Test!</h1>
                          |
                          |
                          |<p>
                          |  This is a test
                          |</p>
                          |<h2>Subsection</h2>
                          |
                          |
                          |<p>
                          |  This is a subsection
                          |</p>
                          |<h3>Subsubsection</h3>
                          |
                          |
                          |<p>
                          |  This is a subsubsection
                          |</p>
                          |
                          |<p>
                          |  <a href="https://www.google.com">https://www.google.com</a>
                          |</p>
                          |
                          |<p>
                          |  <span style="color:red">This is an error</span>
                          |</p>
                          |
                          |<p>
                          |  <code>ZIO.succeed(1)</code>
                          |</p>
                          |
                          |<p>
                          |  <b>This is strong</b>
                          |</p>
                          |
                          |<p>
                          |  <i>This is italic</i>
                          |</p>
                          |<dl>
                          |  <dt>
                          |    This is a description list item
                          |  </dt>
                          |  <dd>
                          |    <p>
                          |      This is the description
                          |    </p>
                          |  </dd>
                          |</dl>
                          |
                          |<ol>
                          |  <li>
                          |    <p>
                          |      This is an enumeration item
                          |    </p>
                          |  </li>
                          |  <li>
                          |    <p>
                          |      This is another enumeration item
                          |    </p>
                          |    <ul>
                          |      <li>
                          |        <p>
                          |          This is a nested enumeration item
                          |        </p>
                          |      </li>
                          |      <li>
                          |        <p>
                          |          This is another nested enumeration item
                          |        </p>
                          |      </li>
                          |    </ul>
                          |  </li>
                          |</ol>
                          |""".stripMargin
      assertTrue(complexDoc == expected)
    },
  )
}
