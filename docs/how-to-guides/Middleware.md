# Middleware Tutorial

Middleware is a powerful tool in managing the complexity of HTTP applications and addressing common concerns. It allows for clean and modular code organization, making it easier to maintain and evolve the application over time.

## What is middleware?

Middleware is a layer of software that sits between the application and the HTTP server. It intercepts all requests and responses, and can be used to perform a variety of tasks, such as:

* Authentication
* Authorization
* Logging
* Error handling
* Rate limiting
* Caching

## Why are middlewares needed?

Middleware is needed to handle common cross-cutting concerns that are not specific to a particular route or endpoint. For example, authentication and authorization are concerns that need to be applied to all requests, regardless of the route being accessed.

If we were to implement authentication and authorization directly in our application code, it would quickly become cluttered and difficult to maintain. Middleware allows us to keep our application code clean and focused, while the concerns are managed independently.

## How do middlewares work?

Middlewares are typically implemented as functions that take an `HttpRequest` and return an `HttpResponse`. The middleware function can then perform any number of tasks, such as:

* Inspecting the request headers
* Parsing the request body
* Calling an external service
* Logging the request
* Throwing an error

If the middleware function returns a non-`HttpResponse`, the request will be aborted and the middleware will not be called again.

## How to use middlewares in ZIO-HTTP

ZIO-HTTP provides a number of built-in middlewares, as well as a simple API for creating custom middlewares. To use a middleware, simply attach it to your `HttpApp` using the `@@` operator.

For example, the following code attaches the `BasicAuthMiddleware` to the `HttpApp`:

```scala
val app = HttpApp.collectZIO[Request] {
  case Method.GET -> Root / "users" / id =>
    // Core business logic
    dbService.lookupUsersById(id).map(Response.json(_.json))
  case Method.GET -> Root / "users" =>
    // Core business logic
    dbService.paginatedUsers(pageNum).map(Response.json(_.json))
} @@ BasicAuthMiddleware(credentials)
```

The `BasicAuthMiddleware` middleware will inspect the `Authorization` header of each request and verify that it contains a valid username and password. If the credentials are valid, the middleware will continue the request processing chain. If the credentials are invalid, the middleware will return a `Unauthorized` response.

## Conclusion

Middleware is a powerful tool that can be used to manage the complexity of HTTP applications and address common concerns. By using middleware, we can keep our application code clean and focused, while the concerns are managed independently.

Using the ZIO-HTTP library, you can easily attach, combine, and create your own middlewares to address your application's specific needs.

That concludes our tutorial on middleware. Happy coding!