package zhttp.html

/**
 * A ZIO Http styled container for HTML templating.
 */
object StyledContainerHtml {
  def apply(title: String)(element: Html): Html = {
    html(
      head(),
      body(
        h1(title),
        element,
      ),
    )
  }
}
