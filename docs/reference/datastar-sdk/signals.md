---
id: signals
title: Extracting Datastar Signals from Requests
---

When the client sends a request to the server, it includes the current values of all signals. For GET requests, signals are sent in a special query parameter named `datastar`; for non-GET requests (POST, PUT, etc.), signals are sent in the request body. You can extract these signals from the request using `readSignals[T]`.

## Example: Form with Signal Binding

For example, the following HTML form binds an input field to a signal named `delay`; when the form is submitted, the current value of the `delay` signal is sent to the server:

```scala mdoc:compile-only
import zio.http.template2._
import zio.http.datastar._
import zio.http.endpoint.Endpoint
import zio.http._

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

## Reading Signals on the Server

The server can extract the signals from the request using the `readSignals[T]` method:

```scala mdoc:silent
import zio._
import zio.schema._
import zio.http._
import zio.http.template2._
import zio.http.datastar._

case class Delay(value: Int)
object Delay {
  implicit val schema: Schema[Delay] = DeriveSchema.gen
}

val route =
  Method.GET / "hello-world" -> events {
    handler { (request: Request) =>
      for {
        // Extract delay once before the loop to avoid repeated JSON decoding
        delay <- request.readSignals[Delay].orElse(ZIO.succeed(Delay(100)))
        message = "Hello, world!"
        _ <- ZIO.foreachDiscard(message.indices) { i =>
          for {
            _ <- ServerSentEventGenerator.executeScript(js"console.log('Sending substring(0, ${i + 1})')")
            _ <- ServerSentEventGenerator.patchElements(div(id("message"), message.substring(0, i + 1)))
            _ <- ZIO.sleep(delay.value.millis)
          } yield ()
        }
      } yield ()
    }
  }
```

## How It Works

1. The client declares signals using the [`dataSignals` attribute](./attributes.md#working-with-signals)
2. Input fields are bound to signals using the `dataBind` attribute
3. When a request is made (via `dataOn` event listeners or form submission), Datastar automatically includes all current signal values in the `datastar` query parameter as JSON
4. On the server, you define a case class that matches the signal structure
5. Use `request.readSignals[T]` to deserialize and type-safely extract the signal values

The case class fields should match your signal names exactly. ZIO's Schema derivation automatically handles the deserialization.
