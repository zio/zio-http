---
id: hello-world
title: "Hello World Example"
sidebar_label: "Hello World"
---

## Simple Example

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/HelloWorld.scala")
```
**Explanation**
This script sets up a simple HTTP server using ZIO HTTP.

1. **HTTP Routes**:
   - Two routes are defined:
     - GET `/text`: Responds with "Hello World!" as plain text.
     - GET `/json`: Responds with `{"greetings": "Hello World!"}` as JSON.

2. **Server Configuration**:
   - The `textRoute` and `jsonRoute` are combined into an HTTP application (`app`) using `Routes`.
   - `Routes` is a convenient way to compose multiple routes into a single HTTP application.

3. **Server Startup**:
   - The `run` effect starts the server using `Server.serve`.
   - It's provided with the `app` and the default server environment (`Server.default`).


## Advanced Example

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/HelloWorldAdvanced.scala")
```

**Explanation**
Absolutely! Here's a simplified version of your example doc, keeping the main points but making it easier to understand for someone updating documentation:

**Simplified Explanation**

This script creates a basic but configurable HTTP server using the ZIO HTTP library.

**Routes**

* **`/foo` and `/bar`:** Simple routes that return the text "foo" and "bar".
* **`/random`:** Responds with a randomly generated 10-character string.
* **`/utc`:**  Returns the current time in UTC (universal time).

**Server Setup**

* **Port:** The server will use a randomly available port unless you provide one. 
* **Thread Count:** You can control how many threads the server uses by providing a command-line argument.

**Key Points**

* This example shows how to set up a basic but flexible HTTP server using ZIO HTTP.
* You can customize aspects of the server's behavior. 


## Advanced with Middlewares Example

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/HelloWorldWithMiddlewares.scala")
```

**Explanation**
This code sets up an HTTP server with ZIO HTTP and demonstrates the usage of middlewares. 

1. **Route Configuration**:
   - Defines two routes:
     - `/text`: Responds with "Hello World!" immediately.
     - `/long-running`: Delays for 5 seconds before responding with "Hello World!".

2. **Middleware Configuration**:
   - `Middleware.debug`: Prints debug information about incoming requests and outgoing responses.
   - `Middleware.timeout`: Closes the connection if the request takes more than 3 seconds.
   - `Middleware.addHeader`: Adds a static header `X-Environment: Dev`.
   - `serverTime`: Adds a dynamic header `X-Time` containing the current server time in milliseconds.

3. **Server Setup**:
   - Combines the routes and middlewares using the `@@` operator.
   - Starts the server with the configured app and middlewares.