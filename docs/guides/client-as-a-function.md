---
id: client-as-a-function
title: "Basic: Client as a Function"
---

# Basic: Client as a Function

This example demonstrates the simplest possible "client" implementation in ZIO HTTP. The entire application is testable by firing HTTP requests at it as if it were a real client.

## Example

```scala mdoc:compile
import zio._
import zio.http._
import zio.http.model._

object ClientAsFunction extends ZIOAppDefault {

  // Define the client request
  val request = Request(Method.GET, URL.decode("https://jsonplaceholder.typicode.com/todos/1").toOption.get)

  // Define the client application
  val clientApp: ZIO[Client, Throwable, Response] = for {
    client <- ZIO.service[Client]
    response <- client.request(request)
  } yield response

  // Run the client application and print the response
  val run = clientApp.flatMap(response => Console.printLine(response.body.asString)).provide(Client.default)
}
```
In this example, we define a simple HTTP client that sends a GET request to a public API and prints the response. The application is testable by creating a request and firing it at the client function.

