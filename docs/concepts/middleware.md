---
id: middleware
title: "Middleware"
---

# Middleware

Middleware plays a crucial role in ZIO HTTP by allowing to modify incoming requests or outgoing responses. This section explains the concept of middleware and demonstrates how to use it effectively in ZIO HTTP applications.


## Need of MiddleWare

Middleware acts as a layer between the client and server components of an application, enabling to add cross-cutting concerns such as logging, authentication, authorization, or request/response modification. Each middleware function has access to both the incoming request and the outgoing response, making it versatile for implementing various functionalities.

## Anatomy of Middleware

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

The `@@` operator is used to attach middleware to an `Http` app. It allows you to chain multiple middleware functions for a sequence of transformations. Here's an example:
```scala mdoc:silent 
val app = Http.collectZIO[Request] {
  // ... your app logic
}

val composedMiddlewares = Middleware.debug ++ Middleware.timeout(5.seconds)

val appWithMiddlewares = app @@ composedMiddlewares
```
In this example, `composedMiddlewares `combines `Middleware.debug` (for logging) and `Middleware.timeout` (for setting a timeout) using the `++` operator. The resulting middleware chain is then attached to the app using `@@`.

#### Benefits of Chaining:

Chaining middleware empowers you to build complex application behaviours by composing simpler middleware functions. This modular approach promotes code maintainability and reusability.

## Combining Middlewares

Middleware functions can be combined together using various combinators provided by ZIO HTTP using `++, <<<, >>> and <>`. Combining middleware allows developers to build complex behaviour by combining simpler middleware functions.

For example, if we have three middleware's f1, f2, f3

`f1 >>> f2 >>> f3` applies on an http, sequentially feeding an http to f1 first followed by f2 and f3.

```scala mdoc:silent 
f1 >>> f2 >>> f3   // applies f1 first, then f2 and f3 sequentially
f1 ++ f2 ++ f3    // applies f1, then f2 and f3 from left to right
```

#### Using `++` combinator

`++` is an alias for combine. It combines two middlewares without changing their input/output types (*AIn = AOut / BIn = BOut*)

For example, if we have three middlewares `f1, f2, f3`

`f1 ++ f2 ++ f3` applies on an http, from left to right with f1 first followed by others, like this

 ```
  f3(f2(f1(http)))
```
#### A simple usage of `++` combinator

Start with imports

```scala mdoc:silent 
import zio.http.Middleware.basicAuth
import zio.http._
import zio.http.Server
import zio.console.putStrLn
import zio.{App, ExitCode, URIO}
```

A user app with single endpoint that welcomes a user

```scala mdoc:silent 

val userApp: UHttpApp = Http.collect[Request] { case Method.GET -> !! / "user" / name / "greet" =>
  Response.text(s"Welcome to the ZIO party! ${name}")
}
```

A basicAuth middleware with hardcoded user pw and another patches response with environment value

```scala 

val basicAuthMW = basicAuth("admin", "admin")
lazy val patchEnv = Middleware.addHeader("X-Environment", "Dev")
// apply combined middlewares to the userApp
val appWithMiddleware = userApp @@ (basicAuthMW ++ patchEnv)
```

Start the server

```scala mdoc:silent 
val server = Server.start(8090, appWithMiddleware).exitCode
zio.Runtime.default.unsafeRunSync(server)
```

Fire a curl request with an incorrect user/password combination

```scala
curl -i --user admin:wrong http://localhost:8090/user/admin/greet

HTTP/1.1 401 Unauthorized
www-authenticate: Basic
X-Environment: Dev
content-length: 0
```

We notice in the response that first basicAuth middleware responded `HTTP/1.1 401 Unauthorized `and then patch middleware attached a `X-Environment: Dev` header.

#### Using `>>>`

`>>>` is an alias for andThen and similar to ++ with one BIG difference input/output types can be different (*AIn≠ AOut / BIn≠ BOut*) Whereas, in the case of `++` types remain the same (horizontal composition).

For example, if we have three middlewares f1, f2, f3

```f1 >>> f2 >>> f3``` applies on an http, sequentially feeding an http to `f1` first followed by `f2` and `f3.`

```
f1(http) => http1 f2(http1) => http2
```

#### Using `<>` combinator

`<>` is an alias for `orElse`. While using `<>`, if the output `Http` of the first middleware fails, the second middleware will be evaluated, ignoring the result from the first.

##### A simple example using `<>`

```scala mdoc:silent 
val middleware: Middleware[Any, Nothing, Request, Response, Request, Response] = Middleware.fail("error") <> Middleware.addHeader("X-Environment", "Dev")
```


#### other operators:

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
val composedMiddleware = authMiddleware ++ loggingMiddleware ++ timeoutMiddleware```
```
## Built-in Middlewares

ZIO HTTP provides a collection of built-in middleware functions such as authentication, logging, request validation and more. Some of the out-of-the-box middleware functions include:

#### Authentication
* **Middleware.basicAuth(username: String, password: String):** Provides basic authentication for HTTP endpoints. It checks if the incoming request includes a valid authorization header with the provided credentials.

#### Logging
* **Middleware.debug:** Logs debug information about incoming requests and outgoing responses.

#### Timeouts
* **Middleware.timeout(duration: Duration):** Sets a timeout for incoming requests, cIf a request takes longer than the specified duration to complete, it is canceled.

