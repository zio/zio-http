package example.datastar

import zio._

import zio.http._
import zio.http.datastar._
import zio.http.template2._

object GreetingFormExample extends ZIOAppDefault {

  val routes: Routes[Any, Response] = Routes(
    Method.GET / ""      ->
      event(handler((_: Request) => DatastarEvent.patchElements(indexPage))),
    Method.GET / "greet" -> event {
      handler { (req: Request) =>
        DatastarEvent.patchElements(
          div(
            id("greeting"),
            p(s"Hello ${req.queryParam("name").getOrElse("Guest")}"),
          ),
        )
      }
    } @@ Middleware.debug,
  )

  def indexPage = html(
    head(
      meta(charset("UTF-8")),
      meta(name("viewport"), content("width=device-width, initial-scale=1.0")),
      title("Greeting Form - ZIO HTTP Datastar"),
      datastarScript,
      style.inlineCss(css),
    ),
    body(
      div(
        className := "container",
        h1("👋 Greeting Form 👋"),
        form(
          id("greetingForm"),
          dataOn.submit := js"@get('/greet', {contentType: 'form'})",
          label(
            `for`("name"),
            "What's your name?",
          ),
          input(
            `type`("text"),
            id("name"),
            name("name"),
            placeholder("Enter your name!"),
            required,
            autofocus,
          ),
          button(
            `type`("submit"),
            "Greet me!",
          ),
        ),
        div(id("greeting")),
      ),
    ),
  )

  val css =
    """
  body {
    font-family: system-ui, -apple-system, sans-serif;
    max-width: 600px;
    margin: 50px auto;
    padding: 20px;
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    min-height: 100vh;
  }
    .container {
      background: white;
      border-radius: 10px;
      padding: 30px;
      box-shadow: 0 10px 40px rgba(0,0,0,0.2);
    }
  h1 {
    color: #333;
    margin-bottom: 30px;
  }
  label {
    display: block;
    margin-bottom: 8px;
    color: #555;
    font-weight: 500;
  }
  input[type="text"] {
    width: 100%;
    padding: 12px;
    margin-bottom: 20px;
    border: 2px solid #e0e0e0;
    border-radius: 6px;
    font-size: 16px;
    transition: border-color 0.3s;
    box-sizing: border-box;
  }
  input[type="text"]:focus {
    outline: none;
    border-color: #667eea;
  }
  button {
    background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
    color: white;
    padding: 12px 30px;
    border: none;
    border-radius: 6px;
    font-size: 16px;
    font-weight: 600;
    cursor: pointer;
    transition: transform 0.2s, box-shadow 0.2s;
  }
  button:hover {
    transform: translateY(-2px);
    box-shadow: 0 5px 20px rgba(102, 126, 234, 0.4);
  }
  button:active {
    transform: translateY(0);
  }
  button:disabled {
    opacity: 0.6;
    cursor: not-allowed;
  }
  #greeting {
    margin-top: 30px;
    padding: 20px;
    background: #f0f4ff;
    border-left: 4px solid #667eea;
    border-radius: 6px;
    font-size: 18px;
    color: #333;
  }
  #greeting:empty {
    display: none;
  }
  """

  override def run: ZIO[Any, Throwable, Unit] =
    Server
      .serve(routes)
      .provide(Server.default)
}
