//> using dep "dev.zio::zio-http:3.4.1"

package example

import zio._

import zio.http._

object HtmlTemplating extends ZIOAppDefault {
  // Importing everything from `zio.html`
  import zio.http.template._

  def routes: Routes[Any, Response] = {
    // Html response takes in a `Html` instance.
    Handler.html {

      // Support for default Html tags
      html(
        // Support for child nodes
        head(
          title("ZIO Http"),
        ),
        body(
          div(
            // Support for css class names
            css := "container text-align-left",
            h1("Hello World"),
            ul(
              // Support for inline css
              styles := "list-style: none",
              li(
                // Support for attributes
                a(href := "/hello/world", "Hello World"),
              ),
              li(
                a(href := "/hello/world/again", "Hello World Again"),
              ),

              // Support for Seq of Html elements
              (2 to 10) map { i =>
                li(
                  a(href := s"/hello/world/i", s"Hello World $i"),
                )
              },
            ),
          ),
        ),
      )
    }
  }.toRoutes

  def run = Server.serve(routes).provide(Server.default)
}
