package zio.http.endpoint.cli

import zio._
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
    val cliEndpoints = endpoints.flatMap(CliEndpoint.fromEndpoint(_))

    val subcommand = cliEndpoints
      .groupBy(_.commandName)
      .map { case (name, cliEndpoints) =>
        val doc     = cliEndpoints.map(_.doc).map(_.toPlaintext()).mkString("\n\n")
        val options =
          cliEndpoints
            .map(_.options)
            .zipWithIndex
            .map { case (options, index) => options.map(index -> _) }
            .reduceOption(_ orElse _)
            .getOrElse(Options.none.map(_ => (-1, CliRequest.empty)))

        Command(name, options).withHelp(doc).map { case (index, any) =>
          val cliEndpoint = cliEndpoints(index)
          cliEndpoint
            .asInstanceOf[CliEndpoint[cliEndpoint.Type]]
            .embed(any.asInstanceOf[cliEndpoint.Type], CliRequest.empty)
        }
      }
      .reduceOption(_ orElse _)

    val command =
      subcommand match {
        case Some(subcommand) => Command(name).subcommands(subcommand)
        case None             => Command(name).map(_ => CliRequest.empty)
      }

    HttpCliApp {
      CliApp.make(
        name = name,
        version = version,
        summary = summary,
        footer = footer,
        config = config,
        figFont = figFont,
        command = command,
      ) { case CliRequest(url, method, headers, body) =>
        for {
          response <- Client
            .request(
              Request
                .default(
                  method,
                  url.withHost(host).withPort(port),
                  Body.fromString(body.toString),
                )
                .setHeaders(headers),
            )
            .provide(Client.default, Scope.default)
          _        <- Console.printLine(s"Got response")
          _        <- Console.printLine(s"Status: ${response.status}")
          body     <- response.body.asString
          _        <- Console.printLine(s"""Body: ${if (body.nonEmpty) body else "<empty>"}""")
        } yield ()
      }
    }
  }
}
