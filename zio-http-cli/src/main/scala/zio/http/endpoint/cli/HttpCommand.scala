package zio.http.endpoint.cli

import zio._
import zio.cli._

import zio.http.endpoint._

/*
 * Methods to construct a command for Cli Command[CliRequest].
 */

/** Builds the zio-cli commands for [[zio.http.endpoint.Endpoint]]s. */
object HttpCommand {

  /** Options that parse nothing and yield `CliRequest.empty`. */
  val emptyOptions = Options.Empty.map(_ => CliRequest.empty)

  /*
   * Transforms an Endpoint in its corresponding Command[CliRequest].
   */
  /**
   * Returns the command for `endpoint`. Its options come from the path, query
   * parameters, headers and body of the endpoint (for example `--page <integer>`
   * for an integer query parameter `page`), and its help text is the doc of the endpoint.
   * `cliStyle` selects the command name, as in `HttpCliApp.fromEndpoints`.
   */
  def getCommand(endpoint: Endpoint[_, _, _, _, _], cliStyle: Boolean): Command[CliRequest] = {
    val cliEndpoint = CliEndpoint.fromEndpoint(endpoint)

    val doc = cliEndpoint.doc.toPlaintext()

    val cliRequest: Options[CliRequest] = addOptionsTo(cliEndpoint)

    val subcommands: Command[CliRequest] = Command(cliEndpoint.commandName(cliStyle), cliRequest).withHelp(doc)

    subcommands
  }

  /*
   * Adds the HttpOptions from options to an empty CliRequest.
   */
  /**
   * Returns options that parse the `HttpOptions` of `cliEndpoint` into one
   * `CliRequest` and set its method to the method of the endpoint.
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
  /**
   * Returns the command `name` with one subcommand per endpoint, built with
   * `getCommand`. With no endpoints, the command has no subcommands and yields
   * `CliRequest.empty`.
   */
  def fromEndpoints(
    name: String,
    endpoints: Chunk[Endpoint[_, _, _, _, _]],
    cliStyle: Boolean,
  ): Command[CliRequest] = {

    val subcommands = endpoints.map(getCommand(_, cliStyle)).reduceOption(_ orElse _)

    subcommands match {
      case Some(subcommands) => Command(name).subcommands(subcommands)
      case None              => Command(name).map(_ => CliRequest.empty)
    }
  }

}
