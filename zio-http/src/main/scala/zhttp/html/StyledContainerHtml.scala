package zhttp.html

/**
 * A ZIO Http styled container for HTML templating.
 */
object StyledContainerHtml {
  def apply(heading: String)(element: Html): Html = {
    html(
      head(
        title(s"ZIO Http - ${heading}"),
        style("""
                | body {
                |   font-family: monospace;
                |   font-size: 16px;
                | }
                |""".stripMargin),
      ),
      body(
        div(
          styles := Seq("margin" -> "auto", "padding" -> "2em 4em", "max-width" -> "1024px"),
          h1(heading),
          element,
        ),
      ),
    )
  }
}
