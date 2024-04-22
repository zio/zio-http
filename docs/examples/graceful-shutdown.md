---
id: graceful-shutdown
title: "Graceful Shutdown Example"
sidebar_label: "Graceful Shutdown"
---

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/GracefulShutdown.scala")
```

**Explanation**

* **The `app`:**  A simple HTTP handler simulates request processing by waiting for 10 seconds before responding with "done".
* **The `run` method:**
   - **`started` Promise:** Tracks when the server has fully started.
   - **Server Setup:** Starts the HTTP server with the `app` handler and signals completion using the `started` promise.
   - **Simulated Interruption:** Waits 2 seconds, then gracefully interrupts the server.
   - **Test Request:** Sends a request to the server to demonstrate its availability while running. 
   - **Output:** Prints the response from the test request.
* **Dependency Provision:**  Provides the necessary HTTP client and server configurations for the code to run. 
