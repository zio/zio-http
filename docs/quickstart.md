## Quickstart

Excited to begin your journey? This page provides an excellent overview of zio-http, making it a great starting point.

An example of a minimal application using the zio-http would typically exhibit the following structure:

**Save this in a file** `build.sbt`

```scala
scalaVersion := "2.13.6"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio-http" % "3.0.0-RC2"
)
```

```scala
// save this in a file "QuickStart.scale"
package example

import zio._

import zio.http._

object QuickStart extends ZIOAppDefault {

  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> Root / "hello" => Response.text("Hello, World!")
    case Method.GET -> Root / "greet" / name => Response.text(s"Hello, $name!")
  }

// Run it like any simple app
  override val run = Server.serve(app).provide(Server.default)
}
```

**Code Explainaiton:**

so what's this code doing ?

- The `package example` statement indicates that the code belongs to the example package.

- The `import` statements bring in the required classes and methods from the `zio and zio.http` libraries.

- The QuickStart object represents the entry point of the application. It extends the `ZIOAppDefault trait`, which simplifies the creation of a ZIO application.

- The app value is of type `HttpApp[Any, Nothing]`, which represents an HTTP application that handles requests. It is created using the `Http.collect method`, which allows pattern matching on the incoming requests. In this case, there are two cases specified using partial functions:

- The first case matches a GET request with the path `"/hello"`. It responds with a Response.text containing the message `"Hello, World!"`.

- The second case matches a GET request with a path in the format `"/greet/{name}"`. It extracts the name parameter from the path and responds with a Response.text containing the message `"Hello, {name}!"`.

- The run method is overridden from the `ZIOAppDefault` trait. This method serves as the entry point of the application. It starts the HTTP server by using the Server.serve method, which creates a server that serves the app defined earlier. The provide method is used to provide the default Server environment to the server.

- The `Server.default` value represents a default configuration for the HTTP server. It uses the default settings such as the port number.

- When the application is executed, the run method is called. It starts the HTTP server, which listens for incoming requests and responds accordingly.
