---
id: middleware
title: Middleware
---

A middleware helps in addressing common crosscutting concerns without duplicating boilerplate code.

## Definition

Middleware can be conceptualized as a functional component that accepts a `Routes` and produces a new `Routes`. The defined trait, `Middleware`, is parameterized by a contravariant type `UpperEnv` which denotes it can access the environment of the `Routes`:

```scala
trait Middleware[-UpperEnv] { self =>
  def apply[Env1 <: UpperEnv, Err](routes: Routes[Env1, Err]): Routes[Env1, Err]
} 
```

This abstraction allows middleware to engage with the `Routes` environment, and also the ability to tweak existing routes or add/remove routes as needed.

The diagram below illustrates how `Middleware` works:

<div style={{textAlign: 'center', margin: '10px'}}>

![Middleware Diagram](middleware.svg)

</div>

## Usage

The `@@` operator attaches middleware to routes and HTTP applications.  The example below shows a middleware attached to an `Routes`:

```scala mdoc:compile-only
import zio.http._

val app = Routes(
  Method.GET / string("name") -> handler { (name: String, req: Request) => 
    Response.text(s"Hello $name")
  }
)
val appWithMiddleware = app @@ Middleware.debug
```

Logically the code above translates to `Middleware.debug(app)`, which transforms the app using the middleware.

## Attaching Multiple Middlewares

We can attach multiple middlewares by chaining them using more `@@` operators:

```scala
val resultApp = routes @@ f1 @@ f2 @@ f3
```

In the above code, when a new request comes in, it will first go through the `f3`'s incoming handler, then `f2`, then `f1`, and finally the `routes`, when the response is going out, it will go through the `f1`'s outgoing handler, then `f2`, then `f3`, and finally will be sent out. So **the order of the middlewares matters** and if we change the order of the middlewares, the application's behavior may change.

## Composing Middlewares

Middleware can be combined using the `++` operator:

```scala
routes @@ (f1 ++ f2 ++ f3)
```

The `f1 ++ f2 ++ f3` applies from left to right with `f1` first followed by others, like this:

```scala
f3(f2(f1(routes)))
```

## Motivation

Before introducing middleware, let us understand why they are needed.

### The Problem: Violation of Separation of Concerns Principle

Consider the following example where we have two endpoints:

* **GET /users/\{id\}** - Get a single user by id
* **GET /users** - Get all users

```scala
val routes = Routes(
  Method.GET / "users" / int("id") -> 
    handler { (id: Int, req: Request) =>
      // core business logic  
      dbService.lookupUsersById(id).map(Response.json(_.json))
    },
  Method.GET / "users" ->
    handler {
      // core business logic  
      dbService.paginatedUsers(pageNum).map(Response.json(_.json))
    }
)
```

As our application grows, we want to code the aspects like Basic Authentication, Request Logging, Response Logging, Timeout, and Retry for all our endpoints.

For both of our example endpoints, our core business logic gets buried under boilerplate like this:

```scala
(for {
    // validate user
    _    <- MyAuthService.doAuth(request)
    // log request
    _    <- logRequest(request)
    // core business logic
    user <- dbService.lookupUsersById(id).map(Response.json(_.json))
    resp <- Response.json(user.toJson)
    // log response
    _    <- logResponse(resp)                
} yield resp)
        .timeout(2.seconds)
        .retryN(5)
```

Imagine repeating this for all our endpoints!

So there are some problems with this approach:

* **Violation of Separation of Concerns Principle**: Our current approach conflates business logic with cross-cutting concerns, such as timeouts, which violates the Separation of Concerns Principle. This coupling complicates the maintenance and understanding of our codebase.
* **Code Duplication**: Replicating cross-cutting concerns across multiple routes results in unnecessary code duplication. For instance, if there are 100 routes, each requiring a timeout, we're forced to repeat the same logic 100 times. Consequently, any modification or upgrade to a shared concern, like altering the logging mechanism, necessitates making changes in numerous locations, significantly increasing the risk of errors and maintenance effort.
* **Maintenance Nightmare**: With this approach, even a minor alteration in a cross-cutting concern demands updating every corresponding route. This not only escalates maintenance efforts but also complicates testing and debugging of core business logic. Consequently, the overall maintenance cost and complexity of the system are amplified.
* **Readability Issues**— This can lead to a lot of boilerplate clogging our neatly written endpoints affecting readability.

### The solution: Middleware and Aspect-oriented Programming

If we refer to Wikipedia for the definition of an "[Aspect](https://en.wikipedia.org/wiki/Aspect_(computer_programming))" we can glean the following points.

