package example.datastar

import zio._
import zio.http._
import zio.http.datastar._
import zio.http.template2._

/**
 * Datastar Dispatch Event Example — Dispatching Custom DOM Events from Server
 *
 * This example demonstrates how to use `ServerSentEventGenerator.dispatchEvent`
 * to fire custom DOM events from the server, enabling the client to respond to
 * server-driven state changes without page refreshes.
 *
 * Run with: sbt "zioHttpExample/runMain example.datastar.DispatchEventExample"
 */
object DispatchEventExample extends ZIOAppDefault {
  val routes: Routes[Any, Response] = Routes(
    Method.GET / Root               -> event {
      handler { (_: Request) =>
        DatastarEvent.patchElements(
          html(
            head(
              title("Datastar Dispatch Event Example"),
              datastarScript,
              style.inlineCss(css),
            ),
            body(
              div(
                className := "container",
                h1("Datastar Dispatch Event Example"),
                p(
                  "Click the button to start a 3-second data processing task. ",
                  "The server will dispatch a custom event when complete.",
                ),
                button(
                  "Start Processing",
                  id("processBtn"),
                  dataOn.click := js"@post('/api/process'); this.disabled = true; document.getElementById('status').style.display = 'block';",
                ),
                div(
                  id("status"),
                  className := "status",
                  "Processing... (this will take 3 seconds)",
                ),
                div(
                  id("result"),
                  className := "status complete",
                  "✓ Processing complete!",
                  dataOn(
                    "processingComplete",
                  ) := js"this.style.display = 'block'; document.getElementById('processBtn').disabled = false; document.getElementById('status').style.display = 'none';",
                ),
              ),
            ),
          ),
        )
      }
    },
    Method.POST / "api" / "process" -> events {
      handler {
        for {
          _ <- ZIO.sleep(3.seconds)
          _ <- ServerSentEventGenerator.dispatchEvent(
            "processingComplete",
            js"{}",
            DispatchEventOptions(source = Some(CssSelector.id("result"))),
          )
        } yield ()
      }
    },
  )

  val css = css"""
    body {
      font-family: sans-serif;
      margin: 40px;
      background: #f5f5f5;
    }
    .container {
      max-width: 600px;
      margin: 0 auto;
      background: white;
      padding: 30px;
      border-radius: 8px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.1);
    }
    h1 {
      color: #1976d2;
    }
    p {
      color: #666;
    }
    button {
      padding: 10px 20px;
      font-size: 16px;
      cursor: pointer;
      background: #1976d2;
      color: white;
      border: none;
      border-radius: 4px;
    }
    button:disabled {
      background: #ccc;
      cursor: not-allowed;
    }
    .status {
      margin-top: 20px;
      padding: 15px;
      border-radius: 4px;
      background: #fff3cd;
      color: #856404;
      border: 1px solid #ffeeba;
    }
    .status.complete {
      display: none;
      background: #d4edda;
      color: #155724;
      border: 1px solid #c3e6cb;
    }
  """

  override def run: ZIO[Any, Throwable, Unit] =
    Server
      .serve(routes)
      .provide(Server.default)
}
