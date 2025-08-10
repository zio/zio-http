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

package zio.http.template2

import zio.test._

/**
 * Comprehensive specs for DOM rendering, comparing template2 to the original
 * template package. Tests the specific rendering rules found in the original
 * template implementation.
 */
object DomRenderingSpec extends ZIOSpecDefault {

  def spec = suite("DOM Rendering Specs")(
    // Test void element rendering rules (based on original template logic)
    suite("Void Element Rendering")(
      test("void element with no attributes should render as self-closing") {
        val element = br
        assertTrue(element.render == "<br/>")
      },
      test("void element with attributes should render with attributes and self-closing") {
        val element = input(`type` := "text", name := "username")
        assertTrue(element.render == """<input type="text" name="username"/>""")
      },
      test("void element with multiple attributes should render correctly") {
        val element  = img(src := "/image.jpg", alt := "Image", width := "100", height := "100")
        val expected = """<img src="/image.jpg" alt="Image" width="100" height="100"/>"""
        assertTrue(element.render == expected)
      },
      test("all void elements should be handled correctly") {
        val voidElements = List(
          area,
          base,
          br,
          col,
          embed,
          hr,
          img,
          input,
          link,
          meta,
          param,
          source,
          track,
          wbr,
        )

        assertTrue(voidElements.forall { elem =>
          val rendered = elem.render
          rendered.startsWith("<") && rendered.endsWith("/>") && !rendered.contains("><")
        })
      },
    ),

    // Test non-void element rendering rules
    suite("Non-Void Element Rendering")(
      test("element with no attributes and no children should render with opening and closing tags") {
        val element = div
        assertTrue(element.render == "<div></div>")
      },
      test("element with attributes but no children should render correctly") {
        val element = div(className := "container", id := "main")
        assertTrue(element.render == """<div class="container" id="main"></div>""")
      },
      test("element with children but no attributes should render correctly") {
        val element = div("Hello World")
        assertTrue(element.render == "<div>Hello World</div>")
      },
      test("element with both attributes and children should render correctly") {
        val element = div(className := "container", "Hello World")
        assertTrue(element.render == """<div class="container">Hello World</div>""")
      },
    ),

    // Test attribute rendering rules (based on original template logic)
    suite("Attribute Rendering")(
      test("string attributes should be quoted and HTML escaped") {
        val element  = div(titleAttr := """Test "quoted" & <escaped>""")
        val expected = """<div title="Test &quot;quoted&quot; &amp; &lt;escaped&gt;"></div>"""
        assertTrue(element.render == expected)
      },
      test("boolean attributes when true should render just the name") {
        val element  = input(required, disabled, checked)
        val rendered = element.render
        assertTrue(
          rendered.contains("required") &&
            rendered.contains("disabled") &&
            rendered.contains("checked") &&
            !rendered.contains("required=") &&
            !rendered.contains("disabled=") &&
            !rendered.contains("checked="),
        )
      },
      test("boolean attributes when false should not render") {
        val falseRequired = Dom.boolAttr("required", enabled = false)
        val element       = input(falseRequired)
        assertTrue(!element.render.contains("required"))
      },
      test("multiple attributes should be space-separated") {
        val element  = div(id := "main", className := "container", titleAttr := "Test")
        val rendered = element.render
        assertTrue(
          rendered.contains("""id="main"""") &&
            rendered.contains("""class="container"""") &&
            rendered.contains("""title="Test"""") &&
            rendered.count(_ == ' ') >= 2, // At least 2 spaces between attributes
        )
      },
    ),

    // Test text content rendering and HTML escaping
    suite("Text Content Rendering")(
      test("plain text should render as-is") {
        val element = p("Hello World")
        assertTrue(element.render == "<p>Hello World</p>")
      },
      test("text with HTML special characters should be escaped") {
        val element  = p("Hello <world> & \"friends\"")
        val expected = "<p>Hello &lt;world&gt; &amp; &quot;friends&quot;</p>"
        assertTrue(element.render == expected)
      },
      test("raw HTML should not be escaped") {
        val element = div(raw("<strong>Bold</strong>"))
        assertTrue(element.render == "<div><strong>Bold</strong></div>")
      },
      test("mixed text and elements should render correctly") {
        val element = p("Hello ", strong("world"), "!")
        assertTrue(element.render == "<p>Hello <strong>world</strong>!</p>")
      },
    ),

    // Test indentation rendering (comparing to original template logic)
    suite("Indentation Rendering")(
      test("no indentation should render on single line") {
        val element  = div(
          h1("Title"),
          p("Content"),
        )
        val rendered = element.render(indentation = false)
        assertTrue(!rendered.contains("\n"))
      },
      test("with indentation should render with proper formatting") {
        val element  = div(
          h1("Title"),
          p("Content"),
        )
        val rendered = element.render(indentation = true)
        assertTrue(
          rendered.contains("\n") &&
            rendered.contains("  <h1>") && // Indented child
            rendered.contains("  <p>"),    // Indented child
        )
      },
      test("nested elements should have proper indentation levels") {
        val element  = div(
          section(
            h1("Title"),
            p("Content"),
          ),
        )
        val rendered = element.render(indentation = true)
        assertTrue(
          rendered.contains("  <section>") && // Level 1
            rendered.contains("    <h1>") &&  // Level 2
            rendered.contains("    <p>"),     // Level 2
        )
      },
      test("single text content should not add extra newlines") {
        val element  = p("Simple text")
        val rendered = element.render(indentation = true)
        assertTrue(rendered == "<p>Simple text</p>")
      },
    ),

    // Test script and style element special handling (from original template)
    suite("Special Element Handling")(
      test("script content should not be HTML escaped") {
        val element  = script("""var x = "<test>";""")
        val rendered = element.render
        assertTrue(rendered.contains("""var x = "<test>";"""))
      },
      test("style content should not be HTML escaped") {
        val element  = style(""".class { content: "<>"; }""")
        val rendered = element.render
        assertTrue(rendered.contains(""".class { content: "<>"; }"""))
      },
    ),

    // Test complex nested structures
    suite("Complex Structure Rendering")(
      test("nested table structure should render correctly") {
        val element = table(
          thead(
            tr(
              th("Name"),
              th("Age"),
            ),
          ),
          tbody(
            tr(
              td("John"),
              td("25"),
            ),
            tr(
              td("Jane"),
              td("30"),
            ),
          ),
        )

        val rendered = element.render
        assertTrue(
          rendered.contains("<table>") &&
            rendered.contains("<thead>") &&
            rendered.contains("<th>Name</th>") &&
            rendered.contains("<td>John</td>") &&
            rendered.contains("</table>"),
        )
      },
      test("form with mixed elements should render correctly") {
        val element = form(action := "/submit", method := "post")(
          div(className := "form-group")(
            label(`for`  := "name", "Name:"),
            input(`type` := "text", id := "name", required),
          ),
          button(`type` := "submit", "Submit"),
        )

        val rendered = element.render
        assertTrue(
          rendered.contains("""<form action="/submit" method="post">""") &&
            rendered.contains("""<label for="name">Name:</label>""") &&
            rendered.contains("""<input type="text" id="name" required/>""") &&
            rendered.contains("""<button type="submit">Submit</button>"""),
        )
      },
    ),

    // Test empty and fragment rendering
    suite("Empty and Fragment Rendering")(
      test("empty element should render as empty string") {
        assertTrue(empty.render == "")
      },
      test("fragment should render children without wrapper") {
        val frag = fragment(
          p("First"),
          p("Second"),
        )
        assertTrue(frag.render == "<p>First</p><p>Second</p>")
      },
      test("nested fragments should flatten correctly") {
        val frag = fragment(
          p("First"),
          fragment(
            p("Second"),
            p("Third"),
          ),
        )
        assertTrue(frag.render == "<p>First</p><p>Second</p><p>Third</p>")
      },
    ),

    // Comparison tests with original template package
    suite("Template Package Compatibility")(
      test("void elements render identically to original template") {
        import zio.http.{template => original}

        // Compare br element
        val template2Br = br.render
        val originalBr  = original.Dom.element("br").encode.toString
        assertTrue(template2Br == originalBr)
      },
      test("elements with attributes render similarly to original") {
        import zio.http.{template => original}

        // Compare div with class
        val template2Div = div(className := "test").render
        val originalDiv  = original.Dom.element("div", original.Dom.attr("class", "test")).encode.toString
        assertTrue(template2Div == originalDiv)
      },
      test("text escaping matches original template behavior") {
        import zio.http.{template => original}

        val testText      = "Hello <world> & \"friends\""
        val template2Text = p(testText).render
        val originalText  = original.Dom.element("p", original.Dom.text(testText)).encode.toString

        assertTrue(template2Text == originalText)
      },
    ),
  )
}
