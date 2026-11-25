package zio.http.endpoint.cli

import zio._
import zio.cli._
import zio.cli.figlet.FigFont

import zio.http._
import zio.http.endpoint._

/**
 * Command-line interface for an HTTP application. It wraps a `CliApp` whose
 * commands send HTTP requests and print the responses.
 */
final case class HttpCliApp[-R, +E, +Model](cliApp: CliApp[R, E, Model])
/** Constructor for [[HttpCliApp]]. */
object HttpCliApp {

  /**
   * Generates a [[HttpCliApp]] from the given endpoints. Each endpoint becomes
   * one command. The app sends the request to `host` and `port`, prints the
   * status of the response, and prints its body when the request asks for it.
   *
   * {{{
   * import zio._
   * import zio.cli.HelpDoc
   * import zio.http._
   * import zio.http.endpoint._
   *
   * val hello = Endpoint(Method.GET / "hello").out[String]
   *
   * // The app has one command, `get-hello`, that sends GET http://localhost:8080/hello
   * val app: HttpCliApp[Any, Throwable, Response] =
   *   HttpCliApp.fromEndpoints("hello-cli", "1.0.0", HelpDoc.Span.text("Calls the hello endpoint"), Chunk(hello), "localhost", 8080)
   * }}}
   *
   * @param name
   *   The name of the generated CLI
   * @param version
   *   The version of the generated CLI
   * @param summary
   *   The summary of the generated CLI
   * @param endpoints
   *   Chunk of [[Endpoint]]
   * @param host
   *   The server host where the generated CLI will send requests to
   * @param port
   *   The server port where the generated CLI will send requests to
   * @param footer
   *   Footer for the help docs of the generated CLI
   * @param config
   *   Configuration of the generated CLI
   * @param figFont
   *   FigFont to use for man pages of the generated CLI
   * @param cliStyle
   *   Style of commands of the generated CLI: true for CLI idiomatic interface
   *   (the lowercase method, `create` for POST and `update` for PUT, followed
   *   by the literal path segments, joined with `-`, as in `get-hello`), false
   *   for HTTP-like interface (the method followed by the path, as in
   *   `get /hello`)
   * @param client
   *   The client that the generated CLI uses to send the request and to read
   *   body parts from URLs. The default uses `Client.default`.
   * @return
   *   a [[HttpCliApp]]
   */
  def fromEndpoints(
    name: String,
    version: String,
    summary: HelpDoc.Span,
    endpoints: Chunk[Endpoint[_, _, _, _, _]],
    host: String,
    port: Int,
    footer: HelpDoc = HelpDoc.Empty,
    config: CliConfig = CliConfig.default,
    figFont: FigFont = FigFont.Default,
    cliStyle: Boolean = true,
    client: CliClient = DefaultClient(),
  ): HttpCliApp[Any, Throwable, Response] = {
    HttpCliApp {
      CliApp.make(
        name = name,
        version = version,
        summary = summary,
        footer = footer,
        config = config,
        figFont = figFont,
        command = HttpCommand.fromEndpoints(name, endpoints, cliStyle),
      ) { case req @ CliRequest(_, _, _, _, mustPrint, mustSave) =>
        for {
          request  <- req.toRequest(host, port, client)
          response <- client match {
            case CliZIOClient(client)    => client.batched.apply(request)
            case CliZLayerClient(client) => Client.batched(request).provide(client)
            case DefaultClient()         => Client.batched(request).provide(Client.default)
          }

          _ <- Console.printLine(s"Got response")
          _ <- Console.printLine(s"Status: ${response.status}")
          _ <- ZIO.when(mustPrint)(printResponse(response))
          _ <- ZIO.when(mustSave)(saveResponse(response))
        } yield response
      }
    }
  }

  private def printResponse(response: Response): Task[Unit] = for {
    body <- response.body.asString
    _    <- Console.printLine(s"""Body: ${if (body.nonEmpty) body else "<empty>"}""")
  } yield ()

  private def saveResponse(response: Response): Task[Unit] = ???

}
