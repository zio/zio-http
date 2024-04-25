---
id: middleware
title: Middleware in ZIO HTTP
---

In ZIO HTTP, middleware is a powerful concept that allows us to modify the behavior of HTTP requests and responses before and after they are processed by your application. Middleware functions are applied to the HTTP app using the `@@` operator, creating a pipeline of transformations that are executed in the order they are composed.

Here are some examples of middleware in ZIO HTTP:

1. **Middleware.metrics()**
   - This middleware collects metrics for your HTTP app, allowing you to monitor its performance and behavior.
   - In the `HelloWorldWithMetrics` example, the `backend` app has `Middleware.metrics()` applied, which collects metrics such as the number of requests with a specific custom header.

2. **Middleware.serveResources()**
   - This middleware serves static files from your application's resources directory.
   - In the `StaticFiles` example, `Middleware.serveResources(Path.empty / "static")` is used to serve static files from the "static" subdirectory of the resources directory.

3. **Middleware.cors()**
   - This middleware handles Cross-Origin Resource Sharing (CORS) for your HTTP app.
   - In the `HelloWorldWithCORS` example, `Middleware.cors(config)` is used with a custom `CorsConfig` to allow requests from `http://localhost:3000`.

4. **Middleware.debug**
   - This middleware logs debug information about incoming requests and outgoing responses.
   - In the `HelloWorldWithMiddlewares` example, `Middleware.debug` is used to print debug information about requests and responses.

5. **Middleware.timeout()**
   - This middleware sets a timeout for incoming requests, canceling them if they take too long to complete.
   - In the `HelloWorldWithMiddlewares` example, `Middleware.timeout(3 seconds)` is used to close the connection if a request takes more than 3 seconds.

6. **Middleware.addHeader()**
   - This middleware adds a static header to all responses.
   - In the `HelloWorldWithMiddlewares` example, `Middleware.addHeader("X-Environment", "Dev")` is used to add a static header to all responses.

7. **Custom Middleware**
   - You can create custom middleware by using the `Middleware.patchZIO` function.
   - In the `HelloWorldWithMiddlewares` example, `serverTime` is a custom middleware that adds a dynamic header with the current server time to all responses.

Middleware functions can be composed together using the `++` operator, creating a pipeline of transformations that are applied to the HTTP app. This allows you to combine multiple middleware functions to achieve the desired behavior for your application.

Overall, middleware in ZIO HTTP provides a flexible and powerful way to modify the behavior of your HTTP app, allowing you to add functionality such as logging, metrics, caching, authentication, and more, without tightly coupling it with your application's business logic. 

[Reference]
- [HelloWorldWithMetrics](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/HelloWorldWithMetrics.scala)
- [StaticFiles](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/StaticFiles.scala)
- [HelloWorldWithCORS](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/HelloWorldWithCORS.scala)
- [CounterProtocolStackExample](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/middleware/CounterProtocolStackExample.scala)
- [HelloWorldWithMiddlewares](https://github.com/zio/zio-http/blob/main/zio-http-example/src/main/scala/example/HelloWorldWithMiddlewares.scala)