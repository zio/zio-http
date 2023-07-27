---
id: endpoint
title: Endpoint
---

## Endpoint

Endpoints in ZIO-HTTP represent individual API operations or routes that the server can handle. They encapsulate the structure and behavior of the API endpoints in a type-safe manner, making them a fundamental building block for defining HTTP APIs.

### Endpoint Definition:

- Endpoints are defined using the `Endpoint` object's combinators, such as `get`, `post`, `path`, `query`, and more. These combinators allow you to specify various aspects of the endpoint, such as the HTTP method, URL path, query parameters, request/response bodies, and more.

### Middleware:

- Middleware can be applied to endpoints using the `@@` operator. Middleware allows you to add additional behavior or processing to the endpoint. For instance, you can handle authentication, validation, error handling, logging, or implement any custom logic needed for the endpoint using middleware.

### Endpoint Implementation:

- Endpoints are implemented using the `implement` method, which takes a function specifying the logic to handle the request and generate the response. This implementation function receives an instance of the request as input and produces an instance of the response as output.

- Inside the implementation function, you can use ZIO effects to perform computations, interact with dependencies, and produce the desired response. ZIO's functional approach makes it easy to handle errors, perform asynchronous operations, and compose complex behaviors within the endpoint implementation.

### Endpoint Composition:

- Endpoints can be composed together using operators like `++`, allowing you to build a collection of endpoints that make up your API. This composition enables you to structure the API by grouping related endpoints or creating reusable components.

### Converting to App:

- To serve the defined endpoints, they need to be converted to an HTTP application (`HttpApp`). This conversion is done using the `toApp` method, which prepares the endpoints to be served as an HTTP application.

- Any required middleware can be applied during this conversion to the final app. Middleware added to the endpoints will be applied to the HTTP application, ensuring that the specified behavior is enforced for each incoming request.

### Running an App:

- ZIO HTTP server requires an `HttpApp[R]` to run. The server can be started using the `Server.serve()` method, which takes the HTTP application as input and any necessary configurations.

- The server is responsible for listening on the specified port, accepting incoming connections, and handling the incoming HTTP requests by routing them to the appropriate endpoints.

With `Endpoint` in ZIO-HTTP, you can define and implement your API endpoints in a type-safe and composable way. The DSL allows you to specify the details of each endpoint, handle middleware for additional behavior, and easily compose endpoints to structure your API. This powerful concept empowers developers to build robust and scalable API services using ZIO-HTTP.