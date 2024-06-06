---
id: use-a-server-backend
title: "Basic: Use a Server backend"
---

# Basic: Use a Server Backend

This example shows how to both serve an application HttpHandler using an embedded HTTP server and to query it using an HTTP client. All server-backend implementations are launched in an identical manner using implementations of the ServerConfig interface - and a base implementation of this interface is provided for each server backend.

This example shows how to both serve an application using an embedded HTTP server and to query it using an HTTP client in ZIO HTTP. The server-backend implementation is launched and queried in an identical manner using implementations of the ServerConfig interface - and a base implementation of this interface is provided for each server backend

## Example

```scala mdoc:passthrough

import zio._
import zio.http._
import zio.http.model._

object UseServerBackend extends ZIOAppDefault {

  // Define the application as an HttpApp
  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case req @ Method.GET -> !! / "greet" =>
      Response.text(s"Hello, ${req.url.queryParams.get("name").flatMap(_.headOption).getOrElse("World")}!")
  }

  // Define the client request
  val clientRequest = Request(Method.GET, URL.decode("http://localhost:9000/greet?name=John%20Doe").toOption.get)

  // Define the client application
  val clientApp: ZIO[Client, Throwable, Response] = for {
    client <- ZIO.service[Client]
    response <- client.request(clientRequest)
  } yield response

  // Start the server, make a client request, and then stop the server
  val run = for {
    server <- Server.start(8000, app).fork
    _ <- ZIO.sleep(1.second) // Give the server time to start
    response <- clientApp.flatMap(response => Console.printLine(response.body.asString))
    _ <- server.interrupt
  } yield ()
}
```

In this example, we set up a simple HTTP server that listens on port 8000 and responds with a greeting message. We then create a client request to query the server and print the response. Finally, we stop the server.