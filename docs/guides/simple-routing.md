# Basic: Simple Routing

This example demonstrates how to set up simple routing in ZIO HTTP. We will define routes and handle requests accordingly.

## Example

```scala mdoc:compile
import zio._
import zio.http._
import zio.http.model._

object SimpleRouting extends ZIOAppDefault {

  // Define the application with simple routing
  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "hello" => Response.text("Hello, World!")
    case Method.GET -> !! / "goodbye" => Response.text("Goodbye, World!")
  }

  // Start the server with the defined application
  val run = Server.start(8080, app)
}
```

In this example, we set up a simple HTTP server with two routes: one that responds with "Hello, World!" to GET requests on the `/hello` path, and another that responds with "Goodbye, World!" to GET requests on the `/goodbye` path.