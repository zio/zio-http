package zio.http.endpoint.cli

import zio._
import zio.ZIOAppDefault
import zio.cli._
import zio.cli.figlet.FigFont

import zio.http._
import zio.http.endpoint._

/**
 * Command-line interface for an HTTP application.
 */
final case class HttpCliApp[-R, +E, +Model](cliApp: CliApp[R, E, Model])
object HttpCliApp {

  /**
   * Generates a [[HttpCliApp]] from the given endpoints.
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
   * @return
   *   a [[HttpCliApp]]
   */
  def fromEndpoints[M <: EndpointMiddleware](
    name: String,
    version: String,
    summary: HelpDoc.Span,
    endpoints: Chunk[Endpoint[_, _, _, M]],
    host: String,
    port: Int,
    footer: HelpDoc = HelpDoc.Empty,
    config: CliConfig = CliConfig.default,
    figFont: FigFont = FigFont.Default,
  ): HttpCliApp[Any, Throwable, CliRequest] = {
    HttpCliApp {
      CliApp.make(
        name = name,
        version = version,
        summary = summary,
        footer = footer,
        config = config,
        figFont = figFont,
        command = HttpCommand.fromEndpoints(name, endpoints),
      ) { 
        case req @ CliRequest(_, _, _, _, mustPrint, mustSave) =>
          for {
            request <- req.toRequest(host, port)
            response <- Client
              .request(request)
              .provide(Client.default)
            _        <- Console.printLine(s"Got response")
            _        <- Console.printLine(s"Status: ${response.status}")
            _        <- ZIO.when(mustPrint)(printResponse(response))
            _        <- ZIO.when(mustSave)(saveResponse(response))
          } yield ()
      }
    }
  }

  private def printResponse(response: Response): Task[Unit] = for {
    body     <- response.body.asString
    _        <- Console.printLine(s"""Body: ${if (body.nonEmpty) body else "<empty>"}""")
  } yield ()

  private def saveResponse(response: Response): Task[Unit] = ???


}