#### Request/Response Modification

* **Middleware.addHeader:** Adds custom headers to outgoing responses.
* **Middleware.cors:** Handles Cross-Origin Resource Sharing (CORS) for HTTP endpoints.
* **Middleware.csrf:** Provides protection against Cross-Site Request Forgery (CSRF) attacks.

## Creating Custom Middleware

ZIO HTTP provides the flexibility to create custom middleware functions using the `Middleware.patchZIO` function. This allows to tailor middleware behavior to your application's specific needs.

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

val middleware: Middleware[Any, Nothing, String, String, Request, Response] = 
  Middleware.codec[Request,String](r => Right(r.method.toString()), s => Right(Response.text(s)))
```

* **fromHttp:** Constructs middleware from a predefined Http object.
 
```scala mdoc:silent 

val app: Http[Any, Nothing, Any, String] = Http.succeed("Hello World!")
val middleware: Middleware[Any, Nothing, Nothing, Any, Request, Response] = Middleware.fromHttp(app)
```

## Transforming Middleware (Advanced Techniques)

ZIO HTTP offers powerful ways to transform existing middleware functions, enabling to create more complex processing pipelines. Here's a breakdown of key transformation techniques:

#### Transforming Output Type

* **map and mapZIO**: These functions allows to modify the output type of the `Http` object produced by a middleware function.

   - **map:** Takes a pure function that transforms the output value.
  - **mapZIO:** Takes an effectful function (a `ZIO` effect) that transforms the output value.

val middleware: Middleware[Any, Nothing, Nothing, Any, Any, Int] = Middleware.succeed(3)

```scala mdoc:silent 

val mid1: Middleware[Any, Nothing, Nothing, Any, Any, String] = middleware.map((i: Int) => i.toString)  // Pure transformation
val mid2: Middleware[Any, Nothing, Nothing, Any, Any, String] = middleware.mapZIO((i: Int) => ZIO.succeed(s"$i"))  // Effectful transformation
```

#### Transforming Output Type with Intercept

* **intercept and interceptZIO:** These functions create a new middleware by applying transformation functions to both the outgoing response (B) and an additional value (S) generated during the transformation.

```scala mdoc:silent 

val middleware: Middleware[Any, Nothing, String, String, String, Int] = 
  Middleware.intercept[String, String](_.toInt + 2)((_, a) => a + 3)

// Takes two functions: (incoming, outgoing)
// First function transforms String to Int and adds 2
// Second function takes the original String and transformed value, adds 3 to the transformed value
```
#### Transforming Input Type

* **contramap and contramapZIO:** These functions are used to modify the input type of the Http object a middleware function accepts.
  - **contramap:** Takes a pure function that transforms the input value.
  - **contramapZIO:** Takes an effectful function (a ZIO effect) that transforms the input value

val middleware: Middleware[Any, Nothing, Int, Int, Int, Int] = 
  Middleware.codec[Int, Int](decoder = a => Right(a + 1), encoder = b => Right(b + 1))

```scala mdoc:silent 

val mid1: Middleware[Any, Nothing, Int, Int, String, Int] = middleware.contramap[String](_.toInt)  // Pure transformation
val mid2: Middleware[Any, Nothing, Int, Int, String, Int] = middleware.contramapZIO[String](a => UIO(a.toInt)) // Effectful transformation
```

## Conditional Application for Middlewares

* **when and whenZIO:** These functions conditionally apply a middleware based on a predicate function. They only execute the middleware if the predicate evaluates to `true`.
  - **when:** Takes a pure predicate function.
  - **whenZIO:** Takes an effectful predicate function (a `ZIO` effect).

```scala mdoc:silent 

val middleware: Middleware[Any, Nothing, Nothing, Any, Any, String] = Middleware.succeed("yes")

val mid1: Middleware[Any, Nothing, Nothing, Any, String, String] = 
  middleware.when[String]((str: String) => str.length > 2)  // Pure predicate

val mid2: Middleware[Any, Nothing, Nothing, Any, String, String] = 
  middleware.whenZIO[Any, Nothing, String]((str: String) => UIO(str.length > 2)) // Effectful predicate
```
#### Logical Operators for Middleware Selection

* **ifThenElse and ifThenElseZIO:** These functions allow you to select a middleware based on a predicate. They work similarly to the if-else construct in programming languages.
  - **ifThenElse:** Takes pure functions for the `true` and `false` branches.
  - **ifThenElseZIO:** Takes effectful functions (ZIO effects) for the `true` and `false` branches

```scala mdoc:silent 

val mid1: Middleware[Any, Nothing, Nothing, Any, Int, Int] = 
  Middleware.ifThenElse[Int](_ > 5)(
    isTrue = i => Middleware.succeed(i + 1),
    isFalse = i => Middleware.succeed(i - 1)
  )  // Pure functions

val mid2
```

### References
- [HelloWorldWithMetrics](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/HelloWorldWithMetrics.scala)

- [StaticFiles](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/StaticFiles.scala)
- [HelloWorldWithCORS](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/HelloWorldWithCORS.scala)
- [CounterProtocolStackExample](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/middleware/CounterProtocolStackExample.scala)
- [HelloWorldWithMiddlewares](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/HelloWorldWithMiddlewares.scala)

