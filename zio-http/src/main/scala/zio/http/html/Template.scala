package zio.http.html

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * A ZIO Http styled general purpose templates
 */
object Template {

  def container(heading: CharSequence)(element: Html): Html = {
    html(
      head(
        title(s"ZIO Http - ${heading}"),
        style("""
                | body {
                |   font-family: monospace;
                |   font-size: 16px;
                |   background-color: #edede0;
                | }
                |""".stripMargin),
      ),
      body(
        div(
          styles := Seq("margin" -> "auto", "padding" -> "2em 4em", "max-width" -> "80%"),
          h1(heading),
          element,
        ),
      ),
    )
  }
}
