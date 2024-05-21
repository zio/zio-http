---
id: custom-middleware
title: "Creating Custom Middleware"
---

# Creating Custom Middleware

ZIO HTTP provides the flexibility to create custom middleware functions using the `Middleware.patchZIO` function. This allows to tailor middleware behaviour to your application's specific needs.

ZIO HTTP provides several helpful functions to construct custom middleware:

* **identity:** Acts as a no-op, returning the input Http without any modifications, similar to the mathematical identity function.

```scala mdoc:silent 
val identityMW: Middleware[Any, Nothing, Nothing, Any, Any, Nothing] = Middleware.identity
```

* **succeed:** Creates a middleware that always returns a successful Http with a specified value.

```scala mdoc:silent 
val middleware: Middleware[Any, Nothing, Nothing, Any, Any, Int] = Middleware.succeed(1)
```

* **fail:** Creates a middleware that always returns a failing Http with a provided error message.

```scala mdoc:silent 
val middleware: Middleware[Any, String, Nothing, Any, Any, Nothing] = Middleware.fail("error")
```

* **collect:** Constructs middleware using a function that takes an Http object and returns a middleware to be applied.

```scala mdoc:silent 
val middleware: Middleware[Any, Nothing, Request, Response, Request, Response] = 
  Middleware.collect[Request](_ => Middleware.addHeaders(Headers("a", "b")))
```

* **collectZIO:** Similar to collect, but uses an effectful function (a ZIO effect) to create the middleware to be applied.

```scala mdoc:silent 
val middleware: Middleware[Any, Nothing, Request, Response, Request, Response] = 
  Middleware.collectZIO[Request](_ => ZIO.succeed(Middleware.addHeaders(Headers("a", "b"))))
```
* **codec:** Creates middleware for custom encoding and decoding between request/response types. It takes two functions: a decoder (converts input type to request) and an encoder (converts response to output type).

```scala mdoc:silent 
val middleware: Middleware[Any, Nothing, Request, Response, Request, Response] = 
  Middleware.collect[Request](_ => Middleware.addHeaders(Headers("a", "b")))
```
* **fromHttp:** Constructs middleware from a predefined Http object.

```scala mdoc:silent 
val app: Http[Any, Nothing, Any, String] = Http.succeed("Hello World!")
val middleware: Middleware[Any, Nothing, Nothing, Any, Request, Response] = Middleware.fromHttp(app)
``
