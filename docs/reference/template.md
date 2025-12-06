---
id: template
title: "Template DSL"
sidebar_label: Template
---

ZIO HTTP Template2 is a modern, type-safe HTML templating DSL for Scala that allows you to write HTML, CSS, and JavaScript directly in your Scala code with full compile-time checking. 

### Why Template2?

- **Type Safety**: Catch HTML errors at compile time
- **Composability**: Build reusable components as Scala functions
- **Pure Scala**: No separate template files to maintain, everything is in Scala

## Your First HTML Page using Template2

Let's create a simple "Hello World" page:

```scala mdoc:silent
import zio.http.template2._

val page: Dom =
  html(
    head(
      title("Hello World")
    ),
    body(
      h1("Hello, ZIO HTTP Template2!"),
      p("This is my first template.")
    )
  )
```

Rendering the above code (`page.render(indentation = true)`) will produce the following HTML:

```html
<html>
<head>
    <title>Hello World</title>
</head>
<body>
<h1>Hello, ZIO HTTP Template2!</h1>
<p>This is my first template.</p>
</body>
</html>
```

## Serving HTML Page with ZIO HTTP

To serve the HTML page we created using `template2`, we can set up a simple server as follows:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.template2._

object HelloWorld extends ZIOAppDefault {

  val page: Dom = html(
    head(
      title("Hello World")
    ),
    body(
      h1("Hello, ZIO HTTP Template2!"),
      p("This is my first template.")
    )
  )

  val run = Server
    .serve(
      Method.GET / Root -> handler {
        Response.html(page)
      }
    )
    .provide(Server.default)
}
```

No need to render the HTML manually; `Response.html` takes care of it for you.

## Attributes

We have three types of attributes: Partial Attributes, Boolean Attributes, and Multi-Value Attributes.

1. **Partial attributes** require a value to become complete, e.g., `id`, `href`, and `name`. They use the `:=` operator or `apply()` method:

```scala mdoc:compile-only
// Using := operator
div(
  id := "container",
  href := "https://example.com",
  name := "username"
)

// Using apply() method
div(
  id("container"),
  href("https://example.com"),
  name("username")
)
```

There are many predefined partial attributes available for use. However, if you need to create a custom one, you can use the `custom()` helper. For example:

```scala mdoc:compile-only
button(
  custom("onclick") := js"handleClick()"
)("Click here!")
```

Which renders as:

```html
<button onclick="handleClick()">Click here!</button>
```

2. **Boolean attributes** represent presence/absence states (like `disabled`, `checked`, `hidden`):

```scala mdoc:compile-only
input(
  required,
  autofocus
)
```

If we render the above input, it will produce the following HTML:

```html
<input required autofocus/>
```

3. **Multi-value attributes** handle space-separated or comma-separated values, commonly used for CSS classes:

```scala mdoc:compile-only
// CSS classes (space-separated by default)
div(
  `class` := ("container", "active", "large")
)

// Alternative using className
div(
  className := ("btn", "btn-primary")
)

// With Iterable
div(
  className := List("card", "shadow")
)
```

If we render the first div, it will produce the following HTML:

```html
<div class="container active large"></div>
```

To control how multi-value attributes are joined, use `multiAttr` with `AttributeSeparator`:

```scala mdoc:compile-only
// Space-separated (default)
div(Dom.multiAttr("class", List("foo", "bar")))
// Renders as: <div class="foo bar"></div>

// Comma-separated
div(Dom.multiAttr("data-list", AttributeSeparator.Comma, "a", "b", "c"))
// Renders as: <div data-list="a,b,c"></div>

// Semicolon-separated
div(styleAttr := "color: red; font-size: 14px")
// Renders as: <div style="color: red;font-size: 14px"></div>

// Custom separator
div(Dom.multiAttr("custom", AttributeSeparator.Custom("|"), "x", "y"))
// Renders as: <div custom="x|y"></div>
```

Here is an example of a complete form using various attribute types:

```scala mdoc:compile-only
form(
  action := "/submit",
  method := "POST",
  input(
    `type`      := "text",
    name        := "email",
    placeholder := "Enter email",
    required,
    maxlength   := 100,
  ),
  button(
    `type` := "submit",
  )("Submit")
)
```

This will render as:

```html
<form action="/submit" method="POST">
  <input name="email" placeholder="Enter email" maxlength="100" type="text" required/>
  <button type="submit">Submit</button>
</form>
```

### Dynamic Attribute Management

You may want to manipulate attributes based on runtime conditions. You can use operations like `attr`, `addAttributes`, and `removeAttr`:

```scala
val element = div(id := "myDiv", `class` := "container")

// Add or update an attribute
val updated = element.attr("title", "Updated title")

// Add multiple attributes
val updatedWithData =
  div(name("shopping-card")).addAttributes(
    data("item-id") := "98765",
    data("category") := "electronics",
  )

