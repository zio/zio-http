---
id: index
title: "Introduction to ZIO Http"
sidebar_label: "ZIO Http"
---

ZIO HTTP is an powerful library that empowers developers to build highly performant HTTP-based services and clients using functional Scala and ZIO, with Netty as its core. This library provides powerful functional domains that make it easy to create, modify, and compose applications. Let's start by exploring the HTTP domain, which involves creating an HTTP app when using ZIO HTTP. Here's an example of a simple echo server using ZIO-HTTP:

```scala
package example

import zio._

import zio.http._

object RequestStreaming extends ZIOAppDefault {

  // Create HTTP route which echos back the request body
  val app = Http.collect[Request] { case req @ Method.POST -> Root / "echo" =>
    // Returns a stream of bytes from the request
    // The stream supports back-pressure
    val stream = req.body.asStream

    // Creating HttpData from the stream
    // This works for file of any size
    val data = Body.fromStream(stream)

    Response(body = data)
  }

  // Run it like any simple app
  val run: UIO[ExitCode] =
    Server.serve(app).provide(Server.default).exitCode
}
```

ZIO-HTTP provides a core library, `zhttp-http`, which includes the base HTTP implementation and server/client capabilities based on ZIO. Additional functionality like serverless support, templating, and websockets are available through separate add-on modules. ZIO-HTTP applications can be easily integrated into different deployment platforms, such as server-based, serverless, or compiled to native binaries.

The principles of ZIO-HTTP are:

- Application as a Function: HTTP services in ZIO-HTTP are composed of simple functions. The `HttpApp` type represents a function from an `HttpRequest` to a `ZIO` effect that produces an `HttpResponse`.
- Immutability: Entities in ZIO-HTTP are immutable by default, promoting functional programming principles.
- Symmetric: The same `HttpApp` interface is used for both defining HTTP services and making HTTP requests. This enables easy testing and integration of services without requiring an HTTP container.
- Minimal Dependencies: The core `zhttp-http` module has minimal dependencies, and additional add-on modules only include dependencies required for specific functionality.
- Testability: ZIO-HTTP supports easy in-memory and port-based testing of individual endpoints, applications, websockets/SSE, and complete suites of microservices.
- Portability: ZIO-HTTP applications are portable across different deployment platforms, making them versatile and adaptable.

By leveraging the power of ZIO and the simplicity of functional programming, ZIO-HTTP provides a robust and flexible toolkit for building scalable and composable HTTP services in Scala.

## Quickstart

Eager to start coding without delay? If you're in a hurry, you can follow the [quickstart]() guide or explore the [examples repository](https://github.com/zio/zio-http/tree/main/zio-http-example), which demonstrates different use cases and features of ZIO-HTTP.

## Module feature overview

Core:

- Lightweight and performant HTTP handler and message objects
- Powerful routing system with support for path-based and parameterized routes
- Typesafe HTTP message construction and deconstruction
- Extensible filters for common HTTP functionalities such as caching, compression, and request/response logging
- Support for cookie handling
- Servlet implementation for integration with Servlet containers
- Built-in support for launching applications with an embedded server backend

Client:

- Robust and flexible HTTP client with support for synchronous and asynchronous operations
- Adapters for popular HTTP client libraries such as Apache HttpClient, OkHttp, and Jetty HttpClient
- Websocket client with blocking and non-blocking modes
- GraphQL client integration for consuming GraphQL APIs

Server:

- Lightweight server backend spin-up for various platforms including Apache, Jetty, Netty, and SunHttp
- Support for SSE (Server-Sent Events) and Websocket communication
- Easy customization of underlying server backend
- Native-friendly for compilation with GraalVM and Quarkus

Serverless:

- Function-based support for building serverless HTTP and event-driven applications
- Adapters for AWS Lambda, Google Cloud Functions, Azure Functions, and other serverless platforms
- Custom AWS Lambda runtime for improved performance and reduced startup time

Contract:

- Typesafe HTTP contract definition with support for path parameters, query parameters, headers, and request/response bodies
- Automatic validation of incoming requests based on contract definition
- Self-documenting routes with built-in support for OpenAPI (Swagger) descriptions

Templating:

- Pluggable templating system support for popular template engines such as Dust, Freemarker, Handlebars, and Thymeleaf
- Caching and hot-reload template support for efficient rendering

Message Formats:

- First-class support for various message formats such as JSON, XML, YAML, and CSV
- Seamless integration with popular libraries like Jackson, Gson, and Moshi for automatic marshalling and unmarshalling

Resilience:

- Integration with Resilience4J for implementing resilience patterns such as circuit breakers, retries, rate-limiting, and bulkheading

Metrics:

- Support for integrating zio-http applications with Micrometer for monitoring and metrics collection

Security:

- OAuth support for implementing authorization flows with popular providers like Auth0, Google, Facebook, and more
- Digest authentication support for secure client-server communication

Cloud Native:

- Tooling and utilities for operating zio-http applications in cloud environments such as Kubernetes and CloudFoundry
- Support for 12-factor configuration, dual-port servers, and health checks

Testing:

- Approval testing extensions for testing zio-http Request and Response messages
- Chaos testing API for injecting failure modes and evaluating application behavior under different failure conditions
- Matchers for popular testing frameworks like Hamkrest, Kotest, and Strikt

Service Virtualization:

- Record and replay HTTP contracts to simulate virtualized services using Servirtium Markdown format
- Includes Servirtium MiTM (Man-in-the-Middle) server for capturing and replaying HTTP interactions

WebDriver:

- Lightweight implementation of Selenium WebDriver for testing zio-http applications

These features provide a comprehensive set of tools and capabilities for building scalable, performant, and secure HTTP applications with zio-http.

## Example

This brief illustration is intended to showcase the ease and capabilities of zio-http. Additionally, refer to the quickstart guide for a minimalistic starting point that demonstrates serving and consuming HTTP services with dynamic routing.

To install, add these dependencies to your `build.sbt`:

```scala
package example

import zio._
import zio.http.HttpAppMiddleware.basicAuth
import zio.http._

object BasicAuth extends ZIOAppDefault {

  // Define an HTTP application that requires a JWT claim
  val user: HttpApp[Any, Nothing] = Http.collect[Request] { case Method.GET -> Root / "user" / name / "greet" =>
    Response.text(s"Welcome to the ZIO party! ${name}")
  }

  // Compose all the HttpApps together
  val app: HttpApp[Any, Nothing] = user @@ basicAuth("admin", "admin")

  // Run the application like any simple app
  val run = Server.serve(app).provide(Server.default)
}
```

# Explanation of the code above

- The BasicAuth object extends ZIOAppDefault, which is a trait that provides a default implementation for running ZIO applications.

- The code imports the necessary dependencies from  ZIO and ZIO HTTP.

- The user value represents an HTTP application that requires a JWT claim. It uses the Http.collect combinator to pattern match on GET requests with a specific path pattern (Root / "user" / name / "greet") and responds with a greeting message that includes the extracted name.

- The app value is created by composing the user HTTP application with the basicAuth middleware. The basicAuth function takes a username and password as arguments and returns a middleware that performs basic authentication. It applies basic authentication with the username "admin" and password "admin" to the user application.

- Finally, the server is run using the Server.serve method. The app is provided as the HTTP application, and Server.default is provided as the server configuration. The server configuration contains default settings for the server, such as the port to listen on. The run value represents the execution of the server. It starts the ZIO runtime and executes the server, making it ready to receive and respond to HTTP requests.
