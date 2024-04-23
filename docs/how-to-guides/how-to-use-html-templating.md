---
id: how-to-use-html-templating
title: "How to Use HTML Templating in ZIO HTTP"
---

This guide demonstrates how to use ZIO HTTP's built-in HTML templating capabilities to generate and serve HTML content in your web application.

## Setting up the Application

First, we need to import the necessary dependencies and define the main application object:

```scala mdoc:silent
import zio._
import zio.http._
import zio.http.template._ // Importing everything from `zio.html`

```

We import the `zio.http.template` module, which provides the HTML templating functionality.

## Defining the HTTP Handler

Next, we define the HTTP handler that will generate the HTML response:

```scala mdoc:silent
def app: HttpApp[Any] = {
  // Html response takes in a `Html` instance.
  Handler.html {
    // Support for default Html tags
    html(
      // Support for child nodes
      head(
        title("ZIO Http"),
      ),
      body(
        // ...
      )
    )
  }.toHttpApp
}
```

The `Handler.html` function takes a `Html` instance as its argument, which represents the HTML content to be rendered. The `html`, `head`, `title`, and `body` functions are used to create the corresponding HTML elements.

## Building the HTML Content

Inside the `body` element, we can construct the desired HTML structure using the provided functions:

```scala mdoc:silent
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
)
```

This example demonstrates several features of the HTML templating API:

- Adding CSS classes using the `css` modifier
- Applying inline styles using the `styles` modifier
- Setting attributes (e.g., `href`) on HTML elements
- Generating a sequence of HTML elements using a `map` operation

## Running the Server

Finally, we can run the server and serve the HTML content:

```scala mdoc:silent
def run = Server.serve(app).provide(Server.default)
```

The `Server.serve` function takes the `HttpApp` instance and starts the server, serving the generated HTML content.