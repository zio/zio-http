---
id: getting_started
title: "Getting Started"
---

Include ZIO in your project by adding the following to your `build.sbt` file:

```scala mdoc:passthrough
println(s"""```""")
if (zio.web.BuildInfo.isSnapshot)
  println(s"""resolvers += Resolver.sonatypeRepo("snapshots")""")
println(s"""libraryDependencies += "dev.zio" %% "zio-http-core" % "${zio.web.BuildInfo.version}"""")
println(s"""```""")
```

## Creating a Protocol Module

To get started you first need to create and instance of a [ProtocolModule](datatypes/modules/protocolmodule.md).
For this example we will use [HttpProtocolModule](datatypes/modules/httpprotocolmodule.md) which describes a set of HTTP
[Endpoints](datatypes/modules/endpoints.md), the [Schema](datatypes/schema.md) that define the messages and the
[Codec](datatypes/codec.md) for encoding and decoding those messages.

Let's create a simple endpoint which will receive a `name` and respond with a greeting:

```scala mdoc
import zio.ZIO
import zio.web.{endpoint, Endpoints, Handler, Handlers}
import zio.web.codec.Codec
import zio.web.http.HttpProtocol
import zio.web.http.model.{ Method, Route }
import zio.schema.Schema

object GreetingModule extends HttpProtocol {

  override val defaultProtocol: Codec           = null
  override val allProtocols: Map[String, Codec] = Map.empty
  override type ProtocolDocs = this.type

  val greetingEndpoint =
    endpoint("greet")
      .withRequest(Schema[String])
      .withResponse(Schema[String]) @@ Route(_ / "greet") @@ Method.GET

  val endpoints = Endpoints(greetingEndpoint)

  val greetingHandler =
    Handler.make(greetingEndpoint) { greeting: String =>
      ZIO.succeed(s"Hi $greeting!")
    }

  val handlers = Handlers(greetingHandler)
}
```

This starts with a basic [Endpoint](datatypes/modules/endpoint.md) with an `endpointName` of `"greet"` and then
specifies that both the request and response messages will have a `Schema` with the type of `String`.

It then provides a `Handler` which is a type alias for a function that takes a request message and returns a value that
describes and effect that will produce the response message. In this case we take a request message of type `String`
called `name` and return a description of a successful response message containing the greeting of `s"Hi $name"`.

Lastly, the endpoint is annotated with a [Route](datatypes/http/route.md) and a [Method](datatypes/http/method.md).

Now that we have defined our `Endpoint` we combine that with the `Endpoints.empty` to specify all the endpoints for our
module.

## Using the Module

Once you have created your module you can use the convenience methods to create a client and server. For example:

```scala mdoc
import zio.console._
import zio.logging.{ LogFormat, LogLevel, Logging, log }
import zio.web.http.{ HttpClientConfig, HttpMiddleware, HttpServerConfig }
import zio.{ ExitCode, URIO, ZLayer }

object Demo extends zio.App {

  import GreetingModule.{endpoints, handlers, greetingEndpoint}

  private val program =
    GreetingModule
      .makeServer(HttpMiddleware.none, endpoints, handlers)
      .build
      .use { _ =>
        for {
          client   <- GreetingModule.makeClient(endpoints).build.useNow
          greeting <- endpoints.invoke(greetingEndpoint)("Jason").provide(client)
          _        <- putStrLn(greeting)
        } yield ExitCode.success
      }
      .catchAll { _ =>
        putStrLnErr("Kaboom!").map(_ => ExitCode.failure)
      }

  private val host = "localhost"
  private val port = 8080

  private val requirements =
    loggingLayer ++
      ZLayer.succeed(HttpServerConfig(host, port)) ++
      ZLayer.succeed(HttpClientConfig(host, port))

  lazy val loggingLayer =
    Logging.console(LogLevel.Debug, LogFormat.ColoredLogFormat()) >>>
      Logging.withRootLoggerName("example")

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    program.provideCustomLayer(requirements)
}
```

This program makes a server with the endpoints, then creates a client that is used to call the `greetingEndpoint` with
the request of `"Jason"`. When the server responds with the greeting response then that will be printed to the console
as `Hi Jason`.
