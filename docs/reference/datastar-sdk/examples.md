---
id: examples
title: Datastar SDK Examples
---

## Running the Examples

All code from this reference is available as runnable examples in the `zio-http-example` module.

**1. Clone the repository and navigate to the project:**

```bash
git clone https://github.com/zio/zio-http.git
cd zio-http
```

**2. Run individual examples with sbt:**

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

The page displays a time value using `dataText := $currentTime)`, which binds the text content of a span element to the `currentTime` signal. The signal is declared with `dataSignals(Signal[String]("currentTime"))` and initialized to an empty string. When the page loads (`dataOn.load := Js("@get('/server-time')")`), it establishes an SSE connection to the `/server-time` endpoint.

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

The page contains a form with an input field for the user's name. The form uses `dataOn.submit := js"@get('/greet', {contentType: 'form'})"` to intercept the submit event and send a GET request with the form data. The `{contentType: 'form'}` option tells Datastar to serialize the form fields as query parameters.

Unlike the streaming examples, the server responds with a single HTML fragment (not SSE):

```scala
event(handler((_: Request) => DatastarEvent.patchElements(indexPage)))
```

The response is a `text/html` fragment containing a div with `id="greeting"`. Datastar automatically finds the existing `<div id="greeting">` in the DOM and morphs it with the new content, displaying the personalized greeting.

The interaction is smooth and partial—only the greeting div updates, not the entire page.

### Fruit Explorer Example

This example demonstrates real-time search with debouncing and view transitions, showcasing advanced Datastar features for building sophisticated interactive UIs.

```scala mdoc:passthrough
utils.printSource("zio-http-example/src/main/scala/example/datastar/FruitExplorerExample.scala")
```

**How it works:**

The page contains a single input field with two key attributes:
1. `dataBind("query")` - Binds the input value to a `$query` signal
2. `dataOn.input.debounce(300.millis) := js"@get('/search?q=' + ${$query})"` - Triggers a search request 300ms after the user stops typing

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

### Real-time Chat Example

For a more comprehensive example demonstrating multi-client real-time chat with ZIO Hub broadcasting, see the [Real-time Chat with Datastar](../../guides/real-time-chat-with-datastar.md) guide. This example showcases:

- Broadcasting messages to multiple connected clients using ZIO Hub
- Persistent SSE connections for real-time updates
- Two-way signal binding with form inputs
- Type-safe request handling with `readSignals[T]`

### Dispatch Event Example

This example demonstrates a complete multi-step data processing workflow with real-time progress updates via Server-Sent Events and custom event dispatching to coordinate client-side state changes. It shows how to combine SSE streaming for live feedback with event dispatch for final state coordination.

```scala mdoc:passthrough
import docs.SourceFile

SourceFile.print("zio-http-example/src/main/scala/example/datastar/DispatchEventCompleteExample.scala")
```

([source](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/datastar/DispatchEventCompleteExample.scala))

```bash
sbt "zioHttpExample/runMain example.datastar.DispatchEventCompleteExample"
```
