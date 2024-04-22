---
id: streaming
title: "Streaming Examples"
sidebar_label: "Streaming"
---

## Streaming Request

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/RequestStreaming.scala")
```

**Explanation**

**This code creates an HTTP server route that acts like a mirror for POST request data.**

**How it works:**

* **Route Setup:** Defines a route named "/echo" that only accepts POST requests.
* **Reads as Stream:** Takes the incoming request body and reads it as a continuous stream of data.
* **Echoes Back:**  Packages the streamed data directly as the response to the sender.

**Key Point:** This demonstrates how ZIO HTTP handles data as streams, allowing efficient processing of even large requests.




## Streaming Response

The code demonstrate a simple http server that can be use to stream data to a client and streaming responses:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/StreamingResponse.scala")
```

**Explanation**

**This code sets up an HTTP server with two routes:**

* **Health Check:** A route at "/health" that sends a simple "OK" response to confirm the server is running.

* **Streaming Data:** A route at "/stream" that continuously sends chunks of data. This demonstrates ZIO HTTP's ability to handle streaming responses.

**Key Points:**

* **ZStream Power:**  The streaming response is powered by `ZStream`, allowing efficient delivery of data over time.
* **Flexibility:** This type of streaming is useful for sending ongoing updates or large amounts of data without overwhelming the system. 




## Streaming File

This code showcases the utilization of ZIO HTTP to enable file streaming in an HTTP server:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/FileStreaming.scala")
```

**Explanation**

**This code sets up an HTTP server that streams files to users.**

**Key Points**

* **Routes:**
    * `/health`: A basic check to make sure the server is working.
    * `/blocking`: Demonstrates streaming a file (like "README.md")  using a method suitable for smaller files.
    * `/video`, `/text`:  Shows how to efficiently stream larger files (like videos) or other file types directly.

* **File Streaming Power:** ZIO HTTP is designed to handle file streaming efficiently, making it ideal for serving videos, large datasets, or other content that shouldn't be loaded into memory all at once.
