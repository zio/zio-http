package example

import zhttp.http._
import zhttp.service.Server
import zio._

object HtmlTemplating extends App {
  // Importing everything from `zhttp.html`
  import zhttp.html._

  def app: HttpApp[Any, Nothing] = {
    // Html response takes in a `Html` instance.
    Http.html {

      // Support for default Html tags
      html(
        // Support for child nodes
        head(
          title("ZIO Http"),
        ),
        body(
          div(
            // Support for css class names
            css := "container" :: "text-align-left" :: Nil,
            h1("Hello World"),
            ul(
              // Support for inline css
              styles := Seq("list-style" -> "none"),
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
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
