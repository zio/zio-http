---
id: middleware
title: "Middleware"
---

# Middleware

Middleware in ZIO HTTP plays a crucial role in modifying incoming requests or outgoing responses. It allows for the implementation of cross-cutting concerns such as logging, authentication, and error handling in a reusable and composable manner.


## Key Concepts of Middleware:

### Purpose of Middleware

- Middleware functions are used to process requests before they reach the endpoint handler or to modify responses before they are sent to the client.
- They enable the separation of concerns by allowing common functionality to be applied across multiple endpoints.

### Role of Middleware in ZIO HTTP

- Middleware can be attached to individual routes or globally to all routes.
- They can be chained together to form a pipeline, where each middleware function processes the request or response in sequence.
- Middleware can be combined to create more complex behaviour by composing simpler middleware functions.
- Custom middleware can be created to handle specific requirements, such as custom authentication or request validation.

### Anatomy of Middleware

In ZIO HTTP, middleware is represented as a function with a specific type signature:

```scala mdoc:silent 
type Middleware[R, E, AIn, BIn, AOut, BOut] = Http[R, E, AIn, BIn] => Http[R, E, AOut, BOut]
```
* **`R`**: Environment type (*e.g., database connection pool*)
* **`E`**: Error type
* **`AIn`**: Input type of the incoming Http (*request*)
* **`BIn`**: Output type of the incoming Http (*response*)
* **`AOut`**: Input type of the outgoing Http (*modified request*)
* **`BOut`**: Output type of the outgoing Http (*modified response*)

## Attaching and Chaining Middleware:

The `@@` operator is used to attach middleware to an `Http` app. It allows you to chain multiple middleware functions for a sequence of transformations.


Chaining middleware empowers you to build complex application behaviours by composing simpler middleware functions. This modular approach promotes code maintainability and reusability.

## Combining Middlewares

Middleware functions can be combined together using various combinators provided by ZIO HTTP using `++, <<<, >>> and <>`. Combining middleware allows developers to build complex behaviour by combining simpler middleware functions.

For example, if we have three middleware's f1, f2, f3

`f1 >>> f2 >>> f3` applies on an http, sequentially feeding an http to f1 first followed by f2 and f3.

```scala mdoc:silent 
f1 >>> f2 >>> f3   // applies f1 first, then f2 and f3 sequentially
f1 ++ f2 ++ f3    // applies f1, then f2 and f3 from left to right
```

#### Example Usage of `++` combinator

`++` is an alias for combine. It combines two middlewares without changing their input/output types (*AIn = AOut / BIn = BOut*)

For example, if we have three middlewares `f1, f2, f3`

`f1 ++ f2 ++ f3` applies on an http, from left to right with f1 first followed by others, like this

```
  f3(f2(f1(http)))
```


#### Other Operators:

* **contraMap,contraMapZIO,delay,flatMap,flatten,map**: which are obvious as their name implies.

* **race** to race middlewares

* **runAfter** and **runBefore** to run effect before and after

* **when** to conditionally run a middleware (input of output Http meets some criteria)

**Example: Composing Middleware**

```scala mdoc:silent 
import zio.http.Middleware

// Define middleware functions
val authMiddleware = Middleware.basicAuth("user", "password")
val loggingMiddleware = Middleware.debug
val timeoutMiddleware = Middleware.timeout(5.seconds)

// Compose middleware
val composedMiddleware = authMiddleware ++ loggingMiddleware ++ timeoutMiddleware
```
## Built-in Middlewares

ZIO HTTP provides a collection of built-in middleware functions such as authentication, logging, request validation and more. Some of the out-of-the-box middleware functions include:

#### Authentication
* **Middleware.basicAuth(username: String, password: String):** Provides basic authentication for HTTP endpoints. It checks if the incoming request includes a valid authorization header with the provided credentials.

#### Logging
* **Middleware.debug:** Logs debug information about incoming requests and outgoing responses.

