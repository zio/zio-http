---
id: index
sidebar_label: Datastar SDK
title: Integration of Datastar with ZIO HTTP
---

[Datastar](https://data-star.dev/) is a hypermedia-driven framework for building reactive web applications with minimal JavaScript. The `zio-http-datastar-sdk` integrates Datastar with ZIO HTTP, bringing these capabilities to the ZIO ecosystem and allowing developers to create server-driven UIs with minimal frontend complexity.

In Datastar, the server sends HTML elements that are integrated into the web page. Instead of building a data based API (JSON, XML, etc.) and rendering HTML on the client, the rendering happens on the server, and the HTML elements—including hypermedia controls—are sent to the browser.

This matters because it solves a critical problem in modern web development: building interactive, real-time applications traditionally requires heavy frontend frameworks and complex state synchronization. The Datastar integration provides a simpler alternative for server-driven applications where state lives on the backend, updates flow via SSE or HTTP transactions, and the frontend remains lightweight (about 10.7 KB). 

## Datastar Overview

Datastar uses declarative `data-*` HTML attributes to define the application state and behavior on the client side.

Datastar uses signals to represent reactive state variables that can be updated both on the client and server sides. Signals are prefixed with `$` (like `$username`, `$count`). These signals are automatically sent to the backend with each request, and the server can patch them by sending signal patches back to the client.

For example, when a user types into an input field bound with `data-bind:email`, the `$email` signal updates locally and gets transmitted to the server with subsequent requests. The server can then push signal updates back using JSON Merge Patch (RFC 7396), or send HTML fragments that morph into the DOM. This flow can happen over SSE connections or regular HTTP transactions.

Datastar shines in scenarios where you want to build dynamic, real-time web applications without the overhead of heavy frontend frameworks. Here are some common use cases:

- Chat messages appearing live.
- Monitoring logs, metrics, or notifications.
- Live search results that update as you type.
- Real-time dashboard panels updating from streaming endpoints.

## Reactive Hypermedia with ZIO HTTP

The `zio-http-datastar-sdk` provides both server-side and client-side utilities to provide a unified web development experience within the ZIO ecosystem:

1. The server-side API shields developers from low-level SSE protocol details, providing server-sent event generators for creating [Datastar SSE event types](https://data-star.dev/reference/sse_events) such as patching elements and signals, and executing scripts.
2. The client-side API offers a ZIO-friendly way to embed Datastar [attributes](https://data-star.dev/reference/attributes) into HTML responses, making it easy to create reactive UIs that seamlessly integrate with ZIO HTTP's templating capabilities.

## Installation

To use the Datastar SDK with ZIO HTTP, add the following dependency to your `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-http-datastar-sdk" % "@VERSION@"
```

You also have to include the Datastar JavaScript client module in your HTML pages. You can do this by adding the following script tag to your HTML head:

```scala mdoc:compile-only
import zio.http.template2._

script(
  `type` := "module", 
  src := "https://cdn.jsdelivr.net/gh/starfederation/datastar@<VERSION>/bundles/datastar.js"
)
```

Pick the proper version of the module according to the [installation instructions](https://data-star.dev/guide/getting_started#installation) in the Datastar's documentation.

## Basic Usage

After reading the [Getting Started](https://data-star.dev/guide/getting_started) guide and learning the basics of Datastar, you are ready to dive into an example showing how to use Datastar with ZIO HTTP in practice:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/datastar/SimpleHelloWorldExample.scala")
```

This is a full example of a ZIO HTTP server using Datastar to create a reactive web application. The example demonstrates how to stream updates to the client using Server-Sent Events.

## Datastar HTML Attributes

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

Most of these attributes have a `:=` dsl method that takes a `Js` value representing the [Datastar expression](https://data-star.dev/guide/datastar_expressions) in which you can use signals, JavaScript code, or special `@` commands to interact with the server. You can use the `Js` helper to create these expressions or use the `js` interpolator which has compile-time checking of the expressions.

For example, you can use the `dataOnLoad` attribute to trigger a server request when the page loads:

```scala mdoc:silent
import zio.http._
import zio.http.datastar._
import zio.http.template2._
import zio.http.endpoint.Endpoint

body(
  dataOnLoad := Js("@get('/hello-world')"),
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

We will discuss later how the server sends updates to the signals using the [`ServerSentEventGenerator#patchSignals`](#patching-signals) method.

## Extracting Datastar Signals from Requests

When the client sends a request to the server, it includes the current values of all signals in a special query parameter named `datastar`. You can extract these signals from the request.

For example, the following HTML form binds an input field to a signal named `delay`; when the form is submitted, the current value of the `delay` signal is sent to the server:

```scala mdoc:compile-only
body(
  h1("Hello World Example"), {
    val $delay = Signal[Int]("delay")
    div(
      dataSignals($delay) := js"100",
      label("Delay (ms): ", `for` := "delay"),
      input(dataBind($delay.name), name := "delay", `type` := "number", step := "100"),
    )
  },
  button(dataOn.click := Endpoint(Method.GET / "server-time").out[String].datastarRequest(()))("Start Animation"),
  div(id("message")),
)
```

The server can extract the signals from the request like this:  

```scala mdoc:silent
import zio._
import zio.json._
import zio.http._
import zio.http.datastar._

case class Delay(value: Int)
object Delay {
  implicit val jsonCodec: JsonCodec[Delay] = DeriveJsonCodec.gen
}

val route =
  Method.GET / "hello-world" -> events {
    handler { (request: Request) =>
      val delay = request.url.queryParams
        .getAll("datastar")
        .headOption
        .flatMap { s =>
          Delay.jsonCodec.decodeJson(s).toOption
        }
        .getOrElse(Delay(100))
      
      // Use the extracted delay value in your logic 
      val message = "Hello, world!"
      ZIO.foreachDiscard(message.indices) { i =>
        for {
          _ <- ServerSentEventGenerator.executeScript(js"console.log('Sending substring(0, ${i + 1})')")
          _ <- ServerSentEventGenerator.patchElements(div(id("message"), message.substring(0, i + 1)))
          _ <- ZIO.sleep(delay.value.millis)
        } yield ()
      }
    }
  }
```

## Datastar Event Generation Helpers

ZIO HTTP provides a set of helpers to generate Datastar events that can be sent to the browser as a response. These helpers make it easy to create the different types of events that Datastar supports. In general, there are two types of events:

1. **Single-shot events**: These events are sent as a single response to the browser.
2. **Streaming events**: These events are sent as a stream of responses to the browser.

### Single-shot Events

Single-shot events are those responses that are sent once to the browser using the `text/html` as the content type. In ZIO HTTP, you can create them simply by returning a `Response` with the appropriate content type and body.

For example, assume you have written a form that takes a username and submits it to the server as follows:

```scala mdoc:compile-only
div(
  className := "container",
  h1("👋 Greeting Form 👋"),
  form(
    id("greetingForm"),
    dataOn.submit := Js("@get('/greet', {contentType: 'form'})"),
    label(`for`("name"), "What's your name?"),
    input(`type`("text"), id("name"), name("name"), placeholder("Enter your name!"), required, autofocus),
    button(`type`("submit"), "Greet me!"),
  ),
  div(id("greeting"))
)
```

The server responds with a single-shot event that updates a greeting message with the provided username:

```scala mdoc:compile-only
Method.GET / "greet" -> handler { (req: Request) =>
  Response(
    headers = Headers(Header.ContentType(MediaType.text.`html`)),
    body = Body.fromCharSequence(
      div(
        id("greeting"),
        p(s"Hello ${req.queryParam("name").getOrElse("Guest")}"),
      ).render
    )
  )
}
```

If the client submits the form with the name "John", the request would be `GET /greet?name=John` and the response from the server would be:

```http
HTTP/1.1 200 Ok
content-type: text/html
content-length: 42

<div id="greeting"><p>Hello John</p></div>
```

The browser receives the response and updates the DOM accordingly using Datastar's built-in patching mechanism.

### Streaming Events

Streaming events are those responses that are sent as a stream of events to the browser using the `text/event-stream` as the content type. In ZIO HTTP, you can create them using `ServerSentEventGenerator` to generate the appropriate SSE events.

Assume you call the `/hello-world` endpoint that streams a "Hello, World!" message once the page loads:

```scala mdoc:compile-only
body(
  dataOn.load := Js("@get('/hello-world')"),
  div(
    className := "container",
    h1("Hello World Example"),
    div(id("message"))
  )
)
```

The server responds with a streaming event that sends characters progressively:

```scala mdoc:compile-only
val message = "Hello, world!"

Method.GET / "hello-world" -> events {
  handler {
    ZIO.foreachDiscard(message.indices) { i =>
      for {
        _ <- ServerSentEventGenerator.patchElements(div(id("message"), message.substring(0, i + 1)))
        _ <- ZIO.sleep(100.millis)
      } yield ()
    }
  }
}
```

If the client makes the `GET /hello-world` request, the response from the server would be:

```http
HTTP/1.1 200 Ok
content-type: text/event-stream
connection: keep-alive
transfer-encoding: chunked

event: datastar-patch-elements
data: elements <div id="message">H</div>

event: datastar-patch-elements
data: elements <div id="message">He</div>

event: datastar-patch-elements
data: elements <div id="message">Hel</div>

....

event: datastar-patch-elements
data: elements <div id="message">Hello, world!</div>
```

As the server streams the response, the browser receives each event and updates the DOM accordingly using Datastar's built-in patching mechanism.

You can generate and send three types of Datastar SSE events to the client using the `ServerSentEventGenerator`:
1. **Patch Elements into the DOM** using `ServerSentEventGenerator#patchElements` methods
2. **Patch Signals** which updates the values of reactive signals using `ServerSentEventGenerator#patchSignals` methods
3. **Execute Scripts** which run JavaScript code on the client using `ServerSentEventGenerator#executeScript` method

#### Patching Elements

The `ServerSentEventGenerator#patchElements` takes an HTML fragment and sends it to the client to be merged into the DOM. As a second argument, it takes options of type `PatchElementOptions` to specify how the patching should be done:

```scala mdoc:compile-only
final case class PatchElementOptions(
  selector: Option[CssSelector] = None,
  mode: ElementPatchMode = ElementPatchMode.Outer,
  useViewTransition: Boolean = false,
  eventId: Option[String] = None,
  retryDuration: Duration = 1000.millis,
)
```

1. The `selector` is an optional CSS selector to specify where in the DOM the patch should be applied. If omitted the id of the returned element is used.
2. The `mode` specifies how the patch should be applied. It has 8 different modes:
    - **Outer**: Morph entire element, preserving state
    - **Inner**: Morph inner HTML only, preserving state
    - **Replace**: Replace entire element, reset state
    - **Prepend/Append/Before/After**: Insertion modes
    - **Remove**: Delete element
3. The `useViewTransition` specifies whether to use the [View Transition API](https://developer.mozilla.org/en-US/docs/Web/API/View_Transition_API) for smooth transitions when patching elements.
4. The `eventId` is an optional identifier for the event.
5. The `retryDuration` specifies the duration the client should wait before retrying the connection in case of failure.

For example, if we run the following code on the server:

```scala mdoc:compile-only
val message = "Hello, world!"

ZIO.foreachDiscard(message.indices) { i =>
 for {
   _ <- ServerSentEventGenerator.patchElements(
     div(id("message"), message.substring(0, i + 1)),
     PatchElementOptions(
       mode = ElementPatchMode.Replace,
       retryDuration = 5.seconds,
       eventId = Some(i.toString)),
   )
   _ <- ZIO.sleep(100.millis)
 } yield ()
}
```

We will end up sending the following SSE events to the client:

```http
event: datastar-patch-elements
data: mode replace
data: elements <div id="message">H</div>
id: 0
retry: 5000

event: datastar-patch-elements
data: mode replace
data: elements <div id="message">He</div>
id: 1
retry: 5000

...

event: datastar-patch-elements
data: mode replace
data: elements <div id="message">Hello, world!</div>
id: 12
retry: 5000
```

More details about patching elements can be found in the [Datastar documentation](https://data-star.dev/reference/sse_events#datastar-patch-elements).

#### Patching Signals

The `ServerSentEventGenerator#patchSignals` is used to update the values of reactive signals on the client. As a second argument, it takes options of type `PatchSignalOptions` to specify how the patching should be done:

```scala mdoc:compile-only
final case class PatchSignalOptions(
  onlyIfMissing: Boolean = false,
  eventId: Option[String] = None,
  retryDuration: Duration = 1000.millis,
)
```

1. The `onlyIfMissing` specifies whether to update only signals that are not already present on the client.
2. The `eventId` is an optional identifier for the event.
3. The `retryDuration` specifies the duration the client should wait before retrying the connection in case of failure.

Here is an example of generating the current server time and sending it to the client every second by patching a signal named `currentTime`:

```scala mdoc:compile-only
import java.time.format.DateTimeFormatter

ZIO.clock
  .flatMap(_.currentDateTime)
  .map(_.toLocalTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
  .flatMap { currentTime =>
    ZIO.logInfo(s"Sending time: $currentTime") *>
      ServerSentEventGenerator.patchSignals(
        s"{ 'currentTime': '$currentTime' }",
        PatchSignalOptions(retryDuration = 5.seconds),
      )
  }
  .schedule(Schedule.spaced(1.second))
  .unit
```

This can be used inside a handler that streams the server time to the client. We will end up sending the following SSE events to the client:

```http
event: datastar-patch-signals
data: signals { 'currentTime': '19:38:43' }
retry: 5000

event: datastar-patch-signals
data: signals { 'currentTime': '19:38:44' }
retry: 5000

event: datastar-patch-signals
data: signals { 'currentTime': '19:38:45' }
retry: 5000

...
```

More details about patching signals can be found in the [Datastar documentation](https://data-star.dev/reference/sse_events#datastar-patch-signals).

#### Executing Scripts

The `ServerSentEventGenerator#executeScript` is used to run JavaScript code on the client. It takes the script as a string and as a second argument, it takes options of type `ExecuteScriptOptions` to specify how the script should be executed:

```scala mdoc:compile-only
final case class ExecuteScriptOptions(
  autoRemove: Boolean = true,
  attributes: Seq[(String, String)] = Seq.empty,
  eventId: Option[String] = None,
  retryDuration: Duration = 1000.millis,
)
```

1. The `autoRemove` specifies whether to automatically remove the script element after execution. It defaults to `true`.
2. The `attributes` is a sequence of key-value pairs to add as attributes to the script element.
3. The `eventId` is an optional identifier for the event.
4. The `retryDuration` specifies the duration the client should wait before retrying the connection in case of failure.

Here is an example of generating console log scripts from the server and sending them to the client:

```scala mdoc:compile-only
val message = "Hello, world!"
ZIO.foreachDiscard(message.indices) { i =>
 for {
   _ <- ServerSentEventGenerator.executeScript(js"console.log('Sending substring(0, ${i + 1})')")
   _ <- ZIO.sleep(100.millis)
 } yield ()
}
```

We will end up sending the following SSE events to the client:

```http
event: datastar-patch-elements
data: selector body
data: mode append
data: elements <script data-effect="el.remove">console.log('Sending substring(0, 1)')</script>

event: datastar-patch-elements
data: selector body
data: mode append
data: elements <script data-effect="el.remove">console.log('Sending substring(0, 2)')</script>

...

event: datastar-patch-elements
data: selector body
data: mode append
data: elements <script data-effect="el.remove">console.log('Sending substring(0, 3)')</script>
```

With this, the client will execute each script and log the messages to the console. Datastar finds the `<body>` element, appends the `<script>` tag to it, and the script executes immediately (logging to console). The `data-effect=el.remove` directive causes the script to remove itself from the DOM after execution, because the `autoRemove` is enabled by default.


## Examples

### Simple Hello World Example

This example demonstrates the most basic use of Datastar with ZIO HTTP by streaming a "Hello, world!" message to the browser. It's an excellent starting point for understanding how Server-Sent Events work with Datastar:

```scala mdoc:passthrough
utils.printSource("zio-http-example/src/main/scala/example/datastar/SimpleHelloWorldExample.scala")
```

**How it works:**

The page uses the `dataOn.load` attribute to trigger a GET request to `/hello-world` as soon as the page loads. The server responds with a stream of SSE events, where each event patches a div element with `id="message"` to display progressively more characters of the message.

On the server side, the handler iterates through each character index of "Hello, world!" and for each iteration:
1. Executes a console.log script on the client using `ServerSentEventGenerator.executeScript` to log the current character index
2. Patches the `#message` div with a substring containing all characters up to the current index using `ServerSentEventGenerator.patchElements`
3. Waits 100 milliseconds before processing the next character

This creates a typewriter effect where the message appears one character at a time. The entire interaction happens without any custom JavaScript code - just declarative HTML attributes and server-side streaming.

### Hello World with Custom Delay

This example builds upon the Simple Hello World example by adding user control over the animation speed, demonstrating how to use Datastar signals for bidirectional communication between client and server:

```scala mdoc:passthrough
utils.printSource("zio-http-example/src/main/scala/example/datastar/HelloWorldWithCustomDelayExample.scala")
```

**How it works:**

The page declares a signal called `delay` using `dataSignals(Signal[Int]("delay"))` with an initial value of 100 milliseconds. This signal is bound to a number input field via `dataBind("delay")`, which means any changes to the input automatically update the `$delay` signal value. A button with `dataOn.click := Js("@get('/hello-world')")` triggers the animation when clicked.

The key difference from the simple example is how the server extracts and uses the delay value. When the button is clicked, Datastar automatically includes all current signal values in a query parameter named `datastar`. The server extracts this parameter, decodes it as JSON to get the `Delay` case class, and uses that value to control the sleep duration between character updates.

Users can adjust the delay value and restart the animation to see it play at different speeds, all without writing any JavaScript code.

### Server Time Example

This example showcases real-time, server-pushed updates by streaming the current server time to the browser every second. It demonstrates a common pattern for live dashboards and monitoring applications.

```scala mdoc:passthrough
utils.printSource("zio-http-example/src/main/scala/example/datastar/ServerTimeExample.scala")
```

**How it works:**

The page displays a time value using `dataText := Js("$currentTime")`, which binds the text content of a span element to the `currentTime` signal. The signal is declared with `dataSignals(Signal[String]("currentTime"))` and initialized to an empty string. When the page loads (`dataOn.load := Js("@get('/server-time')")`), it establishes an SSE connection to the `/server-time` endpoint.

The server handler uses ZIO's scheduling capabilities to create a repeating effect that runs every second. Each second, the server:
1. Gets the current time from the clock
2. Formats it as "HH:mm:ss"
3. Patches the `currentTime` signal with the new value using `patchSignals`

The `PatchSignalOptions` configures a 5-second retry duration, meaning if the connection drops, the client will attempt to reconnect after 5 seconds. The signal patching sends JSON that updates only the specified signals without requiring a full page refresh.


### Greeting Form Example

This example demonstrates single-shot (non-streaming) responses using traditional form submissions, showing how Datastar handles HTTP transactions alongside SSE streaming.

```scala mdoc:passthrough
utils.printSource("zio-http-example/src/main/scala/example/datastar/GreetingFormExample.scala")
```

**How it works:**

The page contains a form with an input field for the user's name. The form uses `dataOn.submit := Js("@get('/greet', {contentType: 'form'})")` to intercept the submit event and send a GET request with the form data. The `{contentType: 'form'}` option tells Datastar to serialize the form fields as query parameters.

Unlike the streaming examples, the server responds with a single HTML fragment (not SSE):

```scala
Response(
  headers = Headers(Header.ContentType(MediaType.text.`html`)),
  body = Body.fromCharSequence(
    div(id("greeting"), p(s"Hello ${req.queryParam("name").getOrElse("Guest")}")).render
  )
)
```

The response is a `text/html` fragment containing a div with `id="greeting"`. Datastar automatically finds the existing `<div id="greeting">` in the DOM and morphs it with the new content, displaying the personalized greeting.

The interaction is smooth and partial—only the greeting div updates, not the entire page. This pattern is useful for traditional CRUD operations where you don't need continuous streaming but want the benefits of hypermedia-driven updates.

### Fruit Explorer Example

This example demonstrates real-time search with debouncing and view transitions, showcasing advanced Datastar features for building sophisticated interactive UIs.

```scala mdoc:passthrough
utils.printSource("zio-http-example/src/main/scala/example/datastar/FruitExplorerExample.scala")
```

**How it works:**

The page contains a single input field with two key attributes:
1. `dataBind("query")` - Binds the input value to a `$query` signal
2. `dataOn.input.debounce(300.millis) := Js("@get('/search?q=' + $query)")` - Triggers a search request 300ms after the user stops typing

The debouncing prevents excessive server requests while typing. Each keystroke updates the `$query` signal, but the search only fires after a 300ms pause, reducing server load and providing a smoother UX.

The server handler extracts the search term from the query parameter and filters a fruit list. When results are found, it:
1. First patches an empty results container with `ServerSentEventGenerator.patchElements(div(id("result"), ol(id("list"))))`
2. Then streams each result as a separate list item with a 100ms delay between items:

   ```scala mdoc:compile-only
   ServerSentEventGenerator.patchElements(
     li(r),
     PatchElementOptions(
       selector = Some(CssSelector.id("list")),
       mode = ElementPatchMode.Append,
       useViewTransition = true
     )
   ).delay(100.millis)
   ```

The `PatchElementOptions` are particularly interesting here:
- `selector = Some(CssSelector.id("list"))` - Targets the specific list element by ID
- `mode = ElementPatchMode.Append` - Adds each item to the end of the list rather than replacing it
- `useViewTransition = true` - Enables smooth CSS View Transitions API animations

The CSS includes view transition rules that create smooth fade-in effects:

```css
::view-transition-old(root),
::view-transition-new(root) {
    animation-duration: 0.8s;
}
```

The result is a highly responsive search experience with beautiful animations, all controlled from the server without complex client-side state management.
