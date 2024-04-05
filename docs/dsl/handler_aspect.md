---
id: handler_aspect
title: HandlerAspect
---

A `HandlerAspect` is a wrapper around `ProtocolStack` with the two following features:

- It is a `ProtocolStack` that only works with `Request` and `Response` types. So it is suitable for writing middleware in the context of HTTP protocol. So it can almost be thought of (not the same) as a `ProtocolStack[Env, Request, Request, Response, Response]]`.

- It is specialized to work with an output context `CtxOut` that can be passed through the middleware stack. This allows each layer to add its own output context to the transformation process. So the `CtxOut` will be a tuple of all the output contexts that each layer in the stack has added. These output contexts are useful when we are writing middleware that needs to pass some information, which is the result of some computation based on the input request, to the handler that is at the end of the middleware stack. For example, a layer that is responsible for authentication may want to pass the user information to the handler, this is where the output context comes into play.

Now, we are ready to see the definition of `HandlerAspect`:

```scala
final case class HandlerAspect[-Env, +CtxOut](
  protocol: ProtocolStack[Env, Request, (Request, CtxOut), Response, Response]
) extends Middleware[Env] {
    def apply[Env1 <: Env, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] = ???
}
```

Like the `ProtocolStack`, the `HandlerAspect` is a stack of layers. When we compose two `HandlerAspect` using the `++` operator, we are composing middlewares sequentially. So each layer in the stack corresponds to a separate transformation.

Similar to the `ProtocolStack`, each layer in the `HandlerAspect` may also be stateful at the level of each transformation. So, for example, a layer that is timing request durations may capture the start time of the request in the incoming interceptor, and pass this state to the outgoing interceptor, which can then compute the duration.


## Creating a HandlerAspect

The `HandlerAspect`'s companion object provides many methods to create a `HandlerAspect`. But in this section, we are going to introduce the most basic ones that are used as a building block to create a more complex `HandlerAspect`.

The `HandlerAspect.identity` is the simplest `HandlerAspect` that does nothing. It is useful when you want to create a `HandlerAspect` that does not modify the request or response.

After this simple `HandlerAspect`, let's dive into the `HandlerAspect.intercept*` constructors. Using these, we can create a `HandlerAspect` that can intercept the incoming request, outgoing response, or both:

### Intercepting the Incoming Requests

The `HandlerAspect.interceptIncomingHandler` constructor takes a handler function and applies it to the incoming request. It is useful when we want to modify or access the request before it reaches the handler or the next layer in the stack.

Let's see an example of how to use this constructor to create a middleware that checks the IP address of the incoming request and allows only the whitelisted IP addresses to access the server:

```scala mdoc:compile-only
import zio._
import zio.http._

val whitelistMiddleware: HandlerAspect[Any, Unit] =
  HandlerAspect.interceptIncomingHandler {
    val whitelist = Set("127.0.0.1", "0.0.0.0")
    Handler.fromFunctionZIO[Request] { request =>
      request.headers.get("X-Real-IP") match {
        case Some(host) if whitelist.contains(host) =>
          ZIO.succeed((request, ()))
        case _ =>
          ZIO.fail(Response.forbidden("Your IP is banned from accessing the server."))
      }
    }
  }
```

### Intercepting the Outgoing Responses

The `HandlerAspect.interceptOutgoingHandler` constructor takes a handler function and applies it to the outgoing response. It is useful when we want to modify or access the response before it reaches the client or the next layer in the stack.

Let's work on creating a middleware that adds a custom header to the response:

```scala mdoc:compile-only
val addCustomHeader: HandlerAspect[Any, Unit] =
  HandlerAspect.interceptOutgoingHandler(
    Handler.fromFunction[Response](_.addHeader("X-Custom-Header", "Hello from Custom Middleware!")),
  )
```

The `interceptOutgoingHandler` takes a handler function that receives a `Response` and returns a `Response`. This is simpler than the `interceptIncomingHandler` as it does not necessitate the output context to be passed along with the response.

### Intercepting Both Incoming Requests and Outgoing Responses

The `HandlerAspect.interceptHandler` takes two handler functions, one for the incoming request and one for the outgoing response.

### Intercepting Statefully

The `HandlerAspect.interceptHandlerStateful` constructor is like the `interceptHandler`, but it allows the incoming handler to have a state that can be passed to the next layer in the stack, and finally, that state can be accessed by the outgoing handler.

