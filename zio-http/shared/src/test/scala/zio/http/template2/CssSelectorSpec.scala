package zio.http.template2

import zio.test._

import zio.http.template2.CssSelector._

object CssSelectorSpec extends ZIOSpecDefault {

  def spec = suite("CssSelector DSL")(
    suite("Basic Selector Rendering")(
      test("element selectors render correctly") {
        assertTrue(
          div.selector.render == "div",
          span.selector.render == "span",
          element("custom-element").toString == "custom-element",
        )
      },
      test("class selectors render correctly") {
        assertTrue(`class`("container").render == ".container")
      },
      test("id selectors render correctly") {
        assertTrue(id("header").render == "#header")
      },
      test("universal selector renders correctly") {
        assertTrue(universal.toString == "*")
      },
    ),
    suite("Combinator Rendering")(
      test("child combinator (>) renders correctly") {
        val child      = div > p
        val childChild = nav > ul > li
        assertTrue(
          child.toString == "div > p",
          childChild.toString == "nav > ul > li",
        )
      },
      test("descendant combinator (space) renders correctly") {
        assertTrue(
          (div >> span).toString == "div span",
          (article >> p >> span).toString == "article p span",
        )
      },
      test("adjacent sibling (+) renders correctly") {
        assertTrue(
          (h1 + p).toString == "h1 + p",
          (div + section).toString == "div + section",
        )
      },
      test("general sibling (~) renders correctly") {
        assertTrue(
          (h2 ~ p).toString == "h2 ~ p",
          (nav ~ zio.http.template2.main).toString == "nav ~ main",
        )
      },
      test("and combinator (&) renders correctly") {
        assertTrue(
          (div & `class`("active")).toString == "div.active",
          (input & `class`("required") & `class`("valid")).toString == "input.required.valid",
        )
      },
      test("or combinator (|) renders correctly") {
        assertTrue(
          (h1 | h2).toString == "h1, h2",
          (div | span | p).toString == "div, span, p",
        )
      },
    ),
    suite("Method-based Combinator Rendering")(
      test("child method renders correctly") {
        assertTrue(
          div.child(p).toString == "div > p",
          nav.child(ul).child(li).toString == "nav > ul > li",
        )
      },
      test("descendant method renders correctly") {
        assertTrue(
          div.descendant(span).toString == "div span",
          article.descendant(p).descendant(em).toString == "article p em",
        )
      },
      test("adjacentSibling method renders correctly") {
        assertTrue(
          h1.adjacentSibling(p).toString == "h1 + p",
        )
      },
      test("generalSibling method renders correctly") {
        assertTrue(
          h2.generalSibling(p).toString == "h2 ~ p",
        )
      },
      test("and method renders correctly") {
        assertTrue(
          div.and(`class`("container")).toString == "div.container",
        )
      },
      test("or method renders correctly") {
        assertTrue(
          span.or(p).toString == "span, p",
        )
      },
    ),
    suite("Pseudo-class Rendering")(
      test("basic pseudo-classes render correctly") {
        assertTrue(
          a.hover.toString == "a:hover",
          input.focus.toString == "input:focus",
          button.active.toString == "button:active",
          a.visited.toString == "a:visited",
        )
      },
      test("structural pseudo-classes render correctly") {
        assertTrue(
          li.firstChild.toString == "li:first-child",
          li.lastChild.toString == "li:last-child",
          li.nthChild(2).toString == "li:nth-child(2)",
          li.nthChild("2n+1").toString == "li:nth-child(2n+1)",
        )
      },
      test("not pseudo-class renders correctly") {
        assertTrue(
          div.not(`class`("hidden")).toString == "div:not(.hidden)",
          p.not(span).toString == "p:not(span)",
        )
      },
    ),
    suite("Pseudo-element Rendering")(
      test("basic pseudo-elements render correctly") {
        assertTrue(
          p.before.toString == "p::before",
          p.after.toString == "p::after",
          p.firstLine.toString == "p::first-line",
          p.firstLetter.toString == "p::first-letter",
        )
      },
    ),
    suite("Attribute Selector Rendering")(
      test("attribute existence renders correctly") {
        assertTrue(
          input.withAttribute("type").toString == "input[type]",
          div.withAttribute("data-toggle").toString == "div[data-toggle]",
        )
      },
      test("exact attribute match renders correctly") {
        assertTrue(
          input.withAttribute("type", "text").toString == """input[type="text"]""",
          div.withAttribute("class", "btn").toString == """div[class="btn"]""",
        )
      },
      test("attribute contains renders correctly") {
        assertTrue(
          div.withAttributeContaining("class", "btn").toString == """div[class*="btn"]""",
          a.withAttributeContaining("href", "example").toString == """a[href*="example"]""",
        )
      },
      test("attribute starts with renders correctly") {
        assertTrue(
          a.withAttributeStarting("href", "https").toString == """a[href^="https"]""",
          img.withAttributeStarting("src", "/images").toString == """img[src^="/images"]""",
        )
      },
      test("attribute ends with renders correctly") {
        assertTrue(
          img.withAttributeEnding("src", ".jpg").toString == """img[src$=".jpg"]""",
          a.withAttributeEnding("href", ".pdf").toString == """a[href$=".pdf"]""",
        )
      },
      test("attribute word match renders correctly") {
        assertTrue(
          div.withAttributeWord("class", "active").toString == """div[class~="active"]""",
          p.withAttributeWord("data-tags", "important").toString == """p[data-tags~="important"]""",
        )
      },
      test("attribute prefix match renders correctly") {
        assertTrue(
          div.withAttributePrefix("lang", "en").toString == """div[lang|="en"]""",
          span.withAttributePrefix("data-lang", "fr").toString == """span[data-lang|="fr"]""",
        )
      },
    ),
    suite("Web Component Rendering")(
      test("slot pseudo-element renders correctly") {
        assertTrue(
          element("my-component").slotted.toString == "my-component::slotted(*)",
          element("custom-element").slotted(p).toString == "custom-element::slotted(p)",
          element("web-component").slotted(`class`("content")).toString == "web-component::slotted(.content)",
        )
      },
      test("part pseudo-element renders correctly") {
        assertTrue(
          element("my-button").part("label").toString == "my-button::part(label)",
          element("custom-input").part("field").toString == "custom-input::part(field)",
        )
      },
      test("host pseudo-class renders correctly") {
        assertTrue(
          element("my-component").host.toString == "my-component:host",
          element("custom-element").host(`class`("expanded")).toString == "custom-element:host(.expanded)",
        )
      },
      test("host-context pseudo-class renders correctly") {
        assertTrue(
          element("my-component")
            .hostContext(`class`("dark-theme"))
            .toString == "my-component:host-context(.dark-theme)",
          element("custom-card").hostContext(div & `class`("mobile")).toString == "custom-card:host-context(div.mobile)",
        )
      },
    ),
    suite("Complex Selector Rendering")(
      test("navigation selectors render correctly") {
        val navActive = nav > ul > li.and(`class`("active")) > a.hover
        assertTrue(navActive.toString == "nav > ul > li.active > a:hover")
      },
      test("form selectors render correctly") {
        val formError =
          form > div.and(`class`("field-group")) > input.withAttribute("type", "email").focus.and(`class`("error"))
        assertTrue(formError.toString == """form > div.field-group > input[type="email"]:focus.error""")
      },
      test("article selectors render correctly") {
        val articleSelector = article.withAttribute("data-category", "tech") > header > h1.not(`class`("draft"))
        assertTrue(articleSelector.toString == """article[data-category="tech"] > header > h1:not(.draft)""")
      },
      test("layout selectors render correctly") {
        val layoutSelector = div.and(`class`("container")) > div.and(`class`("row")) > div.withAttributeContaining(
          "class",
          "col-",
        ) > p.firstChild
        assertTrue(layoutSelector.toString == """div.container > div.row > div[class*="col-"] > p:first-child""")
      },
      test("table selectors render correctly") {
        val tableCell = table > tr.nthChild("even") > td.before
        assertTrue(tableCell.toString == "table > tr:nth-child(even) > td::before")
      },
    ),
    suite("Web Component Complex Selectors")(
      test("shadow DOM selectors render correctly") {
        val shadowSelector = element("custom-card").host > element("slot").slotted(div.and(`class`("content")))
        assertTrue(shadowSelector.toString == "custom-card:host > slot::slotted(div.content)")
      },
      test("themed component selectors render correctly") {
        val themedSelector = element("ui-button").hostContext(`class`("dark-mode")).part("label").hover
        assertTrue(themedSelector.toString == "ui-button:host-context(.dark-mode)::part(label):hover")
      },
      test("nested component selectors render correctly") {
        val nestedSelector =
          element("app-shell").hostContext(div.and(`class`("mobile"))) > element("main-content").slotted > p.firstChild
        assertTrue(
          nestedSelector.toString == "app-shell:host-context(div.mobile) > main-content::slotted(*) > p:first-child",
        )
      },
    ),
    suite("Edge Cases and Special Characters")(
      test("empty selectors handle correctly") {
        assertTrue(
          universal.and(`class`("")).toString == "*.",
          div.withAttribute("", "value").toString == """div[="value"]""",
        )
      },
      test("special characters in values are preserved") {
        assertTrue(
          div.withAttribute("data-config", """{"key": "value"}""").render == """div[data-config="{"key": "value"}"]""",
          `class`("my-class_with-special.chars").render == ".my-class_with-special.chars",
        )
      },
      test("nested quotes in attribute values") {
        val complexAttr = div.withAttribute("data-json", """{"title": "Hello \"World\""}""")
        assertTrue(complexAttr.toString.contains("""data-json="{"title": "Hello \"World\""""))
      },
    ),
    suite("Operator Precedence and Chaining")(
      test("complex operator chaining renders correctly") {
        val complexChain =
          div.and(`class`("main")) > section.withAttribute("id") > (article | aside) > p.firstChild.hover
        assertTrue(complexChain.toString == "div.main > section[id] > article, aside > p:first-child:hover")
      },
      test("multiple attributes chain correctly") {
        val multiAttribute = input
          .withAttribute("type", "password")
          .withAttribute("required")
          .focus
          .and(`class`("valid"))
        assertTrue(multiAttribute.toString == """input[type="password"][required]:focus.valid""")
      },
      test("deeply nested combinators render correctly") {
        val deepNested = div > (span > (em > strong.and(`class`("highlight"))))
        assertTrue(deepNested.toString == "div > span > em > strong.highlight")
      },
      test("complex selectors with many attributes") {
        val complexSelector = input
          .withAttribute("type", "text")
          .withAttribute("name", "username")
          .withAttribute("required")
          .withAttributeContaining("class", "form")
          .withAttributeStarting("data-", "user")
          .focus
          .and(`class`("valid"))
          .and(`class`("filled"))

        val expected =
          """input[type="text"][name="username"][required][class*="form"][data-^="user"]:focus.valid.filled"""
        assertTrue(complexSelector.toString.nonEmpty && complexSelector.toString == expected)
      },
    ),
  )
}
