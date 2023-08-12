/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.endpoint

import zio.test._

import zio.http.ZIOHttpSpec
import zio.http.codec.Doc
import zio.http.codec.Doc._

object DocSpec extends ZIOHttpSpec {
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
                        |<p>This is a test</p>
                        |<h2>Subsection</h2>
                        |<p>This is a subsection</p>
                        |<h3>Subsubsection</h3>
                        |<p>This is a subsubsection</p>
                        |<p>
                        |  <a href="https://www.google.com">https://www.google.com</a>
                        |</p>
                        |<p>
                        |  <span style="color:red">This is an error</span>
                        |</p>
                        |<p>
                        |  <code>ZIO.succeed(1)</code>
                        |</p>
                        |<p>
                        |  <b>This is strong</b>
                        |</p>
                        |<p>
                        |  <i>This is italic</i>
                        |</p>
                        |<dl>
                        |  <dt>This is a description list item</dt>
                        |  <dd>
                        |    <p>This is the description</p>
                        |  </dd>
                        |</dl>
                        |<ol>
                        |  <li>
                        |    <p>This is an enumeration item</p>
                        |  </li>
                        |  <li>
                        |    <p>This is another enumeration item</p>
                        |    <ul>
                        |      <li>
                        |        <p>This is a nested enumeration item</p>
                        |      </li>
                        |      <li>
                        |        <p>This is another nested enumeration item</p>
                        |      </li>
                        |    </ul>
                        |  </li>
                        |</ol>""".stripMargin
      assertTrue(complexDoc == expected)
    },
    test("plain text rendering") {
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
      ).toPlaintext(color = false)
      val expected   = """
                       |AWESOME TEST!
                       |
                       |  This is a test
                       |
                       |SUBSECTION
                       |
                       |  This is a subsection
                       |
                       |SUBSUBSECTION
                       |
                       |  This is a subsubsection
                       |
                       |  https://www.google.com
                       |
                       |  This is an error
                       |
                       |  ZIO.succeed(1)
                       |
                       |  This is strong
                       |
                       |  This is italic
                       |
                       |  This is a description list item
                       |    This is the description
                       |
                       |  1. This is an enumeration item
                       |  2. This is another enumeration item
                       |    - This is a nested enumeration item
                       |    - This is another nested enumeration item
                       |""".stripMargin
      assertTrue(complexDoc == expected)
    },
  )
}