#### Timeouts
* **Middleware.timeout(duration: Duration):** Sets a timeout for incoming requests, cIf a request takes longer than the specified duration to complete, it is cancelled.

#### Request/Response Modification

* **Middleware.addHeader:** Adds custom headers to outgoing responses.
* **Middleware.cors:** Handles Cross-Origin Resource Sharing (CORS) for HTTP endpoints.
* **Middleware.csrf:** Provides protection against Cross-Site Request Forgery (CSRF) attacks.


## Simple Middleware Example

```scala mdoc:silent
import zio._
import zio.http._
import zio.http.middleware._

val loggingMiddleware: Middleware[Any, Nothing] =
  Middleware.log

val app: HttpApp[Any, Nothing] =
  Http.collect[Request] {
    case Method.GET -> !! / "hello" => Response.text("Hello, World!")
  } @@ loggingMiddleware

val run = Server.start(8080, app)
```

## Creating Custom Middleware

ZIO HTTP provides the flexibility to create custom middleware functions using the `Middleware.patchZIO` function. This allows to tailor middleware behavior to your application's specific needs.

ZIO HTTP provides several helpful functions to construct custom middleware:

* **identity:** Acts as a no-op, returning the input Http without any modifications, similar to the mathematical identity function.

* **succeed:** Creates a middleware that always returns a successful Http with a specified value.


* **fail:** Creates a middleware that always returns a failing Http with a provided error message.

* **collect:** Constructs middleware using a function that takes an Http object and returns a middleware to be applied.

* **collectZIO:** Similar to collect, but uses an effectful function (a ZIO effect) to create the middleware to be applied.

* **codec:** Creates middleware for custom encoding and decoding between request/response types. It takes two functions: a decoder (converts input type to request) and an encoder (converts response to output type).

* **fromHttp:** Constructs middleware from a predefined Http object.
 
## Transforming Middleware (Advanced Techniques)

ZIO HTTP offers powerful ways to transform existing middleware functions, enabling to create more complex processing pipelines. Here's a breakdown of key transformation techniques:

#### Transforming Output Type

* **map and mapZIO**: These functions allows to modify the output type of the `Http` object produced by a middleware function.

   - **map:** Takes a pure function that transforms the output value.
  - **mapZIO:** Takes an effectful function (a `ZIO` effect) that transforms the output value.

```scala mdoc:silent 
val mid1: Middleware[Any, Nothing, Nothing, Any, Any, String] = middleware.map((i: Int) => i.toString)  // Pure transformation
val mid2: Middleware[Any, Nothing, Nothing, Any, Any, String] = middleware.mapZIO((i: Int) => ZIO.succeed(s"$i"))  // Effectful transformation
```

#### Transforming Input Type

* **contramap and contramapZIO:** These functions are used to modify the input type of the Http object a middleware function accepts.
  - **contramap:** Takes a pure function that transforms the input value.
  - **contramapZIO:** Takes an effectful function (a ZIO effect) that transforms the input value

```scala mdoc:silent 
val mid1: Middleware[Any, Nothing, Int, Int, String, Int] = middleware.contramap[String](_.toInt)  // Pure transformation
val mid2: Middleware[Any, Nothing, Int, Int, String, Int] = middleware.contramapZIO[String](a => UIO(a.toInt)) // Effectful transformation
```

### Conditional Application for Middlewares

* **when and whenZIO:** These functions conditionally apply a middleware based on a predicate function. They only execute the middleware if the predicate evaluates to `true`.
  - **when:** Takes a pure predicate function.
  - **whenZIO:** Takes an effectful predicate function (a `ZIO` effect).

#### Logical Operators for Middleware Selection

* **ifThenElse and ifThenElseZIO:** These functions allow you to select a middleware based on a predicate. They work similarly to the if-else construct in programming languages.
  - **ifThenElse:** Takes pure functions for the `true` and `false` branches.
  - **ifThenElseZIO:** Takes effectful functions (ZIO effects) for the `true` and `false` branches


