package example.datastar

import java.time.format.DateTimeFormatter

import zio._

import zio.http._
import zio.http.datastar._
import zio.http.endpoint.Endpoint
import zio.http.template2._

object ServerTimeExample extends ZIOAppDefault {

  val timeHTML = html(
    head(
      title("Server Time - Datastar"),
      datastarScript,
      style.inlineCss(
        css"""
        body {
          display: flex;
          justify-content: center;
          align-items: center;
          min-height: 100vh;
          font-family: system-ui, -apple-system, sans-serif;
          background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
          color: white;
        }
        .container {
          text-align: center;
          background: rgba(255, 255, 255, 0.1);
          padding: 3rem;
          border-radius: 1rem;
          backdrop-filter: blur(10px);
        }
        h1 {
          font-size: 2.5rem;
          margin-bottom: 2rem;
        }
        .time-display {
          font-size: 4rem;
          font-weight: bold;
          margin: 2rem 0;
          font-family: 'Courier New', monospace;
        }
        button {
          font-size: 1.2rem;
          padding: 1rem 2rem;
          margin: 0.5rem;
          cursor: pointer;
          border: none;
          border-radius: 0.5rem;
          background: white;
          color: #667eea;
          font-weight: bold;
          transition: transform 0.2s;
        }
        button:hover {
          transform: scale(1.05);
        }
        button:disabled {
          opacity: 0.5;
          cursor: not-allowed;
        }
        .status {
          margin-top: 1rem;
          font-size: 1.2rem;
          opacity: 0.8;
        }
        """.stripMargin,
      ),
    ),
    body(
      div(className := "container")(
        h1("Live Server Time"), {
          val $currentTime = Signal[String]("currentTime")
          span(
            dataSignals($currentTime) := js"'--:--:--'",
            dataText                  := $currentTime,
            className                 := "time-display",
            dataOn.load               := Endpoint(Method.GET / "server-time").out[String].datastarRequest(()),
          )
        },
      ),
    ),
  )

  // Time formatter
  val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

  // Server time streaming endpoint
  val serverTimeHandler =
    events {
      handler {
        ZIO.clock
          .flatMap(_.currentDateTime)
          .map(_.toLocalTime.format(timeFormatter))
          .flatMap { currentTime =>
            ZIO.logInfo(s"Sending time: $currentTime") *>
              ServerSentEventGenerator.patchSignals(
                s"{ 'currentTime': '$currentTime' }",
                PatchSignalOptions(retryDuration = 5.seconds),
              )
          }
          .schedule(Schedule.spaced(1.second))
          .unit
      }
    }

  val routes = Routes(
    Method.GET / Root          ->
      event(handler((_: Request) => DatastarEvent.patchElements(timeHTML))),
    Method.GET / "server-time" ->
      serverTimeHandler,
  )

  override def run =
    ZIO.logInfo("Starting server on http://localhost:8080") *>
      Server.serve(routes).provide(Server.default)
}
