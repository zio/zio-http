---
id: nested-routes
title: "Basic: Client as a Function"
---
# Basic: Nestable routes

This example demonstrates how to set up nestable routes in ZIO HTTP. Nestable routes allow for organizing routes in a hierarchical manner, making it easier to manage complex routing logic.

## Example

```scala mdoc:passthrough
import zio._
import zio.http._
import zio.http.model._

object NestableRoutes extends ZIOAppDefault {

  // Define nested routes
  val userRoutes: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "users" / "list" => Response.text("User List")
    case Method.GET -> !! / "users" / "details" / id => Response.text(s"User Details for $id")
  }

  val productRoutes: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "products" / "list" => Response.text("Product List")
    case Method.GET -> !! / "products" / "details" / id => Response.text(s"Product Details for $id")
  }

  // Combine nested routes into the main application
  val app: HttpApp[Any, Nothing] = userRoutes ++ productRoutes

  // Start the server with the defined application
  val run = Server.start(8080, app)
}
```
In this example, we define two sets of nested routes: one for user-related endpoints and another for product-related endpoints. These nested routes are then combined into the main application, which is served by the HTTP server.