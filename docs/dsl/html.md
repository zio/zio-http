---
id: html
title: Html
---

The package `zio.http.html._` contains lightweight helpers for generating statically typed, safe html similiar in spirit to `scalatags`. 

## Html and DOM

### Html from string

One possible way is to create an instance of `Html` directly from a `String` value, with the obvious drawback of not having checks
from the compiler:

```scala mdoc:silent
import zio.http.html._

val divHtml1: Html = Html.fromString("""<div class="container1 container2"><a href="http://zio.dev">ZIO Homepage</a></div>""")
```

### Html from constructors

In order to improve on type safety one could use `Html` with `Dom` constructor functions, with the drawback that the resulting
code is much more verbose:

```scala mdoc:silent
import zio.http.html._

val divHtml2: Html = Html.fromDomElement(
  Dom.element(
    "div", 
    Dom.attr("class", "container1 container2"), 
    Dom.element(
      "a", 
      Dom.attr("href", "http://zio.dev"), 
      Dom.text("ZIO Homepage")
    )
  )
)
```

Please note that both values `divHtml1` and `divHtml2` produce identical html output.

### Html from Tag API

Practically one would very likely not use one of the above mentioned versions but instead use the `Tag API`. That API lets one use not only html 
elements like `div` or `a` but also html attributes like `hrefAttr` or `styleAttr` as scala functions. By convention values of html attributes 
are suffixed `attr` to easily distinguish those from html elements: 

```scala mdoc:silent
import zio.http.html._

val divHtml3: Html = div(
  classAttr := "container1" :: "container2" :: Nil,
  a(hrefAttr := "http://zio.dev", "ZIO Homepage")
)
```

Also `divHtml3` produces identical html output as `divHtml1` and `divHtml2`. 

Html elements like `div` or `a` are represented as values of `PartialElement` which have an `apply` method for nesting html elements, 
html attributes and text values. Html attributes are represented as values of `PartialAttribute` which provides an operator `:=` for "assigning" 
attribute values. Besides `:=` attributes also have an `apply` method that provides an alternative syntax e.g. instead
of `a(hrefAttr := "http://zio.dev", "ZIO Homepage")` one can use `a(hrefAttr("http://zio.dev"), "ZIO Homepage")`. 

### Html composition

One can compose values of `Html` sequentially using the operator `++` to produce a larger `Html`: 

```scala mdoc:silent
import zio.http.html._

val fullHtml: Html = divHtml1 ++ divHtml2 ++ divHtml3
```

## Html response

One can create a successful http response in routing code from a given value of `Html` with `Response.html`.