* An aspect of a program is a feature linked to many other parts of the program (**_most common example, logging_**).
* Tt is not related to the program's primary function (**_core business logic_**).
* An aspect crosscuts the program's core concerns (**_for example logging code intertwined with core business logic_**).
* Therefore, it can violate the principle of "separation of concerns" which tries to encapsulate unrelated functions. (**_Code duplication and maintenance nightmare_**)

In short, aspect is a common concern required throughout the application, and its implementation could lead to repeated boilerplate code and violation of the principle of separation of concerns.

There is a paradigm in the programming world called [aspect-oriented programming](https://en.wikipedia.org/wiki/Aspect-oriented_programming) that aims for modular handling of these common concerns in an application.

Some examples of common "aspects" required throughout the application

* **Logging**— Essential for tracking system behavior and troubleshooting
* **Timeouts**— Used for preventing long-running code.
* **Retries**— Vital for handling flakiness, particularly when accessing third-party APIs.
* **Authentication**— Ensure users are authenticated before accessing REST resources, utilizing standard methods like basic authentication or more advanced approaches such as OAuth or single sign-on.

This is where `Middleware` comes to the rescue. Using middlewares we can compose out-of-the-box middlewares (or our custom middlewares) to address the above-mentioned concerns using `++` and `@@` operators as shown below.

### The Solution: Middleware in ZIO-HTTP

We cleaned up the code using middleware to address cross-cutting concerns such as authentication, request/response logging, and more. See how we can handle multiple cross-cutting concerns by neatly composing middlewares in a single place:

```scala mdoc:silent
import zio._
import zio.http._

// compose basic auth, request/response logging, timeouts middlewares
val composedMiddlewares = Middleware.basicAuth("user","pw") ++ 
        Middleware.debug ++ 
        Middleware.timeout(5.seconds) 
```

And then we can attach our composed bundle of middlewares to an Http using `@@`

```scala
 val routes = Routes(
  Method.GET / "users" / int("id") -> 
    handler { (id: Int, req: Request) =>
      // core business logic  
      dbService.lookupUsersById(id).map(Response.json(_.json))
    },
  Method.GET / "users" ->
    handler {
      // core business logic  
      dbService.paginatedUsers(pageNum).map(Response.json(_.json))
    }
) @@ composedMiddlewares // attach composedMiddlewares to the routes using @@
```

Observe how we gained the following benefits by using middlewares

* **Readability**: de-cluttering business logic.
* **Modularity**: we can manage aspects independently without making changes in 100 places. For example, 
  * replacing the logging mechanism from logback to log4j2 will require a change in one place, the logging middleware.
  * replacing the authentication mechanism from OAuth to single sign-on will require changing the auth middleware
* **Testability**: we can test our aspects independently.

## Building a Custom Middleware

In most cases, we won't need a custom middleware. Instead, we have plenty of built-in middlewares that are ready to use. However, if we have a specific use case, we can create a custom middleware.

To build a custom middleware, we have to implement the `Middleware` trait, which requires a single method `apply` to be implemented. The `apply` method accepts a `Routes` and returns a new `Routes`.

For example, assume we want to replace every request path that starts with `/api` with `/v1/api`. We can create a custom middleware to achieve this:

```scala mdoc:compile-only
val urlRewrite: Middleware[Any] =
  new Middleware[Any] {
    override def apply[Env1 <: Any, Err](routes: Routes[Env1, Err]): Routes[Env1, Err] =
      routes.transform { handler =>
        Handler.scoped[Env1] {
          Handler.fromFunctionZIO { request =>
            handler(
              request.updateURL(
                url =>
                  if (url.path.startsWith(Path("/api")))
                    url.copy(path = Path("/v1") ++ url.path)
                  else url,
                ),
              )
          }
        }
      }
  }
```

The above implementation is just for demonstration purposes. In practice, we can use the built-in `Middleware.updatePath` to achieve the same functionality:

```scala mdoc:compile-only
val urlRewrite: Middleware[Any] =
  Middleware.updateURL(url =>
    if (url.path.startsWith(Path("/api")))
      url.copy(path = Path("/v1") ++ url.path)
    else url,
  )
```

## Built-in Middlewares

In this section we are going to introduce built-in middlewares that are provided by ZIO HTTP. Please note that the `Middleware` object also inherits many other middlewares from the `HandlerAspect`, that we will introduce them on the [HandlerAspect](handler_aspect.md) page.

### Access Control Allow Origin (CORS) Middleware

The CORS middleware is used to enable cross-origin resource sharing. It allows the server to specify who can access the resources on the server. The origin is a combination of the protocol, domain, and port of the client. By default, the server does not allow cross-origin requests. What this means is that if a client is hosted on a different domain (or different protocol and port), the server will reject the request. So, if the client is hosted on `http://localhost:3000` and the server is hosted on `http://localhost:8080`, the server will reject the request.

This is where the client may want to create a preflight request to the server to ask for permission to access the resources. This is done by sending a preflight `OPTIONS` request to the server with the headers `Origin`, `Access-Control-Request-Method`, and `Access-Control-Request-Headers`. If the server determines that the request is allowed, it includes an `Access-Control-Allow-Origin` header in the response with a value that specifies which origins are permitted to access the resource. The same thing happens with the `Access-Control-Allow-Methods` and `Access-Control-Allow-Headers` headers. Now the client can decide whether to send the actual request or not.

To create a CORS middleware, we can use the `Middleware.cors` constructor. It takes a configuration object of type `CorsConfig` that specifies the allowed origins, methods, headers, and so on. The `CorsConfig` object has the following fields:

1. **`allowedOrigin`**— A function that takes the origin of the client and returns allowed origins of type `Option[Header.AccessControlAllowOrigin]`. By default, the configuration object allows all origins (`*`).
2. **`allowedMethods`**— The `Access-Control-Allow-Methods` response header is used in response to a preflight request which includes the `Access-Control-Request-Method` to indicate which HTTP methods can be used during the actual request. By default, the configuration object allows all methods (`*`).
3. **`allowedHeaders`**— The `Access-Control-Allow-Headers` response header is used in response to a preflight request which includes the `Access-Control-Request-Headers` to indicate which HTTP headers can be used during the actual request. By default, the configuration object allows all headers (`*`).
4. **`allowCredentials`**— The `Access-Control-Allow-Credentials` header is sent in response to a preflight request which includes the `Access-Control-Request-Headers` to indicate whether the actual request can be made using credentials. By default, this configuration is set to `Allow`.
5. **`exposedHeaders`**— The `Access-Control-Expose-Headers` header is used in response to a preflight request to indicate which headers can be exposed as part of the response. By default, the configuration object exposes all headers (`*`).
6. **`maxAge`**— The `Access-Control-Max-Age` response header is used in response to a preflight request to indicate how long the results of a preflight request can be cached. By default, this configuration is set to `None`.

In the following example, we are going to serve two HTTP apps. The first app is a backend that serves a JSON response that contains a message. The second app is a frontend that serves an HTML page with a script that fetches the JSON response from the backend. The frontend is hosted on `http://localhost:3000` and the backend is hosted on `http://localhost:8080`. If we try to fetch the JSON response from the frontend, the server will reject the request because the client is hosted on a different origin.

To allow the frontend to access the backend, we need to create a CORS middleware that allows the origin `http://localhost:3001`. We can do this by creating a `CorsConfig` object with an `allowedOrigin` function that returns `Some(AccessControlAllowOrigin.Specific(origin))` if the origin is `http://localhost:3000`. We then attach the CORS middleware to the backend using the `@@` operator.

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/HelloWorldWithCORS.scala")
```

### Metrics Middleware

The `Middleware.metrics` middleware is used to collect metrics about the HTTP requests and responses that are processed by the server. The middleware collects the following metrics:

* **`http_requests_total`**— The total number of HTTP requests that have been processed by the server, using the counter metric type.
* **`http_request_duration_seconds`**— The duration of the HTTP requests in seconds, using the histogram metric type.
* **`http_concurrent_requests_total`**— The total number of concurrent HTTP requests that are being processed by the server, using the gauge metric type.

In the following example, we are going to serve two HTTP apps. One app is a backend that has some routes and the other app is a metrics app that serves the Prometheus metrics. We have attached the `Middleware.metrics` middleware to the backend using the `@@` operator.

In this example we used the Prometheus connector, so we need to add the following dependencies to the `build.sbt` file:

```scala
libraryDependencies ++= Seq(
  "dev.zio" %% "zio-metrics-connectors"            % "2.3.1",
  "dev.zio" %% "zio-metrics-connectors-prometheus" % "2.3.1"
)
```

To integrate with other metrics systems, please refer to the [ZIO Metrics Connectors](https://zio.dev/zio-metrics-connectors/) documentation.

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/HelloWorldWithMetrics.scala")
```

Another important thing to note is that the `metrics` middleware only attaches to the `Routes` or `Routes`, so if we want to track some custom metrics particular to a handler, we can use the `ZIO#@@` operator to attach a metric of type `ZIOAspect` to the ZIO effect that is returned by the handler. For example, if we want to track the number of requests that have a custom header `X-Custom-Header` in the `/json` route, we can attach a counter metric to the ZIO effect that is returned by the handler using the `@@` operator.

### Timeout Middleware

The `Middleware.timeout` middleware is used to set a timeout for the HTTP requests that are processed by the server. If the request takes longer than the specified duration, the server will respond with request timeout status code `408`. The middleware takes a `Duration` parameter that specifies the timeout duration.

```scala mdoc:invisible
import zio.http._
val routes: Routes[Any, Response] = Handler.ok.toRoutes
```

```scala mdoc:compile-only
routes @@ Middleware.timeout(5.seconds)
```

### Log Annotation Middleware

Using the `Middleware.logAnnotate*` middleware, we can add more annotations to the logging context. There are several variations of the `logAnnotate` middleware:

* **`logAnnotate(key: => String, value: => String)`**— Adds a single log annotation with the specified key and value.
* **`logAnnotate(logAnnotation: => LogAnnotation, logAnnotations: => LogAnnotation*)`**— Adds multiple log annotations with the specified log annotations.
* **`logAnnotate(logAnnotations: => Set[LogAnnotation])`**— Adds multiple log annotations with the specified set of log annotations.
* **`logAnnotate(fromRequest: Request => Set[LogAnnotation])`**— Adds log annotations derived from the request.
* **`logAnnotateHeaders(headerName: String, headerNames: String*)`**— Adds log annotations with the names and values of the specified headers.
* **`logAnnotateHeaders(header: Header.HeaderType, headers: Header.HeaderType*)`**— Adds log annotations with the names and values of the specified headers.

Let's write a middleware that adds a correlation ID to the logging context, which is derived from the `X-Correlation-ID` header of the request. If the header is not present, we generate a random UUID as the correlation ID:

```scala mdoc:silent
val correlationId =
  Middleware.logAnnotate{ req =>
    val correlationId =
      req.headers.get("X-Correlation-ID").getOrElse(
        Unsafe.unsafe { implicit unsafe =>
          Runtime.default.unsafe.run(Random.nextUUID.map(_.toString)).getOrThrow()
        }
      )

    Set(LogAnnotation("correlation-id", correlationId))
  }
```

To see the correlation ID in the logs, we need to place the middleware after the request logging middleware:

```scala mdoc:silent
routes @@ Middleware.requestLogging() @@ correlationId
```

Now, if we call one of the routes with the `X-Correlation-ID` header, we should see the correlation ID in the logs:

```shell
timestamp=2024-04-12T08:16:26.034894Z level=INFO thread=#zio-fiber-44 message="Http request served" location=example.HelloWorldWithLogging.backend file=HelloWorldWithLogging.scala line=20 method=GET correlation-id=34fab1bb-eeca-4b4f-975d-12f18e94f2e7 duration_ms=77 url=/json response_size=27 status_code=200 request_size=0
```

### Serving Static Files Middleware

With the `Middleware.serveDirectory` and `Middleware.serveResources` middlewares, we can serve static files from a directory or resource directory in the classpath:


```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/StaticFiles.scala")
```

### Ensure Header Middleware

The `Middleware.ensureHeader` middleware guarantees that a specific header is present in incoming requests. If the header already exists, the request passes through unchanged. If the header is missing, the middleware adds it with a provided default value.

This middleware comes in two variants:

1. **Type-safe variant** - Uses ZIO HTTP's `Header.HeaderType` for compile-time type safety:

   ```scala mdoc:compile-only
   Middleware.ensureHeader(Header.ContentType)(MediaType.application.json)
   ```

2. **String-based variant** - Uses header name and value as strings for flexibility:

   ```scala mdoc:compile-only
   Middleware.ensureHeader("X-Request-ID")("default-request-id")
   ```

A common use case for this middleware is to ensure that every incoming request contains a unique correlation ID, which is essential for distributed tracing in microservices architectures:

```scala mdoc:compile-only
import java.util.UUID

val routes = Routes(
  Method.GET / "api" / trailing -> handler {
    Response.text("API response")
  }
) @@ Middleware.ensureHeader("X-Request-ID")(UUID.randomUUID().toString)
```

### Forwarding Headers Middleware

The `Middleware.forwardHeaders` middleware is used to forward headers from the incoming request to the outgoing request when using the ZIO HTTP Client. This is useful when we want to forward headers like `Authorization`, `X-Request-ID`, and so on. It takes a set of header names to be forwarded:

```scala
routes @@ Middleware.forwardHeaders(Set("X-Request-ID", "Authorization"))
```

This middleware extracts the specified headers from the incoming request and stores them in the `RequestStore`. When we make an outgoing request using the ZIO HTTP Client, the `ZClientAspect.forwardHeaders` retrieves the headers from the [`RequestStore`](../contextual/request-store.md) and adds them to the outgoing request.

To see the full flow, please refer to the [`ZClientAspect.forwardHeaders` documentation](../client.md#forwarding-headers).
