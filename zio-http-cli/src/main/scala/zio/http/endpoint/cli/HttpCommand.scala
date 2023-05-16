package zio.http.endpoint.cli

import zio.cli._
import zio.http.endpoint._
import zio._
import zio.http._
import zio.http.endpoint._

object HttpCommand {

    def getCommand[M <: EndpointMiddleware](endpoint: Endpoint[_, _, _, M]): Command[CliRequest] = {
        val cliEndpoint = CliEndpoint.fromEndpoint(endpoint)

        val doc = cliEndpoint.doc.toPlaintext()

        val emptyOptions = Options.Empty.map(_ => CliRequest.empty)
        
        def addOptionsTo(options: List[HttpOptions]): Options[CliRequest] =
            options
                .map(option => option.transform _)
                .foldLeft(emptyOptions) {
                    case(options, f) => f(options)
                }

        val cliRequest: Options[CliRequest] = addOptionsTo(cliEndpoint.getOptions).map(_.withMethod(cliEndpoint.methods))

        val subcommands: Command[CliRequest] = Command(cliEndpoint.commandName, cliRequest).withHelp(doc)

        subcommands     
    } 

    def fromEndpoints[M <: EndpointMiddleware](name: String, endpoints: Chunk[Endpoint[_, _, _, M]]): Command[CliRequest] = {

        val subcommands = endpoints.map(getCommand(_)).reduceOption(_ orElse _)

        subcommands match {
                case Some(subcommands) => Command(name).subcommands(subcommands)
                case None             => Command(name).map(_ => CliRequest.empty)
            }
    }
/*
    def fromEndpoints[M <: EndpointMiddleware](name: String, endpoints: Chunk[Endpoint[_, _, _, M]]): HttpCommand = {

        val cliEndpoints: Chunk[CliEndpoint[_]]   = endpoints.flatMap(CliEndpoint.fromEndpoint(_))

        val subcommand: Option[Command[CliRequest]] = cliEndpoints
            .groupBy(_.commandName)
            .map { case (name, cliEndpoints) =>
                val doc     = cliEndpoints.map(_.doc).map(_.toPlaintext()).mkString("\n\n")
                val options: Options[(Int, Any)] =
                cliEndpoints
                    .map(_.options)
                    .zipWithIndex
                    .map { case (options, index) => options.map(index -> _) }
                    .reduceOption(_ orElse _)
                    .getOrElse(Options.none.map(_ => (-1, CliRequest.empty)))

                val c: Command[CliRequest] = Command(name, options).withHelp(doc).map { case (index, any) =>
                val cliEndpoint = cliEndpoints(index)
                cliEndpoint
                    .asInstanceOf[CliEndpoint[cliEndpoint.Type]]
                    .embed(any.asInstanceOf[cliEndpoint.Type], CliRequest.empty)
                }
                c
            }
            .reduceOption(_ orElse _)

        val command =
            subcommand match {
                case Some(subcommand) => Command(name).subcommands(subcommand)
                case None             => Command(name).map(_ => CliRequest.empty)
            }

        HttpCommand(command)
    }
*/
    

    
}
