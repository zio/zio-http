# ZIO HTTP Documentation

## Table of Contents

1. [Introduction](#introduction)
2. [Concepts](#concepts)
3. [Routing](#routing)
4. [Request Handling](#request-handling)
5. [Server](#server)
6. [Client](#client)
7. [Middleware](#middleware)
8. [Endpoint](#endpoint)
9. Tutorials
   - [Your First ZIO HTTP App](#your-first-zio-http-app)
   - [Deploying a ZIO HTTP App](#deploying-a-zio-http-app)
   - [Testing Your ZIO HTTP App](#testing-your-zio-http-app)
10. [How-to Guides](#how-to-guides)
11. Reference
    - [API Docs](#api-docs)
    - [Server Backend](#server-backend)
    - [WebSockets](#websockets)
    - [JSON Handling](#json-handling)
    - [Metrics](#metrics)
    - [Request Logging](#request-logging)
    - [Performance](#performance)
12. [FAQ](#faq)
13. [Conclusion](#conclusion)

------

## Introduction

**ZIO HTTP** is a high-performance, fully asynchronous HTTP library built on top of the [ZIO](https://zio.dev/) ecosystem. It offers a robust foundation for building scalable and maintainable web applications and services in Scala. Leveraging ZIO's powerful concurrency and error-handling capabilities, ZIO HTTP provides an elegant API for both server and client-side HTTP operations.

### Key Features

- **Asynchronous and Non-blocking:** Built for high concurrency with minimal resource usage.
- **Composable Routing:** Define routes using a declarative and type-safe DSL.
- **Middleware Support:** Easily extend functionality with reusable middleware components.
- **Streaming Support:** Handle large payloads and real-time data streams efficiently.
- **Built-in JSON Handling:** Seamless integration with JSON libraries like Circe.
- **WebSocket Integration:** Enable real-time, bidirectional communication.
- **Metrics and Logging:** Monitor application performance and behavior.

### Getting Started

To start using ZIO HTTP, add the following dependency to your `build.sbt`:

```
scala


Copier le code
libraryDependencies += "dev.zio" %% "zio-http" % "1.0.0"
```

Ensure you have the necessary repositories configured and the Scala version compatible with ZIO HTTP.

------

## Concepts

Understanding the foundational concepts of ZIO HTTP is crucial for effectively utilizing its capabilities.

### HttpApp and HttpRoutes

- **HttpApp:** Represents a complete HTTP application capable of handling requests and producing responses.
- **HttpRoutes:** Defines a set of routes that can be composed to form an `HttpApp`.

### Request and Response

- **Request:** Encapsulates all information about an incoming HTTP request, including method, headers, body, and URL.
- **Response:** Represents the HTTP response sent back to the client, containing status codes, headers, and body.

### Middleware

Middleware in ZIO HTTP allows you to intercept and modify requests and responses, enabling functionalities like authentication, logging, and more.

### Endpoint

An endpoint defines a specific route in your application, including the path, HTTP method, and the logic to handle the request.

------

## Routing

Routing in ZIO HTTP involves defining how your application responds to different HTTP requests based on their paths and methods.

### Defining Routes

Routes are defined using a combinatorial approach, allowing for clear and concise route definitions.

```
scalaCopier le codeimport zio.http._
import zio.http.model.Method

val helloRoute = Http.collect[Request] {
  case Method.GET -> Root / "hello" =>
    Response.text("Hello, World!")
}

val goodbyeRoute = Http.collect[Request] {
  case Method.GET -> Root / "goodbye" =>
    Response.text("Goodbye, World!")
}

val app = helloRoute ++ goodbyeRoute
```

### Route Composition

Routes can be composed using combinators like `++` to merge multiple `Http` instances.

```
scala


Copier le code
val combinedApp = helloRoute ++ goodbyeRoute
```

### Path Matching

ZIO HTTP offers flexible path matching with support for parameters and wildcards.

```
scalaCopier le codeval userRoute = Http.collect[Request] {
  case Method.GET -> Root / "user" / userId =>
    Response.text(s"User ID: $userId")
}
```

------

## Request Handling

Handling requests involves parsing incoming data, performing business logic, and constructing appropriate responses.

### Accessing Request Data

Retrieve query parameters, headers, and body content from the `Request` object.

```
scalaCopier le codeval echoRoute = Http.collectZIO[Request] {
  case req @ Method.POST -> Root / "echo" =>
    for {
      body <- req.body.asString
    } yield Response.text(body)
}
```

### Parsing JSON Requests

Use libraries like Circe to parse JSON request bodies.

```
scalaCopier le codeimport io.circe.generic.auto._
import zio.json._

case class Message(content: String)

val parseJsonRoute = Http.collectZIO[Request] {
  case req @ Method.POST -> Root / "message" =>
    for {
      json <- req.body.asString
      message <- ZIO.fromEither(json.fromJson[Message]).mapError(err => new Exception(s"JSON Parsing Error: $err"))
    } yield Response.json(message.toJson)
}
```

------

## Server

Setting up the server is straightforward with ZIO HTTP. Define your routes and serve them on a specified port.

### Starting the Server

```
scalaCopier le codeimport zio.http._

object MyServer extends ZIOAppDefault {
  val routes = Http.collect[Request] {
    case Method.GET -> Root / "ping" =>
      Response.text("pong")
  }

  def run = Server.serve(routes).provide(Server.default.port(8080))
}
```

### Server Configuration

Customize server settings like port, SSL, and connection limits.

```
scalaCopier le codeval customServer = Server.Config.default
  .port(9000)
  .ssl(
    keyStore = "path/to/keystore.jks",
    keyStorePassword = "password",
    protocol = "TLS"
  )
  .build

val server = Server.serve(routes).provide(customServer)
```

------

## Client

ZIO HTTP provides a powerful HTTP client for making requests to external services.

### Making GET Requests

```
scalaCopier le codeimport zio.http.client.Client
import zio._

object HttpClientExample extends ZIOAppDefault {
  def run = for {
    response <- Client.request(Request.get(URL.decode("https://api.example.com/data").toOption.get))
    body <- response.body.asString
    _ <- Console.printLine(s"Response Body: $body")
  } yield ()
    .provide(Client.default)
}
```

### Making POST Requests with JSON

```
scalaCopier le codeimport io.circe.generic.auto._
import zio.http._
import zio.json._

case class User(name: String, age: Int)

object HttpClientPostExample extends ZIOAppDefault {
  def run = for {
    user = User("Alice", 30)
    request = Request.post(
      url = URL.decode("https://api.example.com/users").toOption.get,
      body = Body.fromString(user.toJson, "application/json")
    )
    response <- Client.request(request)
    body <- response.body.asString
    _ <- Console.printLine(s"User Created: $body")
  } yield ()
    .provide(Client.default)
}
```

------

## Middleware

Middleware in ZIO HTTP allows you to intercept and modify requests and responses, adding cross-cutting concerns like authentication, logging, and more.

### Creating Middleware

Define middleware as functions that transform `Http` instances.

```
scalaCopier le codeimport zio.http.middleware._

object LoggingMiddleware {
  val logger: Middleware[Any, Nothing] = Middleware.accessLog(true)
}
```

### Applying Middleware

Apply middleware to your `HttpApp` using the `@@` operator.

```
scala


Copier le code
val securedApp = routes @@ LoggingMiddleware.logger
```

### Custom Middleware

Create custom middleware for specific functionalities.

```
scalaCopier le codeobject CustomHeaderMiddleware {
  def addHeader(key: String, value: String): Middleware[Any, Nothing] =
    Middleware.access.modifyResponse(_.addHeader(key, value))
}

val appWithCustomHeader = routes @@ CustomHeaderMiddleware.addHeader("X-Custom-Header", "MyValue")
```

------

## Endpoint

Endpoints represent individual routes in your application, encapsulating the path, method, and handler logic.

### Defining Endpoints

```
scalaCopier le codeimport zio.http._

case class Product(id: String, name: String, price: Double)

object ProductEndpoints {
  val getProduct = Http.collectZIO[Request] {
    case Method.GET -> Root / "product" / id =>
      // Fetch product logic
      for {
        product <- fetchProduct(id)
      } yield Response.json(product.toJson)
  }

  val createProduct = Http.collectZIO[Request] {
    case req @ Method.POST -> Root / "product" =>
      for {
        body <- req.body.asString
        product <- ZIO.fromEither(body.fromJson[Product]).mapError(err => new Exception(s"JSON Parsing Error: $err"))
        _ <- saveProduct(product)
      } yield Response.status(Status.Created)
  }

  val allEndpoints = getProduct ++ createProduct
}
```

### Composing Endpoints

Combine multiple endpoints to form the complete application.

```
scala


Copier le code
val app = ProductEndpoints.allEndpoints
```

------

## Tutorials

### Your First ZIO HTTP App

Build a simple "Hello, World!" application to get started with ZIO HTTP.

```
scalaCopier le codeimport zio.http._

object HelloWorldApp extends ZIOAppDefault {
  val helloRoute = Http.collect[Request] {
    case Method.GET -> Root / "hello" =>
      Response.text("Hello, World!")
  }

  def run = Server.serve(helloRoute).provide(Server.default.port(8080))
}
```

**Explanation:**

1. **Define the Route:** The `helloRoute` responds to `GET` requests at `/hello` with the text "Hello, World!".
2. **Start the Server:** The server listens on port `8080` and serves the defined route.

**Running the App:**

Execute the application using `sbt run`. Access `http://localhost:8080/hello` in your browser to see the response.

------

### Deploying a ZIO HTTP App

Deploying your ZIO HTTP application involves packaging it and running it in your desired environment. Here's a simple Docker deployment example.

**Dockerfile:**

```
dockerfileCopier le code# Use an official OpenJDK runtime as a parent image
FROM openjdk:17-jdk-slim

# Set the working directory
WORKDIR /app

# Copy the jar file
COPY target/scala-2.13/your-app.jar /app/your-app.jar

# Expose port 8080
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "your-app.jar"]
```

**Building and Running the Docker Image:**

```
bashCopier le codesbt assembly
docker build -t zio-http-app .
docker run -p 8080:8080 zio-http-app
```

**Explanation:**

1. **Dockerfile:** Defines the environment and steps to build the Docker image.
2. **Building the Image:** Use `sbt assembly` to create a fat jar, then build the Docker image with `docker build`.
3. **Running the Container:** Start the container, mapping port `8080` to access the application.

------

### Testing Your ZIO HTTP App

Testing ensures your application behaves as expected. Use ZIO Test for writing unit and integration tests.

```
scalaCopier le codeimport zio.test._
import zio.test.Assertion._
import zio.http._
import zio._

object HelloWorldSpec extends DefaultRunnableSpec {
  def spec = suite("HelloWorldApp Spec")(
    testM("should return Hello, World! on /hello") {
      val request = Request.get(URL.decode("http://localhost:8080/hello").toOption.get)
      for {
        response <- HelloWorldApp.helloRoute.run(request)
        body <- response.body.asString
      } yield assert(body)(equalTo("Hello, World!"))
    }
  )
}
```

**Explanation:**

1. **Define the Test Suite:** `HelloWorldSpec` contains tests for `HelloWorldApp`.
2. **Test Case:** Verifies that a `GET` request to `/hello` returns "Hello, World!".
3. **Running Tests:** Execute tests using `sbt test`.

------

## How-to Guides

### Authentication Middleware

Implementing authentication to secure your routes.

```
scalaCopier le codeimport zio.http._
import zio._

object AuthMiddleware {
  def authenticate(token: String): ZIO[Any, Throwable, Boolean] = {
    // Simple token validation logic
    ZIO.succeed(token == "valid-token")
  }

  val authMiddleware: Middleware[Any, Nothing] = Middleware.accessLog(true).andThen(Http.fromZIO[Request] { request =>
    request.headers.get("Authorization") match {
      case Some(authHeader) =>
        val token = authHeader.value.split(" ").last
        authenticate(token).flatMap {
          case true  => ZIO.succeed(Response.status(Status.Ok))
          case false => ZIO.succeed(Response.status(Status.Unauthorized))
        }
      case None =>
        ZIO.succeed(Response.status(Status.Unauthorized))
    }
  })
}

object SecuredApp extends ZIOAppDefault {
  val securedRoute = Http.collect[Request] {
    case Method.GET -> Root / "secure" =>
      Response.text("Secure Endpoint Accessed")
  }

  def run = Server.serve(securedRoute @@ AuthMiddleware.authMiddleware).provide(Server.default.port(8080))
}
```

**Explanation:**

1. **Authentication Logic:** `authenticate` function checks if the provided token is valid.
2. **Middleware Definition:** `authMiddleware` intercepts requests to validate the `Authorization` header.
3. **Applying Middleware:** Attach `authMiddleware` to routes using `@@`.
4. **Secured Route:** Only accessible if authentication passes.

### JSON Handling with Circe

Seamlessly parse and generate JSON using Circe.

```
scalaCopier le codeimport io.circe.generic.auto._
import zio.http._
import zio.json._

case class User(id: String, name: String, email: String)

object JsonHandlingApp extends ZIOAppDefault {
  val userRoute = Http.collectZIO[Request] {
    case req @ Method.POST -> Root / "user" =>
      for {
        json <- req.body.asString
        user <- ZIO.fromEither(json.fromJson[User]).mapError(err => new Exception(s"JSON Parsing Error: $err"))
        response = Response.json(user.toJson).setStatus(Status.Created)
      } yield response
  }

  def run = Server.serve(userRoute).provide(Server.default.port(8080))
}
```

**Explanation:**

1. **Define Data Model:** `User` case class represents the user data.
2. **Parsing JSON:** Extract and parse JSON from the request body into a `User` instance.
3. **Generating JSON:** Convert the `User` instance back to JSON for the response.

------

## Reference

### API Docs

Comprehensive API documentation is generated using tools like Scaladoc and integrated with ZIO HTTP.

```
scalaCopier le code/** 
 * Represents an HTTP request.
 *
 * @param method The HTTP method.
 * @param url The request URL.
 * @param headers The HTTP headers.
 * @param body The request body.
 */
case class Request(method: Method, url: URL, headers: Headers, body: Body)
```

**Generating API Docs:**

Run `sbt doc` to generate the Scaladoc documentation for your project.

### Server Backend

ZIO HTTP can be backed by various server implementations. By default, it uses the [Http4s](https://http4s.org/) backend.

```
scalaCopier le codeimport zio.http._
import zio._

object CustomBackendApp extends ZIOAppDefault {
  val customServer = Server.Config.default.backend(Http4sBackend.default)
    .port(8080)
    .build

  val routes = Http.collect[Request] {
    case Method.GET -> Root / "backend" =>
      Response.text("Using Custom Backend")
  }

  def run = Server.serve(routes).provide(customServer)
}
```

### WebSockets

Enable real-time communication using WebSockets.

```
scalaCopier le codeimport zio.http._
import zio.http.websocket._

object WebSocketApp extends ZIOAppDefault {
  val wsApp = Http.collectZIO[Request] {
    case req @ Method.GET -> Root / "ws" =>
      for {
        socket <- req.accept
        _ <- socket.receiveStream.foreach {
          case WebSocketFrame.Text(text) =>
            socket.send(WebSocketFrame.text(s"Echo: $text"))
          case _ => ZIO.unit
        }.forkDaemon
      } yield Response.ok
  }

  def run = Server.serve(wsApp).provide(Server.default.port(8080))
}
```

**Explanation:**

1. **Accept Connection:** `req.accept` upgrades the HTTP request to a WebSocket connection.
2. **Handle Messages:** Listen to incoming messages and respond accordingly.
3. **Echo Server:** Responds by echoing received text messages.

### JSON Handling

Integrate with JSON libraries like Circe for parsing and generating JSON.

```
scalaCopier le codeimport io.circe.generic.auto._
import zio.http._
import zio.json._

case class Product(id: String, name: String, price: Double)

object JsonApp extends ZIOAppDefault {
  val productRoute = Http.collectZIO[Request] {
    case req @ Method.POST -> Root / "product" =>
      for {
        json <- req.body.asString
        product <- ZIO.fromEither(json.fromJson[Product]).mapError(err => new Exception(s"JSON Error: $err"))
        response = Response.json(product.toJson).setStatus(Status.Created)
      } yield response
  }

  def run = Server.serve(productRoute).provide(Server.default.port(8080))
}
```

### Metrics

Monitor application performance and behavior using ZIO Metrics.

```
scalaCopier le codeimport zio.metrics._
import zio.http._

object MetricsApp extends ZIOAppDefault {
  val requestCounter = Metric.counter("requests_total")
  val responseTimeHistogram = Metric.histogram("response_time_ms")

  val metricsMiddleware = Middleware.accessLog(true).andThen(Http.fromZIO[Request] { request =>
    for {
      start <- Clock.currentTime(TimeUnit.MILLISECONDS)
      response <- Response.text("Metrics Example").provideSomeLayer(Server.default)
      end <- Clock.currentTime(TimeUnit.MILLISECONDS)
      duration = end - start
      _ <- responseTimeHistogram.update(duration.toDouble)
      _ <- requestCounter.increment
    } yield response
  })

  val app = Http.collect[Request] {
    case Method.GET -> Root / "metrics" =>
      Response.text("Metrics collected")
  }

  def run = Server.serve(app @@ metricsMiddleware).provide(Server.default.port(8080), Metric.default)
}
```

**Explanation:**

1. **Define Metrics:** `requests_total` counts the number of requests, and `response_time_ms` measures response times.
2. **Middleware:** Intercepts requests to update metrics.
3. **Serving Metrics:** The `/metrics` endpoint provides access to collected metrics.

### Request Logging

Implement comprehensive logging for incoming requests and outgoing responses.

```
scalaCopier le codeimport zio.http._
import zio.logging._

object LoggingApp extends ZIOAppDefault {
  val loggingMiddleware = Middleware.accessLog(true)

  val logRoute = Http.collect[Request] {
    case Method.GET -> Root / "log" =>
      Response.text("Logging Enabled")
  }

  def run = Server.serve(logRoute @@ loggingMiddleware).provide(
    Server.default,
    Logging.console()
  )
}
```

**Explanation:**

1. **Define Middleware:** `accessLog(true)` logs details of each request.
2. **Apply Middleware:** Attach to routes using `@@`.
3. **Logging Backend:** Use `Logging.console()` to output logs to the console.

------

## Performance

Optimizing the performance of your ZIO HTTP applications ensures scalability and responsiveness.

### Connection Pool Tuning

Adjust connection pool settings to balance resource usage and performance.

```
scalaCopier le codeval tunedPool = ConnectionPool(
  maxConnections = 500,
  maxIdleTime = 10.minutes,
  keepAlive = true
)

val clientWithTunedPool = Client.Config.default
  .connectionPool(tunedPool)
  .build

val optimizedRequest = for {
  response <- Client.make(clientWithTunedPool).request(Request.get(URL.decode("https://api.example.com/data").toOption.get))
  body <- response.body.asString
} yield body

def run = optimizedRequest.provide(Client.default).flatMap { body =>
  Console.printLine(s"Optimized Pool Response: $body")
}
```

### Asynchronous Processing

Leverage ZIO's asynchronous capabilities to handle multiple requests concurrently without blocking.

```
scalaCopier le codeobject AsyncProcessingApp extends ZIOAppDefault {
  val asyncRoute = Http.collectZIO[Request] {
    case Method.GET -> Root / "async" =>
      ZIO.foreachPar(1 to 1000)(_ => performAsyncTask()).map { results =>
        Response.json(results.toJson)
      }
  }

  def performAsyncTask(): Task[String] = {
    // Simulate an asynchronous task
    ZIO.succeed("Task Completed")
  }

  def run = Server.serve(asyncRoute).provide(Server.default.port(8080))
}
```

### Resource Management

Efficiently manage resources like threads, memory, and connections to prevent leaks and ensure optimal usage.

```
scalaCopier le codeobject ResourceManagementApp extends ZIOAppDefault {
  val resourceApp = Http.collectZIO[Request] {
    case Method.GET -> Root / "resource" =>
      ZIO.acquireRelease(ZIO.effectTotal(openResource()))(resource => ZIO.effectTotal(closeResource(resource)))
        .flatMap { resource =>
          ZIO.succeed(Response.text(s"Using resource: $resource"))
        }
  }

  def openResource(): String = "ResourceHandle"

  def closeResource(resource: String): Unit = println(s"Closed resource: $resource")

  def run = Server.serve(resourceApp).provide(Server.default.port(8080))
}
```

**Explanation:**

1. **Acquire Resource:** `ZIO.acquireRelease` ensures resources are safely acquired and released.
2. **Using the Resource:** Perform operations using the acquired resource.
3. **Automatic Release:** Resources are automatically released, even in the event of failures.

------

## FAQ

### How do I handle authentication in ZIO HTTP?

Authentication can be handled using middleware that intercepts requests, validates tokens or credentials, and modifies requests or responses accordingly. Refer to the [Authentication Middleware](#authentication-middleware) section for detailed examples.

### Can ZIO HTTP handle WebSockets?

Yes, ZIO HTTP has built-in support for WebSockets, enabling real-time, bidirectional communication. Refer to the [WebSockets](#websockets) section for examples.

### How do I perform graceful shutdowns with ZIO HTTP?

ZIO's managed resources and fiber interruption capabilities allow for graceful shutdowns. Ensure that all resources are properly released and ongoing requests are completed or canceled as needed.

```
scalaCopier le codeobject GracefulShutdownExample extends ZIOAppDefault {
  def run = for {
    serverFiber <- Server.serve(routes).provide(Server.default).fork
    _ <- Console.printLine("Server is running. Press ENTER to stop.")
    _ <- Console.readLine
    _ <- serverFiber.interrupt
    _ <- Console.printLine("Server has been stopped gracefully.")
  } yield ()
}
```

### How can I test my ZIO HTTP applications?

Testing can be performed using ZIO Test, enabling unit tests, integration tests, and property-based tests. Refer to the [Testing Your ZIO HTTP App](#testing-your-zio-http-app) section for comprehensive testing strategies.

### Is it possible to integrate ZIO HTTP with other libraries like Circe or Doobie?

Absolutely. ZIO HTTP integrates seamlessly with libraries like Circe for JSON handling and Doobie for database interactions. The provided examples demonstrate such integrations, ensuring you can leverage the full power of the Scala ecosystem.

------

## Conclusion

ZIO HTTP offers a powerful and flexible framework for building robust HTTP clients and servers in Scala. By leveraging ZIO's asynchronous and functional capabilities, you can create highly concurrent, scalable, and maintainable applications. This documentation has covered a wide range of topics, from basic setups to advanced integrations and best practices. Whether you're building a simple API or a complex, real-time application, ZIO HTTP provides the tools and abstractions necessary to succeed.

For more information, refer to the official ZIO HTTP documentation and explore the ZIO ecosystem for additional libraries and integrations.

If you have further questions or need assistance, feel free to reach out to the ZIO community through their forums or Gitter.

**Happy Coding!**