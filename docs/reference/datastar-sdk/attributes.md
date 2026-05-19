---
id: attributes
title: Datastar HTML Attributes
---

The `zio-http-datastar-sdk` provides extensions to the templating module that allow you to easily add type-safe Datastar attributes to your HTML elements:

| ZIO HTTP Attribute        | Datastar HTML Attribute       | Description                                                                                                  |
|---------------------------|-------------------------------|--------------------------------------------------------------------------------------------------------------|
| `dataAttr`                | `data-attr`                   | Set arbitrary attributes. [↗](https://data-star.dev/reference/attributes#data-attr)                          |
| `dataBind`                | `data-bind`                   | Binds signal name to input/select/textarea values. [↗](https://data-star.dev/reference/attributes#data-bind) |
| `dataClass`               | `data-class`                  | Toggle classes. [↗](https://data-star.dev/reference/attributes#data-class)                                   |
| `dataComputed`            | `data-computed`               | Computed values from expressions. [↗](https://data-star.dev/reference/attributes#data-computed)              |
| `dataEffect`              | `data-effect`                 | Side effects from expressions. [↗](https://data-star.dev/reference/attributes#data-effect)                   |
| `dataIgnore`              | `data-ignore`                 | Ignore this element and its children. [↗](https://data-star.dev/reference/attributes#data-ignore)            |
| `dataIgnoreSelf`          | `data-ignore`                 | Ignore only this element, not children. [↗](https://data-star.dev/reference/attributes#data-ignore)          |
| `dataIgnoreMorph`         | `data-ignore-morph`           | Ignore morphing for this element. [↗](https://data-star.dev/reference/attributes#data-ignore-morph)          |
| `dataIndicator`           | `data-indicator`              | Loading indicator. [↗](https://data-star.dev/reference/attributes#data-indicator)                            |
| `dataJsonSignals`         | `data-json-signals`           | JSON signal declarations. [↗](https://data-star.dev/reference/attributes#data-json-signals)                  |
| `dataOn`                  | `data-on`                     | Event listeners (click, input, etc.). [↗](https://data-star.dev/reference/attributes#data-on)                |
| `dataOnIntersect`         | `data-on-intersect`           | Execute when element intersects viewport. [↗](https://data-star.dev/reference/attributes#data-on-intersect)  |
| `dataOnInterval`          | `data-on-interval`            | Execute on interval. [↗](https://data-star.dev/reference/attributes#data-on-interval)                        |
| `dataOnLoad`              | `data-on-load`                | Execute when element loads. [↗](https://data-star.dev/reference/attributes#data-on-load)                     |
| `dataOnSignalPatch`       | `data-on-signal-patch`        | Execute when signal patches. [↗](https://data-star.dev/reference/attributes#data-on-signal-patch)            |
| `dataOnSignalPatchFilter` | `data-on-signal-patch-filter` | Filter signal patch events. [↗](https://data-star.dev/reference/attributes#data-on-signal-patch-filter)      |
| `dataPreserveAttr`        | `data-preserve-attr`          | Preserve attributes during morphing. [↗](https://data-star.dev/reference/attributes#data-preserve-attr)      |
| `dataRef`                 | `data-ref`                    | Assign a local reference. [↗](https://data-star.dev/reference/attributes#data-ref)                           |
| `dataShow`                | `data-show`                   | Show element when expression is truthy. [↗](https://data-star.dev/reference/attributes#data-show)            |
| `dataSignals`             | `data-signals`                | Declare/expose signals. [↗](https://data-star.dev/reference/attributes#data-signals)                         |
| `dataStyle("styleName")`  | `data-style`                  | Sets inline style. [↗](https://data-star.dev/reference/attributes#data-style)                                |
| `dataText`                | `data-text`                   | Sets element text content from expression. [↗](https://data-star.dev/reference/attributes#data-text)         |

## Using Attributes

Most of these attributes have a `:=` dsl method that takes a `Js` value representing the [Datastar expression](https://data-star.dev/guide/datastar_expressions) in which you can use signals, JavaScript code, or special `@` commands to interact with the server. You can use the `Js` helper to create these expressions or use the `js` interpolator which has compile-time checking of the expressions.

For example, you can use the `dataOnLoad` attribute to trigger a server request when the page loads:

```scala mdoc:silent
import zio.http._
import zio.http.datastar._
import zio.http.template2._
import zio.http.endpoint.Endpoint

body(
  dataOnLoad := js"@get('/hello-world')",
  dataOn.load := Endpoint(Method.GET / "hello-world").out[String].datastarRequest(()),
  div(
    className := "container",
    h1("Hello World Example"),
    div(id("message"))
  )
)
```

If you want a type-safe DSL, we recommend using the `Endpoint` API instead of a JavaScript expression when using the `dataOn` attribute to define a Datastar server request:

```scala
import zio.http.datastar._
import zio.http.endpoint.Endpoint

body(
   dataOn.load := Endpoint(Method.GET / "hello-world").out[String].datastarRequest(()),
   div(
      className := "container",
      h1("Hello World Example"),
      div(id("message"))
   )
)
```

The `dataOn` attribute has several event listeners that you can use to handle different events on elements. For example, the `dataOnLoad` can be written as `dataOn.load`; there are many other events available, such as `click`, `input`, `submit`, `change`, `focus`, `blur`, etc. 

Each of the attributes may have a couple of modifiers, about which you can learn in their respective sections in the [Datastar documentation](https://data-star.dev/reference/attributes). They are all modeled as a type-safe DSL in the `zio-http-datastar-sdk` module. For example, you can use the `debounce` modifier on the `dataOn.input` attribute to debounce input events:

```scala mdoc:compile-only
import zio._

dataOn.input.debounce(300.millis) := Js("@get('/search?q=' + $query)")
// datastar equivalent: <p data-on-input__debounce.300ms="@get('/search?q=' + $query)"></p>
```

## Working with Signals

Another important attribute is `dataSignals`, which allows you to declare signals that can be used in the Datastar expressions. You can declare a signal using the `dataSignals` attribute as follows:

```scala mdoc:silent
val $currentTime = Signal[String]("currentTime")

dataSignals($currentTime) := js"'--:--:--'"
```

This declares a signal named `currentTime` of type `String` with an initial value of `00:00:00`. You can then use this signal in other Datastar attributes. For example, you can use `dataText` to display the current time:

```scala mdoc:compile-only
span(
  dataSignals($currentTime) := js"'--:--:--'",
  dataText                  := $currentTime,
  dataOn.load               := Endpoint(Method.GET / "server-time").out[String].datastarRequest(()),
)
```

In this example, the `dataText` attribute binds the text content of the `span` element to the `currentTime` signal, and the `dataOn.load` attribute triggers a server request to update the signal when the page loads.

Server-side updates to signals are sent using the [`ServerSentEventGenerator#patchSignals`](./server-api.md#patching-signals) method.
