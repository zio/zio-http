package example.datastar

import zio._
import zio.json._

import zio.http._
import zio.http.datastar._
import zio.http.template2._

case class Delay(value: Int) extends AnyVal

object Delay {
  implicit val jsonCodec: JsonCodec[Delay] = DeriveJsonCodec.gen
}

object HelloWorldWithCustomDelayExample extends ZIOAppDefault {

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
      handler { (request: Request) =>
        val delay = request.url.queryParams
          .getAll("datastar")
          .headOption
          .flatMap { s =>
            Delay.jsonCodec.decodeJson(s).toOption
          }
          .getOrElse(Delay(100))

        ZIO.foreachDiscard(message.indices) { i =>
          for {
            _ <- ServerSentEventGenerator.executeScript("console.log('Sending character index: ' + " + i + ");")
            _ <- ServerSentEventGenerator.patchElements(
              div(id("message"), message.substring(0, i + 1)),
            )
            _ <- ZIO.sleep(delay.value.millis)
          } yield ()
        }
      }
    },
  )

  def indexPage = {
    html(
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
        div(
          className := "container",
          h1("Hello World Example"),
          div(
            dataSignals(Signal[Int]("delay")) := js"100",
            label("Delay (ms): ", `for` := "delay"),
            input(
              `type`                    := "number",
              step                      := "100",
              dataBind("delay"),
            ),
          ),
          button(
            dataOn.click := Js("@get('/hello-world')"),
          )("Start Animation"),
          div(id("message")),
        ),
      ),
    )
  }

  override def run: ZIO[Any, Throwable, Unit] =
    Server
      .serve(routes)
      .provide(Server.default)

  val css = """
    body {
      display: flex;
      flex-direction: row;
      align-items: center;
      justify-content: center;
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
    }
    h1 {
      font-size: 3rem;
      color: #333;
      margin-bottom: 30px;
    }
    label {
      display: inline-block;
      margin-right: 10px;
      color: #555;
      font-weight: 500;
    }
    input[type="number"] {
      padding: 8px 12px;
      border: 2px solid #e0e0e0;
      border-radius: 6px;
      font-size: 1rem;
      transition: border-color 0.3s;
    }
    input[type="number"]:focus {
      outline: none;
      border-color: #667eea;
    }
    button {
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      font-size: 1.5rem;
      padding: 1rem 2rem;
      margin-top: 2rem;
      border: none;
      border-radius: 6px;
      font-weight: 600;
      transition: transform 0.2s, box-shadow 0.2s;
    }
    button:hover {
      transform: translateY(-2px);
      box-shadow: 0 5px 20px rgba(102, 126, 234, 0.4);
    }
    button:active {
      transform: translateY(0);
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
    }
    #message:empty {
      display: none;
    }
    """
}
