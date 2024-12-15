## ZIO HTTP Documentation

## Table of Contents

1.  [Introduction](#1-introduction)
    - [Overview](#overview)
    - [Rationale](#rationale)
2.  [Getting Started](#2-getting-started)
    - [Installation](#installation)
    - [Basic Examples](#basic-examples)
    - [Core Concepts](#core-concepts)
3.  [Server](#3-server)
    - [Configuration](#configuration)
    - [Routes](#routes)
    - [Handlers](#handlers)
    - [Error Handling](#error-handling)
4.  [Client](#4-client)
    - [Client Configuration](#client-configuration)
    - [Request Types](#request-types)
    - [Response Handling](#response-handling)
    - [WebSocket](#websocket)
5.  [Advanced Features](#5-advanced-features)
    - [Middleware](#middleware)
    - [Streaming](#streaming)
    - [Metrics Integration](#metrics-integration)
    - [Template System](#template-system)
    - [Compression](#compression)
    - [Rate Limiting](#rate-limiting)
    - [OpenAPI](#openapi)
6.  [Integration & Ecosystem](#6-integration--ecosystem)
7.  [Testing & Observability](#7-testing--observability)
8.  [Deployment & Production](#8-deployment--production)
9.  [Best Practices](#9-best-practices)
10. [Real-World Examples](#10-real-world-examples)
11. [Migration & Reference](#11-migration--reference)
12. [FAQ](#12-frequently-asked-questions-faq)

---

## 1. Introduction

### Overview

ZIO HTTP is a purely functional HTTP library built on ZIO and Netty, designed around the concept of "HTTP as a function". In this model, both server and client applications are pure functions that transform requests into responses, emphasizing type safety, composability, and testability.

Key Features:

- **Pure Functional**: Built on ZIO for type-safe, composable, and asynchronous operations
- **High Performance**: Leverages Netty for efficient non-blocking I/O
- **Type Safety**: Strong type system ensures compile-time correctness
- **Developer Friendly**: Intuitive DSL for routing and request handling
- **Production Ready**: Built-in support for metrics, logging, and monitoring

### Rationale

Why ZIO HTTP exists and its design principles:

1.  **Functional Purity**
    - HTTP apps as pure functions from Request to Response
    - Predictable behavior through referential transparency
    - Easy to test and reason about
2.  **Type-Driven Design**
    - Leverage Scala's type system for safety
    - Catch errors at compile time
    - Clear and explicit error handling
3.  **Resource Safety**
    - Automatic resource management via ZIO
    - Proper handling of connections and cleanup
    - Built-in back pressure support
4.  **Cloud Native**
    - First-class support for modern deployment patterns
    - Built-in monitoring and observability
    - Designed for containerization and scaling
5.  **Performance Focus**
    - Non-blocking I/O with Netty
    - Efficient memory usage
    - Optimized for high throughput

This foundation provides a robust platform for building modern web applications while maintaining simplicity and type safety.

## 2. Getting Started

### Installation

To get started with ZIO HTTP, add the following dependency to your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-http" % "3.0.1"
```

### Basic Examples

Create a simple server that responds with "Hello, World!" to a GET request at the `/hello` endpoint:

```scala
import zio._
import zio.http._

object BasicServer extends ZIOAppDefault {
  val app = Routes(
   Method.GET / "hello" -> handler(Response.text("Hello, World!"))
  )

  override def run = Server.serve(app).provide(Server.default)
}
```

### Core Concepts

#### Effectful HTTP Model

ZIO HTTP treats an HTTP application (`HttpApp`) as a pure function from a `Request` to a ZIO effect producing a `Response`. This encourages a declarative, composable approach.

#### Routes & Handlers

- **Routes**: Define how requests are routed to handlers (pattern matching on method, path, headers).
- **Handlers**: Transform requests into responses, performing I/O through ZIO effects.

#### Typed Errors & Streaming

- **Typed Errors**: Make failure modes explicit.
- **ZIO Streams**: Integrate seamlessly for handling large payloads, continuous data, and backpressure.

## 3. Server

### Configuration

Configure the server using `ServerConfig`, including settings for ports, compression, and SSL/TLS:

```scala
import zio.http.Server.Config

val serverConfig = Config.default
  .port(8080)
  .compressionLevel(6) // Adjust compression level as needed
  .enableCompression(true)
```

### Routes

Define the routes for your server:

```scala
val appRoutes = Routes(
  Method.GET / "hello" -> handler(Response.text("Hello, World!")),
  Method.GET / "health" -> handler(Response.text("OK"))
)
```

### Handlers

Create handlers to process incoming requests and generate responses:

```scala
val helloHandler = handler { _ =>
  ZIO.succeed(Response.text("Hello, World!"))
}
```

### Error Handling

Implement robust error handling using typed errors and meaningful HTTP responses:

```scala
sealed trait AppError extends Throwable
case object NotFoundError extends AppError
case object UnauthorizedError extends AppError

val errorHandler: Throwable => Response = {
  case NotFoundError => Response.status(Status.NotFound).withText("Resource not found")
  case UnauthorizedError => Response.status(Status.Unauthorized).withText("Unauthorized access")
  case _ => Response.status(Status.InternalServerError).withText("Internal server error")
}

val resilientApp = appRoutes.catchAll(errorHandler)
```

## 4. Client

### Client Configuration

```scala
import zio.duration._

val clientConfig = Client.Config.default
  .withMaxConnectionsPerHost(50)
  .withConnectionPool(true)
  .withRequestTimeout(5.seconds)
  .withRetryPolicy(Retry.fixedDelay(3, 2.seconds)) // Add retry policy

val clientLayer = Client.defaultWith(clientConfig)
```

### Request Types

Create and send various types of HTTP requests:

```scala
import zio._
import zio.http._

object ClientExample extends ZIOAppDefault {
  override def run = {
    val request = Request.get(URL(!! / "api" / "data").setHost("example.com"))
    Client.request(request)
      .flatMap(response => response.body.asString.map(println))
      .provide(Client.default)
  }
}
```

### Response Handling

Process responses received from the server:

```scala
Client.request(request)
  .flatMap { response =>
    response.body.asString.map { body =>
      println(s"Response Body: $body")
    }
  }
  .provide(Client.default)
```

### WebSocket

ZIO HTTP provides comprehensive WebSocket support for real-time bidirectional communication:

#### Basic WebSocket Handler

```scala
import zio.http.WebSocketFrame.{Close, Text}
import zio.http.ChannelEvent.{Read, UserEventTriggered, ExceptionCaught}
import zio.http.UserEvent.HandshakeComplete
import zio.http.WebSocketApp

val socketApp = Handler.webSocket { channel =>
  channel.receiveAll {
    case Read(Text(text)) =>
      channel.send(Read(Text(s"Echo: $text")))
    case UserEventTriggered(HandshakeComplete) =>
      channel.send(Read(Text("Connected!")))
    case _ => ZIO.unit
  }
}

val routes = Routes(
  Method.GET / "ws" -> handler(socketApp.toResponse)
)
```

**Error Handling and Recovery**

```scala
import zio.Promise
import zio.Cause
import zio.http.WebSocketApp
import zio.http.ChannelEvent.{Read, ExceptionCaught}
import zio.http.WebSocketFrame.{Close, Text}

def makeSocketApp(p: Promise[Nothing, Throwable]): WebSocketApp[Any] =
  Handler.webSocket { channel =>
    channel.receiveAll {
      case ExceptionCaught(cause) =>
        ZIO.logErrorCause("WebSocket error", Cause.fail(cause)) *>
        p.succeed(cause)

      case Read(Close(status, reason)) =>
        ZIO.logInfo(s"Connection closed: $status - $reason")

      case _ => ZIO.unit
    }
  }.tapErrorZIO(error =>
    ZIO.logError(s"WebSocket failed: ${error.getMessage}")
  )
```

**Automatic Reconnection**

```scala
import zio.Promise
import zio.duration._
import zio.http.URL
import zio.http.ChannelEvent.{Read, ExceptionCaught}
import zio.http.WebSocketFrame.{Close, Text}

val reconnectingClient = for {
  p <- Promise.make[Nothing, Throwable]
  _ <- makeSocketApp(p).connect(URL(!! / "ws").setHost("localhost").setPort(8080)).catchAll { error =>
    p.succeed(error)
  }
  failure <- p.await
  _ <- ZIO.logError(s"Connection failed: $failure")
  _ <- ZIO.logInfo("Attempting reconnection...")
  _ <- ZIO.sleep(1.second)
} yield ()

val resilientApp = reconnectingClient.forever
```

**Streaming WebSocket**

```scala
import zio.stream._
import zio.duration._
import zio.http.ChannelEvent.{Read, UserEventTriggered}
import zio.http.UserEvent.HandshakeComplete
import zio.http.WebSocketFrame.Text

val streamingSocket = Handler.webSocket { channel =>
  val messageStream = ZStream
    .range(1, 100)
    .schedule(Schedule.spaced(100.milliseconds))
    .map(n => Text(s"Message $n"))

  channel.receiveAll {
    case UserEventTriggered(HandshakeComplete) =>
      messageStream.foreach(msg => channel.send(Read(msg)))
    case _ => ZIO.unit
  }
}
```

**Bidirectional Communication**

```scala
import zio.http.ChannelEvent.Read
import zio.http.WebSocketFrame.Text

def processingSocket[R](processor: String => ZIO[R, Nothing, String]) =
  Handler.webSocket { channel =>
    channel.receiveAll {
      case Read(Text(message)) =>
        for {
          result <- processor(message)
          _ <- channel.send(Read(Text(result)))
        } yield ()
      case _ => ZIO.unit
    }
  }
```

## 5. Advanced Features

### Middleware

Middleware can be stacked to handle cross-cutting concerns such as logging, authentication, rate limiting, CORS, and compression without cluttering handlers.

#### Example Middleware Composition

Update the middleware composition syntax to use the latest operators and patterns:

```scala
import zio._
import zio.http._
import zio.duration._

val appRoutes = Routes(
  Method.GET / "data" -> handler(Response.text("Some data"))
)

val enhancedApp = appRoutes
  .middleware(Middleware.debug)
  .middleware(Middleware.timeout(5.seconds))
  .middleware(Middleware.cors(CorsConfig.default))
  .middleware(Middleware.compress)

// Add custom rate limiting middleware as needed:
val rateLimitingMiddleware = Middleware.custom { http =>
  for {
    counter <- Ref.make(100).toManaged_
    finalHttp <- (for {
      tokens <- counter.get
      response <- if (tokens > 0) {
                    counter.update(_ - 1) *> ZIO.succeed(http)
                  } else {
                    ZIO.succeed(Http.succeed(Response.status(Status.TooManyRequests)))
                  }
    } yield response).orElse(Http.succeed(Response.status(Status.InternalServerError)))
  } yield finalHttp
}

val finalApp = enhancedApp.middleware(rateLimitingMiddleware)
```

#### Common Middleware Patterns

- **CORS**: Allows cross-origin requests from trusted domains using updated `CorsConfig`.
- **Timeouts**: Protects against slow endpoints with the latest timeout settings.
- **Compression**: Improves network efficiency for textual responses using updated compression middleware.
- **Rate Limiting**: Prevents abuse and ensures fair resource usage with enhanced rate limiting examples.

### Streaming

Handle large payloads and continuous data streams efficiently:

```scala
import zio.stream._

val streamingApp = Routes(
  Method.GET / "stream" -> handler { _ =>
    Response.stream(ZStream.range(1, 100).map(_.toString))
  }
)
```

### Metrics Integration

ZIO HTTP provides built-in metrics support using Prometheus:

```scala
import zio.metrics._
import zio.metrics.prometheus._

val metricsConfig = Server.Config.default ++
  ZLayer.succeed(MetricsConfig.default) ++
  prometheus.prometheusLayer

val metricsApp = Routes(
  Method.GET / "metrics" -> handler { _ =>
    ZIO.serviceWithZIO[PrometheusPublisher](_.get.map(Response.text))
  }
).middleware(Middleware.metrics())
```

### Template System

ZIO HTTP provides a typesafe template DSL for HTML generation:

```scala
import zio.http.template._

// Basic template
val simpleTemplate = Routes(
  Method.GET / "template" -> handler { _ =>
    val content = html(
      head(
        title("ZIO HTTP Template"),
        meta(charset := "UTF-8"),
        link(rel := "stylesheet", href := "/styles.css")
      ),
      body(
        div(cls := "container")(
          h1("Welcome"),
          p("This is a type-safe template"),
          // Form example
          form(method := "POST", action := "/submit")(
            input(typ := "text", name := "username"),
            button(typ := "submit")("Submit")
          )
        )
      )
    )
    Response.html(content)
  }
)

// Reusable components
val header = div(cls := "header")(
  nav(cls := "nav")(
    a(href := "/")(img(src := "/logo.png")),
    ul(
      li(a(href := "/home")("Home")),
      li(a(href := "/about")("About"))
    )
  )
)

// Dynamic content
def pageTemplate(title: String, content: Element) = html(
  head(
    title(title),
    meta(charset := "UTF-8")
  ),
  body(
    header,
    main(cls := "content")(content),
    footer(cls := "footer")("© 2024")
  )
)

// Usage with dynamic content
val dynamicPage = Routes(
  Method.GET / "page" / string("title") -> handler { (title: String) =>
    val page = pageTemplate(
      title,
      div(
        h1(title),
        p("Dynamic content here")
      )
    )
    Response.html(page)
  }
)
```

### Compression

ZIO HTTP supports automatic response compression:

```scala
import zio.http.Server.Config
import zio.http.Server.Config.CompressionOptions

// Basic compression setup
val compressedServer = Config.default.copy(
  responseCompression = Some(
    Config.ResponseCompressionConfig(
      contentThreshold = 1024,  // Compress responses larger than 1KB
      options = IndexedSeq(
        CompressionOptions.gzip(),
        CompressionOptions.deflate()
      )
    )
  )
)

// Advanced compression configuration
val customCompression = Config.default.copy(
  responseCompression = Some(
    Config.ResponseCompressionConfig(
      contentThreshold = 512,
      options = IndexedSeq(
        CompressionOptions.gzip(
          level = 6,         // Compression level (1-9)
          bits = 15,         // Window size
          mem = 8           // Memory level
        ),
        CompressionOptions.deflate(
          level = 6,
          bits = 15,
          mem = 8
        )
      )
    )
  )
)

// Usage with server
val app = Routes(
  Method.GET / "data" -> handler { _ =>
    Response.text("Large response that will be compressed")
  }
)

val program = for {
  _ <- Server.serve(app).provide(
    Server.layer(customCompression),
    Scope.default
  )
} yield ()
```

### Rate Limiting

```scala
import zio.duration._
import zio.http.Middleware
import zio.http.Response
import zio.http.Status
import zio.http.Routes

// Basic rate limiting
val basicRateLimit = Routes(
  Method.GET / "api" -> handler(Response.text("OK"))
).middleware(
  Middleware.rateLimit(
    maxRequests = 100,
    window = 1.minute
  )
)

// Advanced rate limiting configuration
def customRateLimiting(config: RateLimitConfig) = Middleware.custom { http =>
  for {
    counter <- ZIO.atomicInt(config.maxRequests).toManaged
    window <- ZIO.atomicLong(System.currentTimeMillis()).toManaged

    finalHttp <- {
      def checkLimit = for {
        now <- ZIO.succeed(System.currentTimeMillis())
        currentWindow <- window.get
        _ <- ZIO.when(now - currentWindow > config.windowDuration.toMillis) {
          window.set(now) *> counter.set(config.maxRequests)
        }
        remaining <- counter.decrementAndGet
      } yield remaining >= 0

      http.contramapZIO { request =>
        checkLimit.flatMap {
          case true => ZIO.succeed(request)
          case false => ZIO.fail(
            Response.status(Status.TooManyRequests)
              .addHeader(Header.Custom("X-RateLimit-Reset", config.windowDuration.toSeconds.toString))
          )
        }
      }
    }
  } yield finalHttp
}

case class RateLimitConfig(
  maxRequests: Int,
  windowDuration: Duration,
  errorResponse: Response = Response.status(Status.TooManyRequests)
)

// Usage with different configurations
val rateLimitedApp = Routes(
  Method.GET / "public" -> handler(Response.text("OK"))
    .middleware(Middleware.rateLimit(100, 1.minute)),

  Method.GET / "api" -> handler(Response.text("OK"))
    .middleware(Middleware.rateLimit(10, 1.second))
)
```

### OpenAPI

Generate OpenAPI specifications from routes and schemas to maintain up-to-date documentation:

```scala
import zio.http.openapi._
import zio.json._

val openApiSpec = OpenApi.fromRoutes(appRoutes)
val specJson = openApiSpec.toJson

// Serve the OpenAPI spec
val specApp = Routes(
  Method.GET / "openapi.json" -> handler { _ =>
    Response.json(specJson)
  }
)
```

## 6. Integration & Ecosystem

### ZIO Config Integration

Load configurations seamlessly from environment variables, files, or secrets managers using the latest ZIO Config APIs.

```scala
import zio.config._
import zio.config.magnolia._
import zio.config.typesafe._

case class AppConfig(serverPort: Int, dbUrl: String)

object AppConfig {
implicit val configDescriptor: Descriptor[AppConfig] = DeriveDescriptor.descriptor[AppConfig]
}

val configLayer = ZLayer.fromZIO(
read(AppConfig.configDescriptor from TypesafeConfigSource.fromResourcePath())
)

val serverLayer = Server.default.provideLayer(configLayer)
```

### ZIO Metrics Integration

Expose and track metrics using ZIO Metrics with updated middleware integration.

```scala
import zio.metrics._

val metricsApp = appRoutes.middleware(Middleware.metrics())

// Expose metrics endpoint
val metricsEndpoint = Routes(
Method.GET / "metrics" -> handler { _ =>
Response.text(Metrics.snapshot().toString)
}
)
```

### ZIO Schema Integration

Define and utilize domain models with ZIO Schema for type-safe serialization and automatic JSON codec derivation.

```scala
import zio.schema._
import zio.json._

case class User(id: Int, name: String)

object User {
implicit val schema: Schema[User] = DeriveSchema.gen[User]
implicit val codec: JsonCodec[User] = DeriveJsonCodec.gen[User]
}

val userApp = Routes(
Method.GET / "user" -> handler { _ =>
val user = User(1, "John Doe")
Response.json(user.toJson)
}
)
```

### OpenAPI Integration

Automatically generate and serve OpenAPI specifications to keep documentation synchronized with the codebase.

```scala
import zio.http.openapi._
import zio.json._

val openApiSpec = OpenApi.fromRoutes(appRoutes)
val specApp = Routes(
Method.GET / "openapi.json" -> handler { _ =>
Response.json(openApiSpec.toJson)
}
)
```

#### Additional Integrations

- **ZIO Logging**: Integrate enhanced logging capabilities.
- **ZIO Telemetry**: Implement distributed tracing with OpenTelemetry.
- **Database Integration**: Connect with databases using ZIO JDBC or ZIO ORM libraries.

## 7. Testing & Observability

### Testing with ZIO Test

Let's write clean and maintainable tests using ZIO Test. Here's a basic example to get you started:

```scala
import zio.test._
import zio.test.Assertion._
import zio._
import zio.http._

object ExampleSpec extends ZIOSpecDefault {
// A simple route we want to test
val testApp = Routes(
Method.GET / "test" -> handler(Response.text("ok"))
)

def spec = suite("MyApp")(
test("should return ok for GET /test") {
for {
response <- Client.request(Request.get(URL(!! / "test")))
body <- response.body.asString
} yield assert(body)(equalTo("ok"))
}
).provide(Server.test(testApp), Client.default)
}
```

When writing tests, aim to:

- Test one thing at a time
- Give clear, descriptive names to your test cases
- Keep setup code minimal and focused
- Handle edge cases and errors explicitly

### Real-World Testing

Your test suite should cover:

1.  Unit tests for individual handlers
2.  Integration tests between components
3.  Load tests for performance-critical paths
4.  Edge cases and error conditions

For load testing, tools like Gatling or k6 work well. Here's a quick example of a Gatling scenario:

```scala
import io.gatling.core.Predef._
import io.gatling.http.Predef._
import scala.concurrent.duration._

class BasicSimulation extends Simulation {
  val httpProtocol = http
    .baseUrl("http://localhost:8080")

  val basicScenario = scenario("Basic Load Test")
    .exec(http("Test Endpoint")
      .get("/test")
      .check(status.is(200)))
    .pause(1)

  setUp(basicScenario.inject(atOnceUsers(100))).protocols(httpProtocol)
}
```

### Observability

Good observability helps catch issues before they become problems. Focus on:

1.  Structured logging - Know what's happening in your app
2.  Request tracing - Track requests as they flow through your system
3.  Key metrics - Monitor what matters for your use case

Let's set up basic logging:

```scala
import zio.logging._

val loggingMiddleware = Middleware.logging()
val loggedApp = appRoutes.middleware(loggingMiddleware)
```

- **Tracing**: Implement distributed tracing with OpenTelemetry.

  ```scala
  import zio.telemetry.opentelemetry._

  val tracer = OpenTelemetry.layer()

  val tracedApp = appRoutes.middleware(Middleware.tracing())
  ```

- **Metrics**: Track performance metrics using ZIO Metrics.

  ```scala
  val metricsMiddleware = Middleware.metrics()
  val metricsApp = appRoutes.middleware(metricsMiddleware)
  ```

- **Dashboards**: Integrate with monitoring tools like Grafana or Prometheus to visualize metrics and logs.

## 8. Deployment & Production

### Containerization & Kubernetes

Update Dockerfile to use multi-stage builds and align with best practices for Kubernetes deployments.

```dockerfile
# Builder Stage
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app
COPY . .
RUN sbt assembly

# Runtime Stage
FROM eclipse-temurin:17-jdk
WORKDIR /app
COPY --from=builder /app/target/scala-2.13/my-zio-http-app.jar .
EXPOSE 8080
CMD ["java", "-jar", "my-zio-http-app.jar"]
```

Deploy to Kubernetes with updated Helm charts or Kubernetes manifests:

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: zio-http-app
spec:
  replicas: 3
  selector:
    matchLabels:
      app: zio-http-app
  template:
    metadata:
      labels:
        app: zio-http-app
    spec:
      containers:
        - name: app
          image: my-zio-http-app:latest
          ports:
            - containerPort: 8080
          env:
            - name: SERVER_PORT
              value: "8080"
```

### Performance Optimization

Implement the latest performance optimization strategies:

- **Netty Event Loops**: Tune Netty’s event loops for optimal concurrency handling.
- **Connection Pools**: Configure connection pools to handle high traffic efficiently.
- **Compression & Caching**: Enable response compression and implement caching mechanisms for static content.
- **Horizontal Scaling**: Scale applications horizontally using Kubernetes or service meshes to handle increased load.
- **Monitoring**: Continuously monitor application performance and adjust configurations as needed.

### Environment Management

Use ZIO Config to manage different environments (development, staging, production) with environment-specific settings.

```scala
import zio.config._
import zio.config.magnolia._
import zio.config.typesafe._

case class AppConfig(serverPort: Int, dbUrl: String)

object AppConfig {
  implicit val configDescriptor: Descriptor[AppConfig] = DeriveDescriptor.descriptor[AppConfig]
}

val devConfig = AppConfig(serverPort = 8080, dbUrl = "jdbc:postgresql://localhost/devdb")
val prodConfig = AppConfig(serverPort = 8443, dbUrl = "jdbc:postgresql://prod-db-server/proddb")

val isProd = sys.env.get("ENV").contains("prod")

val configLayer = ZLayer.fromZIO(
  if (isProd) ZIO.succeed(prodConfig) else ZIO.succeed(devConfig)
)
```

### CI/CD Integration

Integrate with CI/CD pipelines to automate testing, building, and deployment processes.

- **Continuous Integration**: Run automated tests and static code analysis on each commit.
- **Continuous Deployment**: Automatically deploy successful builds to staging or production environments.
- **Rollback Mechanisms**: Implement strategies to revert to previous versions in case of deployment failures.

## 9. Best Practices

### Error Handling Patterns

- **Typed Errors**: Utilize ZIO's typed error system to manage and handle errors predictably.

  ```scala
  sealed trait AppError extends Throwable
  case object NotFoundError extends AppError
  case object UnauthorizedError extends AppError

  val app = Routes(
    Method.GET / "secure" -> handler { _ =>
      ZIO.fail(UnauthorizedError)
    }
  ).catchAll {
    case NotFoundError => Response.status(Status.NotFound).withText("Not Found")
    case UnauthorizedError => Response.status(Status.Unauthorized).withText("Unauthorized")
    case _ => Response.status(Status.InternalServerError).withText("Internal Server Error")
  }
  ```

- **Transform Errors**: Convert internal errors to meaningful HTTP responses to provide clarity to clients.

### Resource Management

- **ZIO Scope & ZLayer**: Use ZIO Scope and ZLayer for managing resources safely and efficiently.

  ```scala
  import zio._

  def acquireDb = ZIO.succeed(println("Acquiring DB connection"))
  def releaseDb(db: Unit) = ZIO.succeed(println("Releasing DB connection"))

  val dbLayer = ZLayer.fromAcquireRelease(acquireDb)(releaseDb)

  val appLayer = Server.default ++ dbLayer
  ```

- **Automatic Cleanup**: Ensure resources like database connections and file handles are automatically released.

### Security Guidelines

- **SSL/TLS**: Always enforce SSL/TLS in production environments to secure data in transit.

  ```scala
  import zio.http.Server.Config
  import zio.http.Server.Config.SSLConfig

  val sslConfig = SSLConfig.auto(
    certPath = "path/to/cert.pem",
    keyPath = "path/to/key.pem"
  )

  val serverConfig = Config.default
    .ssl(sslConfig)
    .enableCompression(true)
  ```

- **Input Validation**: Validate all incoming requests to prevent injection attacks and ensure data integrity.

  ```scala
  import zio.http.Status

  def isValid(input: String): Boolean = input.nonEmpty // Example validation

  val validatedApp = Routes(
    Method.POST / "submit" -> handler { req =>
      req.body.asString.flatMap { input =>
        if (isValid(input)) ZIO.succeed(Response.text("Valid input"))
        else ZIO.succeed(Response.status(Status.BadRequest).withText("Invalid input"))
      }
    }
  )
  ```

- **Rate Limiting**: Implement rate limiting to protect against abuse and ensure fair resource usage.

  ```scala
  import zio.duration._

  val rateLimitConfig = RateLimitConfig(maxRequests = 100, windowDuration = 1.minute)
  val rateLimitedApp = appRoutes.middleware(Middleware.rateLimit(rateLimitConfig.maxRequests, rateLimitConfig.windowDuration))
  ```

- **CORS Configuration**: Enable CORS only for trusted domains to prevent unauthorized cross-origin requests.

  ```scala
  import zio.http.CorsConfig

  val corsApp = appRoutes.middleware(Middleware.cors(CorsConfig(allowedOrigins = Set("https://trusteddomain.com"))))
  ```

### Performance Optimization

- **Benchmarking**: Regularly benchmark application performance to identify and address bottlenecks.

  ```scala
  import zio.metrics._

  // Example using ZIO Metrics for benchmarking
  val metricsApp = appRoutes.middleware(Middleware.metrics())
  ```

- **Profiling**: Use profiling tools to analyze and optimize CPU and memory usage.

- **Streaming**: Leverage ZIO Streams for handling large data sets efficiently with backpressure support.

  ```scala
  import zio.stream._

  val streamApp = Routes(
    Method.GET / "large-data" -> handler { _ =>
      Response.stream(ZStream.fromIterable(1 to 1000000).map(_.toString))
    }
  )
  ```

- **Backpressure**: Implement backpressure mechanisms to manage load and prevent resource exhaustion.

  ```scala
  import zio.http.Middleware
  import zio.http.Middleware.BackpressureConfig

  val backpressureConfig = BackpressureConfig(
    maxPendingRequests = 100,
    maxPendingBytes = 10 * 1024 * 1024 // 10MB
  )
  val backpressureApp = appRoutes.middleware(Middleware.backpressure(backpressureConfig))
  ```

### Code Quality

Keep code organized with these key practices:

1.  Break down large components into smaller, focused modules that each have a single responsibility
2.  Include clear commenting and ScalaDoc for public APIs
3.  Follow consistent naming conventions and code formatting
4.  Write unit tests covering core functionality

### Monitoring & Logging

Production monitoring essentials:

1.  Forward logs to an aggregation service (ELK, Datadog, etc.)
2.  Track key metrics:
    - Request latency and error rates
    - Memory usage and GC stats
    - Connection pool utilization
    - Custom business metrics
3.  Configure alerts for:
    - Service availability drops
    - Error rate spikes
    - Resource exhaustion
    - SLA violations

### 10. Real-World Examples

#### 1. REST API with CRUD Operations

```
case class User(id: Int, name: String, email: String)
object User {
  implicit val schema: Schema[User] = DeriveSchema.gen[User]
  implicit val codec: JsonCodec[User] = DeriveJsonCodec.gen[User]
}

val userRoutes = Routes(
  Method.GET / "users" -> handler { _ =>
    // List all users
    ZIO.succeed(Response.json(users.toJson))
  },
  Method.POST / "users" -> handler { request =>
    // Create new user
    for {
      user <- request.body.asString.flatMap(str =>
        ZIO.fromEither(str.fromJson[User]))
      _ <- addUser(user)
    } yield Response.json(user.toJson)
  },
  Method.GET / "users" / int("id") -> handler { (id: Int, _: Request) =>
    // Get user by id
    getUser(id).map {
      case Some(user) => Response.json(user.toJson)
      case None => Response.status(Status.NotFound)
    }
  }
).middleware(
  Middleware.cors(CorsConfig.default) ++
  Middleware.timeout(5.seconds) ++
  Middleware.requestLogging
)
```

#### 2. Authentication Flow

```
val authMiddleware = new Middleware[Any] {
  override def apply[R1, E1](http: Routes[R1, E1]): Routes[R1, E1] = {
    http.transform { handler =>
      Handler.fromFunctionZIO { request =>
        request.headers.get("Authorization") match {
          case Some(auth) if isValidToken(auth) =>
            handler(request)
          case _ =>
            ZIO.succeed(Response.status(Status.Unauthorized))
        }
      }
    }
  }
}

val secureRoutes = Routes(
  Method.GET / "secure" / "data" -> handler { _ =>
    Response.text("Secured data")
  }
).middleware(authMiddleware)
```

#### 3. File Upload with Progress (Continued)

```scala
import zio._
import zio.http._
import zio.stream._

def saveFile(file: Body.Multipart.File): ZIO[Any, Throwable, Long] = {
  // Placeholder for file saving logic with progress tracking
  // In a real application, you would write the file to disk or cloud storage
  // and update the progress accordingly.
  // This example simulates progress by counting bytes.
  val totalBytes = file.size
  var bytesRead = 0L
  file.body.chunks.mapZIO { chunk =>
    bytesRead += chunk.size
    val progress = (bytesRead.toDouble / totalBytes.toDouble * 100).toInt
    ZIO.logInfo(s"Upload progress: $progress%") *> ZIO.succeed(chunk)
  }.runDrain *> ZIO.succeed(bytesRead)
}

val uploadRoutes = Routes(
  Method.POST / "upload" -> handler { request =>
    for {
      multipart <- request.body.asMultipart
      file <- multipart.file("file")
      bytesSaved <- saveFile(file)
    } yield Response.text(s"File uploaded successfully, $bytesSaved bytes saved")
  }
).middleware(Middleware.requestLogging)
```

#### 4. WebSocket Chat Application

```scala
import zio._
import zio.http._
import zio.http.ChannelEvent._
import zio.http.WebSocketFrame._
import zio.http.UserEvent.HandshakeComplete
import zio.stream._
import zio.Ref

object ChatServer {
  type ChatState = Ref[List[zio.http.Channel[Text]]]

  def makeChatApp(state: ChatState): WebSocketApp[Any] =
    Handler.webSocket { channel =>
      channel.receiveAll {
        case UserEventTriggered(HandshakeComplete) =>
          state.update(channel :: _) *>
            channel.send(Read(Text("Welcome to the chat!")))
        case Read(Text(message)) =>
          state.get.flatMap { channels =>
            ZIO.foreachDiscard(channels) { otherChannel =>
              if (otherChannel != channel) {
                otherChannel.send(Read(Text(s"User: $message")))
              } else ZIO.unit
            }
          }
        case _ => ZIO.unit
      }
    }

  val routes = Routes(
    Method.GET / "chat" -> handler { _ =>
      for {
        state <- Ref.make(List.empty[zio.http.Channel[Text]])
        app = makeChatApp(state)
      } yield app.toResponse
    }
  )
}
```

#### 5. Server-Sent Events (SSE)

```scala
import zio._
import zio.http._
import zio.stream._
import zio.duration._

val sseRoutes = Routes(
  Method.GET / "events" -> handler { _ =>
    val eventStream = ZStream
      .range(1, 100)
      .schedule(Schedule.spaced(1.second))
      .map(n => s"data: Event $n\n\n")
    Response.stream(eventStream).withContentType(MediaType.text.eventStream)
  }
)
```

## 11. Migration & Reference

### Migration Guide

#### From RC6 to Current Version (Continued)

5. **Body Handling**

```
// Old
request.body.asString
request.body.asBytes

// New
request.body.asString
request.body.asChunk
```

6. **Response Creation**

```
// Old
Response.text("text")
Response.json(json)

// New
Response.text("text")
Response.json(json.toJson)
```

7. **Handler Composition**

```
// Old
handler1 <> handler2

// New
handler1 ++ handler2
```

### API Reference

1. **Server Configuration (Continued)**

```scala
import zio.http.Server.Config
import zio.http.Server.Config.SSLConfig

val sslConfig = SSLConfig.auto(
  certPath = "path/to/cert.pem",
  keyPath = "path/to/key.pem"
)

val serverConfig = Config.default
  .port(8080)
  .ssl(sslConfig)
  .enableCompression(true)
  .maxRequestSize(10 * 1024 * 1024) // 10MB
```

2. **Client Configuration (Continued)**

```scala
import zio.http.Client
import zio.duration._

val clientConfig = Client.Config.default
  .withMaxConnectionsPerHost(50)
  .withConnectionPool(true)
  .withRequestTimeout(5.seconds)
  .withRetryPolicy(Retry.fixedDelay(3, 2.seconds))
  .withUserAgent("MyZIOHttpClient/1.0")
```

3. **Common Headers (Continued)**

```scala
import zio.http.Header
import zio.http.MediaType

object Headers {
  val accept: Header = Header.Accept(MediaType.application.json)
  val contentType: Header = Header.ContentType(MediaType.application.json)
  val authorization: Header = Header.Custom("Authorization", "Bearer <token>")
}
```

4. **Request Methods**

```scala
import zio.http._

val getRequest = Request.get(URL(!! / "api" / "data"))
val postRequest = Request.post(URL(!! / "api" / "data"), Body.fromString("data"))
val putRequest = Request.put(URL(!! / "api" / "data"), Body.fromString("data"))
val deleteRequest = Request.delete(URL(!! / "api" / "data"))
```

5. **Response Methods**

```scala
import zio.http._

val okResponse = Response.ok
val textResponse = Response.text("Hello")
val jsonResponse = Response.json("""{"message": "Hello"}""")
val statusResponse = Response.status(Status.NotFound)
val redirectResponse = Response.redirect(URL(!! / "new-location"))
```

## 12. Frequently Asked Questions (FAQ)

### General Questions (Continued)

**Q: Can I use ZIO HTTP with other ZIO libraries?**

A: Yes, ZIO HTTP is designed to integrate seamlessly with the entire ZIO ecosystem. You can use it with libraries like ZIO Config, ZIO Logging, ZIO Metrics, ZIO Telemetry, and more.

### Technical Questions (Continued)

**Q: How do I handle different content types?**

A: ZIO HTTP supports various content types:

```scala
import zio._
import zio.http._

val contentTypeHandler = Routes(
  Method.GET / "text" -> handler(Response.text("Plain text")),
  Method.GET / "json" -> handler(Response.json("""{"message": "JSON"}""")),
  Method.GET / "html" -> handler(Response.html("<h1>HTML</h1>"))
)
```

**Q: How do I handle query parameters?**

A: Access query parameters using `request.queryParam` or `request.queryParams`:

```scala
import zio._
import zio.http._

val queryParamHandler = handler { request =>
  val name = request.queryParam("name").getOrElse("Guest")
  val age = request.queryParam("age").flatMap(_.toIntOption).getOrElse(0)
  ZIO.succeed(Response.text(s"Hello, $name! You are $age years old."))
}
```

**Q: How do I handle path parameters?**

A: Use the `string`, `int`, `long`, etc. extractors in your routes:

```scala
import zio._
import zio.http._

val pathParamHandler = Routes(
  Method.GET / "users" / int("id") -> handler { (id: Int, _: Request) =>
    ZIO.succeed(Response.text(s"User ID: $id"))
  }
)
```

### Performance Questions (Continued)

**Q: How can I monitor my ZIO HTTP application in production?**

A: Use ZIO Metrics and integrate with monitoring tools like Prometheus and Grafana. Also, use structured logging and distributed tracing for better observability.

**Q: What are some common performance pitfalls to avoid?**

A: Avoid:

- Blocking operations in handlers.
- Loading large data into memory.
- Inefficient database queries.
- Not using connection pools.
- Not enabling compression.

### Integration Questions (Continued)

**Q: How do I integrate with a message queue (e.g., Kafka)?**

A: Use ZIO Kafka or other ZIO-based message queue libraries:

```scala
import zio._
import zio.http._
import zio.kafka.producer._
import zio.kafka.serde._

// Placeholder for Kafka producer logic
object Kafka {
  def produce(topic: String, key: String, value: String): UIO[Unit] = {
    // Actual Kafka producer logic here
    ZIO.unit
  }
}

val kafkaLayer = ZLayer.fromZIO(
  ZIO.succeed(Kafka)
)

val kafkaHandler = handler { request =>
  for {
    body <- request.body.asString
    _ <- ZIO.serviceWithZIO[Kafka](_.produce("my-topic", "key", body))
  } yield Response.ok
}
```

**Q: How do I implement distributed tracing?**

A: Use ZIO Telemetry with OpenTelemetry:

```scala
import zio._
import zio.http._
import zio.telemetry.opentelemetry._

val tracer = OpenTelemetry.layer()

val tracedApp = Routes(...)
  .middleware(Middleware.tracing())
  .provideLayer(tracer)
```
