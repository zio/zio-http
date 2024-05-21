---
id: attach-context-to-a-request
title: "Attach context to a request"
---


Context is useful for passing additional data through the request lifecycle, which can be accessed by various parts of your application. This example demonstrates how to attach and retrieve context in a request using ZIO HTTP.

```scala mdoc:passthrough
import zio._
import zio.http._
import zio.http.model._

object AttachContextToRequest extends ZIOAppDefault {

  // Define a type for the context
  case class RequestContext(userId: String, isAdmin: Boolean)

  // Define the application with context
  val app: HttpApp[RequestContext, Nothing] = Http.collectZIO[Request] {
    case req @ Method.GET -> !! / "profile" =>
      for {
        ctx <- ZIO.service[RequestContext]  // Retrieve the context
        response = Response.text(s"User ID: ${ctx.userId}, Admin: ${ctx.isAdmin}")
      } yield response
  }

  // Create a request and attach context
  val request = Request(Method.GET, URL(!! / "profile"))
  val context = RequestContext("user123", isAdmin = true)

  // Run the application with the attached context
  val run = for {
    response <- app.provideLayer(ZLayer.succeed(context))(request)
    _ <- Console.printLine(response)
  } yield ()
}
```
In this example, we define a simple HTTP application that retrieves user profile information. The application expects a context of type `RequestContext`, which includes user ID and admin status. This context is attached to the request and used in the request handler to generate a personalized response.