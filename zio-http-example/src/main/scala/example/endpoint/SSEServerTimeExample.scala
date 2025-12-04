//> using dep "dev.zio::zio:2.1.22"
//> using dep "dev.zio::zio-http:3.5.1"

package example.endpoint

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

import zio._

import zio.stream._

import zio.http._
import zio.http.endpoint.AuthType.None
import zio.http.endpoint._
import zio.http.template2._

import example.endpoint.{style => _, _}

object SSEServerTimeExample extends ZIOAppDefault {

  val sseEndpoint: Endpoint[Unit, Unit, ZNothing, ZStream[Any, Nothing, ServerSentEvent[String]], None] =
    Endpoint(Method.GET / "server-time")
      .outStream[ServerSentEvent[String]](MediaType.text.`event-stream`)

  // Stream that emits current time every second
  val timeStream: ZStream[Any, Nothing, ServerSentEvent[String]] =
    ZStream.repeatWithSchedule(
      ServerSentEvent(DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalDateTime.now)),
      Schedule.fixed(1.second),
    )

  val sseRoute: Route[Any, Nothing] =
    sseEndpoint.implementHandler(Handler.succeed(timeStream))

  val pageRoute: Route[Any, Nothing] =
    Method.GET / Root -> handler {
      val page = html(
        head(
          meta(charset := "UTF-8"),
          meta(name    := "viewport", content := "width=device-width, initial-scale=1.0"),
          titleAttr := "Server Time using SSE",
          style.inlineCss("""
            body {
              font-family: Arial, sans-serif;
              display: flex;
              justify-content: center;
              align-items: center;
              min-height: 100vh;
              margin: 0;
              background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
            }
            .container {
              text-align: center;
              background: white;
              padding: 3rem;
              border-radius: 20px;
              box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
            }
            h1 {
              color: #333;
              margin-bottom: 2rem;
              font-size: 2rem;
            }
            #time {
              font-size: 4rem;
              font-weight: bold;
              color: #667eea;
              font-family: 'Courier New', monospace;
              text-shadow: 2px 2px 4px rgba(0, 0, 0, 0.1);
            }
            .status {
              margin-top: 1rem;
              font-size: 0.9rem;
              color: #666;
            }
            .connected {
              color: #10b981;
            }
            .disconnected {
              color: #ef4444;
            }
          """.stripMargin),
        ),
        body(
          div(
            className := "container",
            h1("Server Time"),
            div(id        := "time", "Connecting..."),
            div(className := "status", id := "status", "Establishing connection..."),
          ),
          script.inlineJs(js"""
            const timeElement = document.getElementById('time');
            const statusElement = document.getElementById('status');

            const eventSource = new EventSource('/server-time');

            eventSource.onopen = function() {
              statusElement.textContent = 'Connected';
              statusElement.className = 'status connected';
            };

            eventSource.onmessage = function(event) {
              timeElement.textContent = event.data;
            };

            eventSource.onerror = function(error) {
              statusElement.textContent = 'Connection lost. Reconnecting...';
              statusElement.className = 'status disconnected';
            };
          """.stripMargin),
        ),
      )
      Response.html(page)
    }

  val routes: Routes[Any, Response] = Routes(pageRoute, sseRoute)

  def run = Server.serve(routes).provide(Server.default)
}
