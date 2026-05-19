package example.datastar

import zio._
import zio.http._
import zio.http.datastar._
import zio.http.endpoint.Endpoint
import zio.http.template2._

/**
 * Datastar Dispatch Event — Complete Example with Server-Sent Events
 *
 * This complete example demonstrates a data processing workflow where:
 *   1. Client initiates a long-running task via POST
 *   2. Server streams SSE updates during processing
 *   3. Server dispatches a custom event when complete
 *   4. Client responds to the event with UI updates
 *
 * Run with: sbt "zioHttpExample/runMain
 * example.datastar.DispatchEventCompleteExample"
 */
object DispatchEventCompleteExample extends ZIOAppDefault {
  val routes: Routes[Any, Response] = Routes(
    Method.GET / Root                        -> event {
      handler { (_: Request) =>
        DatastarEvent.patchElements(indexPage)
      }
    },
    Method.POST / "api" / "start-processing" -> handler { (_: Request) =>
      Response.ok
    },
    Method.GET / "api" / "processing-stream" -> events {
      handler {
        for {
          // Update button state and status
          _ <- ServerSentEventGenerator.patchElements(
            button(
              "Processing...",
              id("startBtn"),
              disabled,
            ),
          )
          _ <- ServerSentEventGenerator.patchElements(
            div(
              id("statusBox"),
              className := "status-box",
              "Processing...",
            ),
          )
          _ <- ZIO.sleep(300.millis)

          // Send initial log entry
          _ <- ServerSentEventGenerator.patchElements(
            div(className := "log-entry", "✓ Processing started"),
          )
          _ <- ZIO.sleep(500.millis)

          // Simulate processing step 1
          _ <- ServerSentEventGenerator.patchElements(
            div(className := "log-entry", "✓ Step 1: Validating data"),
          )
          _ <- ZIO.sleep(800.millis)

          // Simulate processing step 2
          _ <- ServerSentEventGenerator.patchElements(
            div(className := "log-entry", "✓ Step 2: Processing records"),
          )
          _ <- ZIO.sleep(800.millis)

          // Simulate processing step 3
          _ <- ServerSentEventGenerator.patchElements(
            div(className := "log-entry", "✓ Step 3: Generating report"),
          )
          _ <- ZIO.sleep(800.millis)

          // Update status box
          _ <- ServerSentEventGenerator.patchElements(
            div(
              id("statusBox"),
              className := "status-box success",
              "✓ Processing complete!",
            ),
          )

          // Dispatch custom event to trigger client-side handler
          _ <- ServerSentEventGenerator.dispatchEvent(
            "processingComplete",
            js"{}",
            DispatchEventOptions(source = Some(CssSelector.id("startBtn"))),
          )

          // Final log entry
          _ <- ServerSentEventGenerator.patchElements(
            div(className := "log-entry success", "✓ All steps completed successfully"),
          )
        } yield ()
      }
    },
  )

  def indexPage = html(
    head(
      title("Data Processor with Dispatch Events"),
      datastarScript,
      style.inlineCss(css),
    ),
    body(
      div(
        className                    := "container",
        h1("📊 Data Processing with Server Events"),
        p("Demonstrates server-side event dispatching for coordinating multi-step workflows."),
        button(
          "Start Processing",
          id("startBtn"),
          dataOn.click := js"""
            fetch('/api/start-processing', { method: 'POST' });
            const sse = new EventSource('/api/processing-stream');
            sse.addEventListener('message', (e) => {
              if (e.data.includes('processingComplete')) {
                sse.close();
              }
            });
          """,
        ),
        div(
          id("statusBox"),
          className    := "status-box",
          "Ready to process",
        ),
        div(
          id("logContainer"),
          className    := "log-container",
          div(className := "log-entry", "Waiting for processing to start..."),
        ),
        dataOn("processingComplete") := js"document.getElementById('startBtn').disabled = false;",
      ),
    ),
  )

  val css = css"""
    body {
      font-family: system-ui;
      margin: 40px;
      background: #f5f5f5;
    }
    .container {
      max-width: 700px;
      margin: 0 auto;
      background: white;
      padding: 30px;
      border-radius: 8px;
      box-shadow: 0 2px 8px rgba(0,0,0,0.1);
    }
    h1 {
      color: #1976d2;
      margin-bottom: 10px;
    }
    p {
      color: #666;
      margin-bottom: 20px;
    }
    button {
      padding: 12px 24px;
      font-size: 16px;
      background: #1976d2;
      color: white;
      border: none;
      border-radius: 4px;
      cursor: pointer;
      font-weight: 500;
    }
    button:disabled {
      background: #ccc;
      cursor: not-allowed;
    }
    .status-box {
      margin-top: 20px;
      padding: 15px;
      border-left: 4px solid #1976d2;
      background: #f5f5f5;
      border-radius: 4px;
      font-weight: 500;
    }
    .status-box.success {
      border-left-color: #4caf50;
      background: #e8f5e9;
      color: #2e7d32;
    }
    .log-container {
      margin-top: 30px;
      max-height: 300px;
      overflow-y: auto;
      border: 1px solid #ddd;
      border-radius: 4px;
      background: #fafafa;
    }
    .log-entry {
      padding: 10px 15px;
      border-bottom: 1px solid #eee;
      font-family: 'Courier New', monospace;
      font-size: 13px;
      color: #333;
    }
    .log-entry.success {
      color: #4caf50;
    }
  """

  override def run: ZIO[Any, Throwable, Unit] =
    Server
      .serve(routes)
      .provide(Server.default)
}
