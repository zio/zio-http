package example.datastar

import zio._

import zio.http._
import zio.http.datastar._
import zio.http.template2._

object FruitExplorerExample extends ZIOAppDefault {

  val routes: Routes[Any, Response] = Routes(
    // Main page route
    Method.GET / ""       -> handler {
      Response(
        headers = Headers(
          Header.ContentType(MediaType.text.html),
        ),
        body = Body.fromCharSequence(indexPage.render),
      )
    },
    Method.GET / "search" -> events {
      handler { (req: Request) =>
        for {
          searchTerm <- ZIO
            .succeed(
              req.url.queryParameters
                .getAll("q")
                .headOption,
            )
          results    <- search(searchTerm)
          _          <- ZIO.when(results.isEmpty)(
            ServerSentEventGenerator.patchElements(div(id("result"), p("No results found."))),
          )
          _          <- ZIO.when(results.nonEmpty) {
            ServerSentEventGenerator.patchElements(div(id("result"), ol(id("list")))) *>
              ZIO.foreachDiscard(results) { r =>
                ServerSentEventGenerator
                  .patchElements(
                    li(r),
                    PatchElementOptions(
                      selector = Some(CssSelector.id("list")),
                      mode = ElementPatchMode.Append,
                      useViewTransition = true,
                    ),
                  )
                  .delay(100.millis)
              }
          }
        } yield ()
      }.orDie
    } @@ Middleware.debug,
  )

  def indexPage = {
    html(
      head(
        meta(charset("UTF-8")),
        meta(name("viewport"), content("width=device-width, initial-scale=1.0")),
        title("Fruit Explorer Example - ZIO HTTP Datastar"),
        script(
          `type` := "module",
          src    := "https://cdn.jsdelivr.net/gh/starfederation/datastar@v1.0.0-RC.5/bundles/datastar.js",
        ),
        style.inlineCss("""
          ::view-transition-old(root),
          ::view-transition-new(root) {
              animation-duration: 0.8s;
          }

          ::view-transition-old(root) {
              animation-timing-function: ease-in-out;
          }

          ::view-transition-new(root) {
              animation-timing-function: ease-in-out;
          }

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
          .form-group {
            margin-bottom: 20px;
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
        """),
      ),
      body(
        div(
          className := "container",
          h1("\uD83D\uDD0E Fruit Explorer \uD83C\uDF47"), {
            val $query                            = Signal[String]("query")
            input(
              `type`                            := "text",
              placeholder                       := "Search ...",
              name                              := "query",
              dataSignals($query)               := "",
              dataBind($query.name),
              dataOn.input.debounce(300.millis) := Js("@get('/search?q=' + $query)"),
              autofocus,
            )
          },
          div(id("result")),
        ),
      ),
    )
  }

  def search(term: Option[String]): Task[List[String]] = ZIO.succeed {
    val data: List[String] = List(
      "Apple",
      "Banana",
      "Orange",
      "Mango",
      "Strawberry",
      "Grape",
      "Watermelon",
      "Pineapple",
      "Peach",
      "Cherry",
      "Pear",
      "Plum",
      "Kiwi",
      "Blueberry",
      "Raspberry",
      "Blackberry",
      "Lemon",
      "Lime",
      "Grapefruit",
      "Avocado",
      "Coconut",
      "Pomegranate",
      "Apricot",
      "Nectarine",
      "Cantaloupe",
      "Honeydew",
      "Fig",
      "Date",
      "Persimmon",
      "Mulberry",
      "Quince",
      "Melon",
      "Greengage",
      "Barberry",
      "Bitter Orange",
      "Sour Cherry",
    )

    if (term.isEmpty) Nil
    else data.filter(_.toLowerCase.contains(term.get.toLowerCase))
  }

  override def run: ZIO[Any, Throwable, Unit] =
    Server
      .serve(routes)
      .provide(Server.default)
}
