package zio.http.template2

import zio.test._

object MultiValueAttributeSpec extends ZIOSpecDefault {

  import AttributeSeparator._
  import Dom._

  def spec: Spec[Any, Any] = suite("MultiValueAttributeSpec")(
    suite("AttributeSeparator")(
      test("Space separator should render with spaces") {
        assertTrue(Space.render == " ", Space.toString == " ")
      },
      test("Comma separator should render with commas") {
        assertTrue(Comma.render == ",", Comma.toString == ",")
      },
      test("Semicolon separator should render with semicolons") {
        assertTrue(Semicolon.render == ";", Semicolon.toString == ";")
      },
      test("Custom separator should render with custom string") {
        val custom = AttributeSeparator.Custom(" | ")
        assertTrue(custom.render == " | ", custom.toString == " | ")
      },
    ),
    suite("MultiValue AttributeValue")(
      test("should join values with space separator") {
        val multiValue = AttributeValue.MultiValue(Vector("foo", "bar", "baz"), Space)
        assertTrue(multiValue.toString == "foo bar baz")
      },
      test("should join values with comma separator") {
        val multiValue = AttributeValue.MultiValue(Vector("red", "blue", "green"), Comma)
        assertTrue(multiValue.toString == "red,blue,green")
      },
      test("should join values with semicolon separator") {
        val multiValue = AttributeValue.MultiValue(Vector("tag1", "tag2", "tag3"), Semicolon)
        assertTrue(multiValue.toString == "tag1;tag2;tag3")
      },
      test("should join values with custom separator") {
        val custom     = AttributeSeparator.Custom(" -> ")
        val multiValue = AttributeValue.MultiValue(Vector("a", "b", "c"), custom)
        assertTrue(multiValue.toString == "a -> b -> c")
      },
      test("should handle empty values") {
        val multiValue = AttributeValue.MultiValue(Vector.empty, Space)
        assertTrue(multiValue.toString == "")
      },
    ),
    suite("PartialMultiAttribute")(
      test("should create CompleteAttribute with varargs") {
        val partial  = PartialMultiAttribute("class", Space)
        val complete = partial := ("foo", "bar", "baz")
        assertTrue(complete.name == "class", complete.value.toString == "foo bar baz")
      },
      test("should create CompleteAttribute with Iterable") {
        val partial  = PartialMultiAttribute("class", Space)
        val complete = partial := List("container", "main", "active")
        assertTrue(complete.name == "class", complete.value.toString == "container main active")
      },
      test("should use apply method with varargs") {
        val partial  = PartialMultiAttribute("data-tags", Comma)
        val complete = partial("tag1", "tag2", "tag3")
        assertTrue(complete.name == "data-tags", complete.value.toString == "tag1,tag2,tag3")
      },
      test("should use apply method with Iterable") {
        val partial  = PartialMultiAttribute("style", Semicolon)
        val complete = partial(Vector("color: red", "font-size: 14px"))
        assertTrue(complete.name == "style", complete.value.toString == "color: red;font-size: 14px")
      },
    ),
    suite("multiAttr factory methods")(
      test("should create partial multi-attribute with default separator") {
        val partial = multiAttr("class")
        assertTrue(partial.name == "class", partial.separator == Space)
      },
      test("should create partial multi-attribute with custom separator") {
        val partial = multiAttr("data-items", Comma)
        assertTrue(partial.name == "data-items", partial.separator == Comma)
      },
      test("should create complete attribute from Iterable") {
        val complete = multiAttr("class", List("btn", "btn-primary"))
        assertTrue(complete.name == "class", complete.value.toString == "btn btn-primary")
      },
      test("should create complete attribute from varargs with custom separator") {
        val complete = multiAttr("style", Semicolon, "color: blue", "margin: 5px")
        assertTrue(complete.name == "style", complete.value.toString == "color: blue;margin: 5px")
      },
    ),
    suite("classAttr convenience methods")(
      test("should create class attribute from varargs") {
        val attr = `class`("container", "fluid", "main")
        assertTrue(attr.name == "class", attr.value.toString == "container fluid main")
      },
      test("should create class attribute from Iterable") {
        val classes = List("nav", "navbar", "navbar-expand-lg")
        val attr    = `class`(classes)
        assertTrue(attr.name == "class", attr.value.toString == "nav navbar navbar-expand-lg")
      },
      test("should handle empty class list") {
        val attr = `class`(List.empty[String])
        assertTrue(attr.name == "class", attr.value.toString == "")
      },
    ),
    suite("HTML rendering")(
      test("should render element with multi-value class attribute") {
        val elem = element("div")(`class`("container", "main", "active"), Dom.text("Content"))
        val html = elem.render
        assertTrue(html == """<div class="container main active">Content</div>""")
      },
      test("should render element with custom separator attribute") {
        val elem = element("div")(
          multiAttr("data-tags", Semicolon)("tag1", "tag2", "tag3"),
          Dom.text("Tagged"),
        )
        val html = elem.render
        assertTrue(html == """<div data-tags="tag1;tag2;tag3">Tagged</div>""")
      },
      test("should render element with comma-separated attribute") {
        val elem = element("meta")(
          multiAttr("content", Comma)("width=device-width", "initial-scale=1"),
        )
        val html = elem.render
        assertTrue(html == """<meta content="width=device-width,initial-scale=1"/>""")
      },
      test("should handle HTML escaping in multi-value attributes") {
        val elem = element("div")(
          multiAttr("data-values", Space)("value with \"quotes\"", "value with <tags>"),
        )
        val html = elem.render
        assertTrue(html.contains("""data-values="value with &quot;quotes&quot; value with &lt;tags&gt;""""))
      },
      test("should not render empty multi-value attributes") {
        val elem = element("div")(
          multiAttr("class", Vector.empty[String]),
          Dom.text("Empty classes"),
        )
        val html = elem.render
        assertTrue(!html.contains("class="))
      },
    ),
    suite("HtmlElements integration")(
      test("predefined class attribute should work with multi-values") {
        val elem = div(`class` := ("container", "main"), Dom.text("Content"))
        val html = elem.render
        assertTrue(html == """<div class="container main">Content</div>""")
      },
      test("predefined className attribute should work with multi-values") {
        val elem = div(className := List("btn", "btn-primary", "btn-large"), Dom.text("Button"))
        val html = elem.render
        assertTrue(html == """<div class="btn btn-primary btn-large">Button</div>""")
      },
      test("predefined rel attribute should work with multi-values") {
        val elem = link(rel := ("stylesheet", "preload"), href := "/styles.css")
        val html = elem.render
        assertTrue(html == """<link rel="stylesheet preload" href="/styles.css"/>""")
      },
      test("ARIA attributes should work with multi-values") {
        val elem = div(
          ariaDescribedby := ("desc1", "desc2", "desc3"),
          ariaLabelledby  := List("label1", "label2"),
          Dom.text("ARIA element"),
        )
        val html = elem.render
        assertTrue(
          html.contains("""aria-describedby="desc1 desc2 desc3""""),
          html.contains("""aria-labelledby="label1 label2""""),
        )
      },
    ),
    suite("Complex examples")(
      test("should handle multiple multi-value attributes") {
        val elem = element("div")(
          `class`("container", "fluid"),
          multiAttr("data-tags", Comma)("frontend", "react", "typescript"),
          multiAttr("style", Semicolon)("color: red", "font-size: 16px"),
          Dom.text("Complex element"),
        )
        val html = elem.render
        assertTrue(
          html.contains("""class="container fluid""""),
          html.contains("""data-tags="frontend,react,typescript""""),
          html.contains("""style="color: red;font-size: 16px""""),
        )
      },
      test("should work with custom separators") {
        val customSep = AttributeSeparator.Custom(" | ")
        val elem      = element("div")(
          multiAttr("data-breadcrumb", customSep)("Home", "Products", "Electronics"),
          Dom.text("Breadcrumb"),
        )
        val html      = elem.render
        assertTrue(html.contains("""data-breadcrumb="Home | Products | Electronics""""))
      },
    ),
    suite("Edge cases")(
      test("should handle single value in multi-attribute") {
        val elem = element("div")(`class`("single"))
        val html = elem.render
        assertTrue(html == """<div class="single"></div>""")
      },
      test("should handle values with special characters") {
        val elem = element("div")(
          multiAttr("data-json", Comma)("""{"key":"value"}""", """{"another":"data"}"""),
        )
        val html = elem.render
        assertTrue(
          html.contains("""data-json="{&quot;key&quot;:&quot;value&quot;},{&quot;another&quot;:&quot;data&quot;}""""),
        )
      },
      test("should work with mixed attribute types") {
        val elem = element("button")(
          `class`("btn", "btn-primary"),
          attr("type") := "button",
          boolAttr("disabled", enabled = true),
          Dom.text("Disabled Button"),
        )
        val html = elem.render
        assertTrue(
          html.contains("""class="btn btn-primary""""),
          html.contains("""type="button""""),
          html.contains("disabled"),
        )
      },
    ),
  )
}