// Remove an attribute
val removed = updated.removeAttr("title")
```

### Conditional Attributes

Use `when` and `whenSome` for conditional attribute application:

```scala mdoc:invisible
val isActive: Boolean = true
val maybeEmail: Option[String] = None
```

```scala mdoc:compile-only
div(
  id := "container"
).when(isActive)(
  `class` := "active",
  ariaExpanded := true
)

div(
  id := "user"
).whenSome(maybeEmail) { email =>
  Seq(
    data("email") := email,
    titleAttr := s"User: $email"
  )
}
```

## Elements

Before we dive into creating elements, here's a simple example of an HTML page using various elements:

```scala mdoc:compile-only
import zio.http.template2.{main => mainTag, _}

html(lang := "en")(
  head(
    meta(charset := "UTF-8"),
    title("My Page"),
    style.inlineResource("styles/main.css"),
    script.externalJs("https://cdn.example.com/lib.js")
  ),
  body(
    header(
      nav(
        ul(
          li(a(href := "/")("Home")),
          li(a(href := "/about")("About")),
        ),
      ),
    ),
    mainTag(
      article(
        h1("Article Title"),
        p("Article content..."),
      ),
    ),
    footer(
      p("© 2024"),
    ),
  ),
)
```

### Generic Elements

Most HTML elements are predefined for you to use directly by their names (e.g., `div`, `span`, `p`) without any extra parentheses or arguments:

```scala mdoc:compile-only
br
// Renders as: <br/>

div
// Renders as: <div></div>

img
// Renders as: <img/>
```

To give you an overview, here's a categorized list of some of the predefined elements:

| Category         | Elements                                                                                 |
|:-----------------|:-----------------------------------------------------------------------------------------|
| **Text Content** | `p`, `span`, `div`, `h1`, `h2`, `h3`, `h4`, `h5`, `h6`, `blockquote`, `pre`, `code`      |
| **Forms**        | `form`, `input`, `button`, `select`, `option`, `textarea`, `label`, `fieldset`, `legend` |
| **Lists**        | `ul`, `ol`, `li`, `dl`, `dt`, `dd`                                                       |
| **Tables**       | `table`, `thead`, `tbody`, `tfoot`, `tr`, `th`, `td`, `caption`, `colgroup`, `col`       |
| **Media**        | `img`, `video`, `audio`, `source`, `track`, `canvas`, `svg`                              |
| **Semantic**     | `header`, `footer`, `main`, `nav`, `article`, `section`, `aside`                         |
| **Interactive**  | `details`, `summary`, `dialog`                                                           |
| **Metadata**     | `head`, `title`, `meta`, `link`, `base`, `style`, `script`                               |

They can accept attributes and children via the `apply` method. The `apply` method takes a variable number of `Modifier` arguments (which can be attributes or children):

```scala mdoc:compile-only
div(id := "main") // Attribute
// Renders as: <div id="main"></div>

div("Hello World") // Child
// Renders as: <div>Hello World</div>

div(p("Paragraph")) // nested Child
// Renders as: <div><p>Paragraph</p></div>

// Mixing attributes and children
div(
  id := "main",                    // Attribute
  `class` := "container",          // Attribute
  p("First paragraph"),            // Child
  data("section") := "intro",      // Attribute
  p("Second paragraph")            // Child
)
// Renders as: 
// <div id="main" class="container" data-section="intro">
//   <p>First paragraph</p>
//   <p>Second paragraph</p>
// </div>

// The same as above but with grouped children in a separate block
div(
  id              := "main",      // Attribute
  `class`         := "container", // Attribute
  data("section") := "intro",     // Attribute
)(
  p("First paragraph"), // Child
  p("Second paragraph"), // Child
)
// Renders as:
// <div id="main" class="container" data-section="intro">
//   <p>First paragraph</p>
//   <p>Second paragraph</p>
// </div>
```

Please note that void elements (e.g., `br`, `hr`, `input`, ...) are self-closing and cannot have children.

We can map over collections to generate lists of elements:

```scala mdoc:compile-only
val items = List("Apple", "Banana", "Cherry")

ul(
  items.map(item => li(item))
)

// With indices
ol(
  items.zipWithIndex.map { case (item, idx) =>
    li(id := s"item-$idx")(item)
  }
)
```

We can conditionally include children based on parameters:

```scala mdoc:invisible
case class User(name: String, email: String, avatar: Option[String])
val showEmail = true
```

```scala mdoc:compile-only
def userCard(user: User, showEmail: Boolean): Dom.Element = {
  div(`class` := "user-card")(
    h3(user.name),
    if (showEmail) p(user.email) else Dom.empty,
    user.avatar.map(url => img(src := url))
  )
}
```

If we need to create a custom element that is not predefined, we can use the `Dom.element` method:

```scala mdoc:compile-only
Dom.element("custom-tag")(custom("x-property") := "value")("Content here")
// Renders as:  
// <custom-tag x-property="value">Content here</custom-tag>
```

### Script Elements

To include JavaScript in your HTML, you can use the `script` element with various methods for inline and external scripts:

```scala mdoc:compile-only
// Inline JavaScript with Js type
script.inlineJs(js"console.log('Hello from inline JavaScript!');")
// Renders as:
// <script type="text/javascript">console.log('Hello from inline JavaScript!');</script>

