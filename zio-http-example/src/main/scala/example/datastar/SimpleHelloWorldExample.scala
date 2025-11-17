package example.datastar

import zio._
import zio.http._
import zio.http.datastar._
import zio.http.endpoint.Endpoint
import zio.http.template2._

object SimpleHelloWorldExample extends ZIOAppDefault {
  val message = "Hello, world!"

  val routes: Routes[Any, Response] = Routes(
    // Main page route
    Method.GET / Root          -> handler {
      Response(
        headers = Headers(
          Header.ContentType(MediaType.text.html),
        ),
        body = Body.fromCharSequence(indexPage.render),
      )
    },
    Method.GET / "hello-world" -> events {
      handler {
        ZIO.foreachDiscard(message.indices) { i =>
          for {
            _ <- ServerSentEventGenerator.executeScript(js"console.log('Sending substring(0, ${i + 1})')")
            _ <- ServerSentEventGenerator.patchElements(div(id("message"), message.substring(0, i + 1)))
            _ <- ZIO.sleep(100.millis)
          } yield ()
        }
      }
    },
  )

  def indexPage = html(
    head(
      meta(charset("UTF-8")),
      meta(name("viewport"), content("width=device-width, initial-scale=1.0")),
      title("Datastar Hello World - ZIO HTTP Datastar"),
      script(
        `type` := "module",
        src    := "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.0-RC.5/bundles/datastar.js",
      ),
      style.inlineCss(css),
    ),
    body(
      dataOn.load := Endpoint(Method.GET / "hello-world").out[String].datastarRequest(()),
      div(
        className := "container",
        h1("Hello World Example"),
        div(id("message")),
      ),
    ),
  )

  override def run: ZIO[Any, Throwable, Unit] =
    Server
      .serve(routes)
      .provide(Server.default)

  val css = """
    body {
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      min-height: 100vh;
      font-family: system-ui, -apple-system, sans-serif;
      font-size: 1.5rem;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      margin: 0;
      padding: 20px;
    }
    .container {
      text-align: center;
      background: white;
      border-radius: 10px;
      padding: 30px;
      box-shadow: 0 10px 40px rgba(0,0,0,0.2);
      max-width: 600px;
      width: 100%;
    }
    h1 {
      font-size: 3rem;
      color: #333;
      margin-bottom: 30px;
      margin-top: 0;
    }
    #message {
      font-size: 2rem;
      margin-top: 2rem;
      padding: 20px;
      background: #f0f4ff;
      border-left: 4px solid #667eea;
      border-radius: 6px;
      color: #333;
      min-height: 50px;
      display: flex;
      align-items: center;
      justify-content: center;
    }
    """
}
