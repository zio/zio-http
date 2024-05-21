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
## Middleware Combinators in ZIO HTTP

In ZIO HTTP, middleware functions can be combined using various combinators. The supported operators include:

* **`++`**: Combines two middleware functions without changing their input/output types.
* **`>>>`**: Similar to `++`, but allows for different input/output types (horizontal composition).
* **`<<<`**: Similar to `>>>`, but applies the middleware functions in reverse order.
* **`<>`**: An alias for orElse, which applies the second middleware if the first one fails.

### Example of Middleware Combinators
Here is an example demonstrating the use of these combinators:

```scala mdoc:passthrough
import zio._
import zio.http._
import zio.http.middleware._

val authMiddleware = Middleware.basicAuth("user", "password")
val loggingMiddleware = Middleware.debug
val timeoutMiddleware = Middleware.timeout(5.seconds)

// Using `++` combinator
val combinedMiddleware1 = authMiddleware ++ loggingMiddleware ++ timeoutMiddleware

// Using `>>>` combinator
val combinedMiddleware2 = authMiddleware >>> loggingMiddleware >>> timeoutMiddleware

// Using `<<<` combinator
val combinedMiddleware3 = timeoutMiddleware <<< loggingMiddleware <<< authMiddleware

// Using `<>` combinator
val combinedMiddleware4 = Middleware.fail("error") <> Middleware.addHeader("X-Environment", "Dev")

```

#### Other Operators:

* **contraMap,contraMapZIO,delay,flatMap,flatten,map**: which are obvious as their name implies.

* **race** to race middlewares

* **runAfter** and **runBefore** to run effect before and after

* **when** to conditionally run a middleware (input of output Http meets some criteria)


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