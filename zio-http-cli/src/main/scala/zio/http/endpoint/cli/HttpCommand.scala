package zio.http.endpoint.cli

import zio.cli._
import zio.http.endpoint._
import zio._
import zio.http._
import zio.http.endpoint._

/*
 * Methods to construct a command for Cli Command[CliRequest].
 */

object HttpCommand {

    /*
     * Transforms an Endpoint in its corresponding Command[CliRequest].
     */
    def getCommand[M <: EndpointMiddleware](endpoint: Endpoint[_, _, _, M], cliStyle: Boolean): Command[CliRequest] = {
        val cliEndpoint = CliEndpoint.fromEndpoint(endpoint, true)

        val doc = cliEndpoint.doc.toPlaintext()

        val emptyOptions = Options.Empty.map(_ => CliRequest.empty)
        
	    /*
         * Adds the HttpOptions from options to an empty CliRequest.
         */
        def addOptionsTo(options: List[HttpOptions]): Options[CliRequest] =
            options
                .map(option => option.transform _)
                .foldLeft(emptyOptions) {
                    case(options, f) => f(options)
                }

        val cliRequest: Options[CliRequest] = addOptionsTo(cliEndpoint.getOptions).map(_.withMethod(cliEndpoint.methods))

        val subcommands: Command[CliRequest] = Command(cliEndpoint.commandName(cliStyle), cliRequest).withHelp(doc)

        subcommands     
    } 

    /*
     * Transforms a chunk of Endpoints in a Command to use directly in the CliApp.
     */
    def fromEndpoints[M <: EndpointMiddleware](name: String, endpoints: Chunk[Endpoint[_, _, _, M]], cliStyle: Boolean): Command[CliRequest] = {

        val subcommands = endpoints.map(getCommand(_, cliStyle)).reduceOption(_ orElse _)

        subcommands match {
                case Some(subcommands) => Command(name).subcommands(subcommands)
                case None             => Command(name).map(_ => CliRequest.empty)
            }
    }
    
}
