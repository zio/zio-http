---
id: middleware-cors-handling
title: "Middleware CORS Handling Example"
sidebar_label: "Middleware CORS Handling"
---

CORS is a mechanism that allows web browsers to safely access resources from different origins.

This code provides a practical example of setting up an HTTP server with Cross-Origin Resource Sharing (CORS) enabled:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/HelloWorldWithCORS.scala")
```

**Explaination** 

This code sets up a simple web application with Cross-Origin Resource Sharing (CORS) enabled.


- It defines a `CorsConfig` specifying allowed origins and their corresponding access control rules.
- Two HTTP servers are created:
  1. `frontendServer`: Serves a frontend application that interacts with the backend.
  2. `backendServer`: Serves a backend application that provides a JSON response.

- The `backend` and `frontend` HTTP applications are configured using the `Routes` DSL to define routes and handlers.
- The `backend` application responds to requests to `/json` with a JSON message.
- The `frontend` application responds to requests to any path (PathCodec.empty) with an HTML page containing JavaScript code that fetches data from the backend endpoint at `http://localhost:8080/json`.
- The `cors` middleware is applied to the `backend` application, allowing cross-origin requests from `http://localhost:3000` as specified in the `CorsConfig`.