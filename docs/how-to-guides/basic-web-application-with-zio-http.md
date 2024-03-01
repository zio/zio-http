---
id: basic-web-application-with-zio-http
title: "Basic web Application With Zio Http"
---

This guide demonstrates the process of using ZIO HTTP and its HTML templating capabilities to build and run a basic web application that generates and serves HTML content.

## Code

```scala
package example

import zio._

import zio.http._

object HtmlTemplating extends ZIOAppDefault {
  // Importing everything from `zio.html`
  import zio.http.html._

  def app: Handler[Any, Nothing, Any, Response] = {
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

  def run = Server.serve(app.toHttp.withDefaultErrorResponse).provide(Server.default)
}
```