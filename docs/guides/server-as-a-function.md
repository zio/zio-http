---
id: server-as-a-function
title: "Basic: Server as a Function"
---

# Basic: Server as a Function

This example demonstrates the simplest possible "server" implementation in ZIO HTTP. Note that we are not spinning up a server-backend here - but the entire application is testable by firing HTTP requests at it as if it were.

## Example

```scala mdoc:compile
import zio._
import zio.http._
import zio.http.model._

object ServerAsFunction extends ZIOAppDefault {

  // Define the application as an HttpApp
  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case req @ Method.GET -> !! / "greet" =>
      Response.text(s"Hello, ${req.url.queryParams.get("name").flatMap(_.headOption).getOrElse("World")}!")
  }

  // Create a request to test the application
  val request = Request(Method.GET, URL(!! / "greet").setQueryParams(Map("name" -> List("John Doe"))))

  // Run the application and print the response
  val run = for {
    response <- app(request)
    _ <- Console.printLine(response)
  } yield ()
}
```

In this example, we define a simple HTTP application that responds with a greeting message. The application is testable by creating a request and firing it at the application function.