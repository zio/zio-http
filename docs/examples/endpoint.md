---
id: endpoint
title: "Endpoint Examples"
sidebar_label: "Endpoint"
---

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/EndpointExamples.scala")
```

This code demonstrates how to create RESTful API endpoints in ZIO HTTP, including OpenAPI documentation and client-side usage.

- It defines two endpoints:
  1. `getUser`: Retrieves user information by user ID.
  2. `getUserPosts`: Retrieves a list of posts for a user by user ID and post ID, with an optional query parameter for filtering by name.

- Middleware for authentication (`auth`) is applied to both endpoints.

- Implementations for the endpoints are provided:
  - `getUserRoute`: Handles requests to retrieve user information.
  - `getUserPostsRoute`: Handles requests to retrieve user posts.

- OpenAPI documentation is generated from the defined endpoints using `OpenAPIGen`.

- Routes are set up to serve the defined endpoints along with Swagger UI for documentation.

- The `app` is constructed by converting the defined routes to an HTTP application.

- A sample request is created to test the server setup.

- Finally, a client example is provided:
  - A `Client` is created and configured to interact with the server.
  - An `EndpointLocator` is set up to locate endpoints on the server.
  - An `EndpointExecutor` is created to execute the defined endpoints.
  - Sample requests (`x1` and `x2`) are made to the server using the executor, and the results are debugged.
