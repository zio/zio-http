package zio.http.endpoint.cli

import zio._
import zio.cli._

import zio.http._
import zio.http.endpoint._

/*
 * Methods to construct a command for Cli Command[CliRequest].
 */

object HttpCommand {

  val emptyOptions = Options.Empty.map(_ => CliRequest.empty)

  /*
   * Transforms an Endpoint in its corresponding Command[CliRequest].
   */
  def getCommand[M <: EndpointMiddleware](endpoint: Endpoint[_, _, _, _, M], cliStyle: Boolean): Command[CliRequest] = {
    val cliEndpoint = CliEndpoint.fromEndpoint(endpoint)

    val doc = cliEndpoint.doc.toPlaintext()

    val cliRequest: Options[CliRequest] = addOptionsTo(cliEndpoint)

    val subcommands: Command[CliRequest] = Command(cliEndpoint.commandName(cliStyle), cliRequest).withHelp(doc)

    subcommands
  }

  /*
   * Adds the HttpOptions from options to an empty CliRequest.
   */
  def addOptionsTo(cliEndpoint: CliEndpoint): Options[CliRequest] =
    cliEndpoint.getOptions
      .map(option => option.transform _)
      .foldLeft(emptyOptions) { case (options, f) =>
        f(options)
      }
      .map(_.method(cliEndpoint.methods))

  /*
   * Transforms a chunk of Endpoints in a Command to use directly in the CliApp.
   */
  def fromEndpoints[M <: EndpointMiddleware](
    name: String,
    endpoints: Chunk[Endpoint[_, _, _, _, M]],
    cliStyle: Boolean,
  ): Command[CliRequest] = {

    val subcommands = endpoints.map(getCommand(_, cliStyle)).reduceOption(_ orElse _)

    subcommands match {
      case Some(subcommands) => Command(name).subcommands(subcommands)
      case None              => Command(name).map(_ => CliRequest.empty)
    }
  }

}
