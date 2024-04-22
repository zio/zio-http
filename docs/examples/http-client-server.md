---
id: http-client-server
title: HTTP Client-Server Example
sidebar_label: HTTP Client-Server
---

## Client and Server Example

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/ClientServer.scala")
```

**Explanation**

* **Server Configuration**
  - You have a simple route that listens for `GET` requests to the `/hello` path.
  - The handler for this route returns a plain text response containing the word "hello".

* **Client Request** 
  - This is where ZIO HTTP's client functionality comes in. ZIO HTTP allows you to define requests as ZIO effects.
  - Here, you're creating a request that will:
     - Use the `GET` method.
     - Target your server's `/hello` endpoint.

* **HTTP Application**
  - You're building your application with two routes:
     - **First:**  The server-side `/hello` route you defined initially.
     - **Second:** A root-level route (`/`) that triggers the client request and presumably does something with the response.

* **Server Setup**
   - You're firing up an HTTP server using fairly standard settings. It will run with default environments for your ZIO app as well as for the HTTP client side.

* **Execution**
   - Importantly, the ZIO program's exit code is tied to the server's execution. This means any errors during the server's operations will determine how the overall program terminates.


## Simple Client Example

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/SimpleClient.scala")
```

**Explanation**

* **Client Setup**
   - The JSONPlaceholder API is a great resource for testing and prototyping. The `/todos` endpoint provides a list of sample TODO items.
   - ZIO HTTP treats URLs properly, ensuring they are encoded correctly.

* **Request Execution**
   - **ZIO Effects:** The `program` is a description of a side effect (in this case, an HTTP request). Until run, it's just a blueprint, not an action.
   - **ZIO Environment:** `ZIO.service[Client]` accesses the HTTP client functionality built into ZIO HTTP. This relies on a `Client` environment being available at runtime.
   - **Chaining:**
      - `client.url(url)` prepares the HTTP request and specifies the target.
      - `.get("/")` sets the HTTP method to `GET`, and any additional path is appended to the base URL (since it's just "/", there's no change).
      - `.body.asString` tells ZIO HTTP to convert the response body into a simple String.  

* **Printing Response**
   - ZIO's `Console` service lets you do simple output for debugging.

* **Execution**
   - `.runtime[Scope].unsafeRun(program)` is where things get interesting:
     - **Runtime:** You're providing a default runtime system for dependencies (like the HTTP client)
     - **Scope:** You're supplying a default scope, which can hold resources that need cleanup.
     - **unsafeRun:** This tells the ZIO runtime to execute your effect, potentially throwing exceptions if something fails. 