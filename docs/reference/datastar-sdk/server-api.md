---
id: server-api
title: Datastar Event Generation Helpers
---

ZIO HTTP provides a set of helpers to generate Datastar events that can be sent to the browser as a response. These helpers make it easy to create the different types of events that Datastar supports. In general, there are two types of events:

1. **Single-shot events**: These events are sent as a single response to the browser.
2. **Streaming events**: These events are sent as a stream of responses to the browser.

## Single-shot Events

Single-shot events are those responses that are sent once to the browser using the `text/html` as the content type. In ZIO HTTP, you can create them simply by returning a `Response` with the appropriate content type and body.

For example, assume you have written a form that takes a username and submits it to the server as follows:

```scala mdoc:compile-only
div(
  className := "container",
  h1("👋 Greeting Form 👋"),
  form(
    id("greetingForm"),
    dataOn.submit := js"@get('/greet', {contentType: 'form'})",
    label(`for`("name"), "What's your name?"),
    input(`type`("text"), id("name"), name("name"), placeholder("Enter your name!"), required, autofocus),
    button(`type`("submit"), "Greet me!"),
  ),
  div(id("greeting"))
)
```

The server responds with a single-shot event that updates a greeting message with the provided username:

```scala mdoc:compile-only
Method.GET / "greet" -> event {
  handler { (req: Request) =>
    DatastarEvent.patchElements(
      div(
        id("greeting"),
        p(s"Hello ${req.queryParam("name").getOrElse("Guest")}"),
        )
      )
  }
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

## Streaming Events

Streaming events are those responses that are sent as a stream of events to the browser using the `text/event-stream` as the content type. In ZIO HTTP, you can create them using `ServerSentEventGenerator` to generate the appropriate SSE events.

Assume you call the `/hello-world` endpoint that streams a "Hello, World!" message once the page loads:

```scala mdoc:compile-only
body(
  dataOn.load := js"@get('/hello-world')",
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
4. **Dispatch Events** which fire custom DOM events on the client using `ServerSentEventGenerator#dispatchEvent` method

## Patching Elements

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

## Patching Signals

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

## Executing Scripts

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

## Dispatching Events

The `ServerSentEventGenerator#dispatchEvent` is used to fire custom DOM events on the client. This enables you to trigger reactive behaviors defined in your HTML via `data-on` attributes or JavaScript event listeners. It takes the event name as a string and optionally the selector and event details:

```scala mdoc:compile-only
final case class DispatchEventOptions(
  source: Option[CssSelector] = None,
  bubbles: Boolean = true,
  cancelable: Boolean = false,
  composed: Boolean = false,
  autoRemove: Boolean = true,
  eventId: Option[String] = None,
  retryDuration: Duration = 1000.millis,
)
```

1. The `source` specifies which element should receive the event. If `None`, the event is dispatched on `window`.
2. The `bubbles` flag controls whether the event bubbles through the DOM tree (default: true).
3. The `cancelable` flag indicates whether the event can be canceled (default: false).
4. The `composed` flag determines if the event propagates across shadow DOM boundaries (default: false).
5. The `autoRemove` flag controls whether the event element is automatically removed after execution (default: true).
6. The `eventId` is an optional identifier for the SSE event.
7. The `retryDuration` specifies the duration the client should wait before retrying the connection in case of failure (default: 1 second).

Here is an example of dispatching a custom event from the server when a background operation completes:

```scala mdoc:compile-only
import zio.http.datastar._

// Server: Dispatch a custom event after processing completes
for {
  _ <- ZIO.sleep(2.seconds)  // Simulate processing
  _ <- ServerSentEventGenerator.dispatchEvent(
    "dataProcessingComplete",
    DispatchEventOptions(
      source = Some(CssSelector.id("data-container")),
      retryDuration = 5.seconds
    )
  )
} yield ()
```

On the client side, you can listen to this event using the `data-on` attribute:

```scala mdoc:compile-only
import zio.http.datastar._
import zio.http.template2._

div(
  id("data-container"),
  dataOn("dataProcessingComplete") := js"console.log('Data processing completed!')",
  p("Waiting for data...")
)
```

When the server dispatches the "dataProcessingComplete" event, Datastar fires the custom event on the element with `id="data-container"`, triggering any handlers attached to it. This is useful for:

- Coordinating complex multi-step workflows between server and client
- Triggering UI state changes based on background job completion
- Implementing reactive patterns where the server controls when client-side actions occur
- Building collaborative features where actions by one client need to trigger updates on others

The dispatched event is a standard DOM `CustomEvent` with optional detail data that can be accessed in event handlers. The event propagates through the DOM tree, allowing you to attach listeners at any parent element.