// External JavaScript
script.externalJs("https://cdn.example.com/lib.js")
// Renders as:
// <script src="https://cdn.example.com/lib.js" type="text/javascript"></script>

// ES6 Module
script.externalModule("/js/app.js")
// Renders as:
// <script src="/js/app.js" type="module"></script>

// Inline module
script.inlineJs(
  """
    |import { helper } from './utils.js';
    |helper.init();
    |""".stripMargin,
)
// Render as:
// <script type="text/javascript">
//  import { helper } from './utils.js';
//  helper.init();
// </script>

// With attributes
script
  .externalJs("/app.js")
  .async
  .integrity("sha384-...")
  .crossOrigin("anonymous")
// Renders as:
// <script async src="/app.js" type="text/javascript" integrity="sha384-..." crossorigin="anonymous"></script>
```

### Style Elements

Style elements are specialized for CSS content.

To include inline CSS, use the `inlineCss` method:

```scala mdoc:compile-only
// Inline CSS
style.inlineCss(
  css"""
       |body {
       |  margin: 0;
       |  font-family: sans-serif;
       |}
       |""".stripMargin,
)
// Renders as: 
//<style type="text/css">
//  body {
//    margin: 0;
//    font-family: sans-serif;
//  }
//</style>
```

To inline a CSS file from the resource directory, use `inlineResource`:

```scala mdoc:compile-only
// Loading from resources
style.inlineResource("styles/main.css")
```

To point to an external CSS file, you can use the `link` element:

```scala mdoc:compile-only
link(rel := "stylesheet", href := "https://example.com/styles/main.css")
// Renders as: <link rel="stylesheet" href="https://example.com/styles/main.css"/>
```

### Finding, Filtering and Collecting

Using `find`, `filter`, and `collect` methods, we can traverse the `Dom` to locate, select, or extract specific elements based on defined criteria:

```scala
val element = div(
  p(`class` := "important")("Important"),
  p("Normal"),
  span("Other")
)

// Find specific elements
val firstP: Option[Dom] = element.find {
  case el: Dom.Element => el.tag == "p"
  case _ => false
}

// Filter elements
val filtered = element.filter {
  case el: Dom.Element => el.attributes.contains("class")
  case _ => true
}

// Collect all matching elements
val allParagraphs: List[Dom] = element.collect {
  case el: Dom.Element if el.tag == "p" => el
}
```

## Building Reusable Components

We can also build reusable components using functions. Here's an example of a card component:

```scala mdoc:compile-only
def card(
  title: Option[String] = None,
  footer: Option[Dom] = None,
)(content: Dom*): Dom.Element = {
  div(`class` := "card")(
    title.map(t => div(`class` := "card-header")(h5(t))),
    div(`class` := "card-body")(content),
    footer.map(f => div(`class` := "card-footer")(f)),
  )
}

def customButton(
  text: String,
  variant: String = "primary",
  size: String = "md",
): Dom.Element = 
  button(
    `class` := (s"btn-$variant", s"btn-$size"),
    role := "button"
  )(text)

// Usage
card(
  title = Some("User Profile"),
  footer = Some(customButton("Save")),
)(
  p("Name: John Doe"),
  p("Email: john@example.com"),
)
```

## Using Third-Party Template Libraries

If you're running your project with other template libraries like Twirl or Scalate and want to migrate to ZIO HTTP, you can integrate them without rewriting all your templates. After migrating and integrating with ZIO HTTP, you can gradually replace your old templates with ZIO HTTP Template2 templates.

Here's an example of how to integrate [Twirl](https://github.com/playframework/twirl) templates with ZIO HTTP. Assume you have written a Twirl template named `greetUser.scala.html` inside the `mytwirltemplate` package:

```html
@import models.User
@(param: User)
<html>
  <head>
    <title>Greet User Twirl Template</title>
  </head>
  <body>
    <h1>Hello, @param.name!</h1>
    <p>Your email is: @param.email</p>
  </body>
</html>
```

And you have a case class `User` defined as follows:

```scala
package models

import zio.schema.DeriveSchema

case class User(name: String, email: String)

object User {
  implicit val schema = DeriveSchema.gen[User]
}
```

Now you can use this Twirl template inside a ZIO HTTP route as follows:

```scala
import zio.http._
import zio.schema.codec.JsonCodec.schemaBasedBinaryCodec

val greetRoute =
  Method.GET / "greet" -> handler { (req: Request) =>
    for {
      user     <- req.body.to[User]
      response = mytwirltemplate.greetUser.render(user)
    } yield Response(
      body    = Body.fromString(response),
      headers = Headers(Header.ContentType(MediaType.text.html)),
    )
  }
```

You can follow a similar approach to integrate other template libraries with ZIO HTTP.