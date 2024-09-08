# Middleware

A middleware has the purpose of intercepting a request, a response or both. It helps in implementing cross-cutting concerns like access logging, authentication, etc.

ZIO HTTP provides a lot out-of-the-box middlewares. For example for CORS or authentication.

For more details how to use middlewares, see the [middleware documentation](./../reference/aop/middleware.md).

## Handler Aspect

A `HandlerAspect` is a special middleware, that can not only intercept requests and responses, but also compute values based on the request and inject it back into the request handler.
This is useful for example for authentication, where the handler aspect can extract the user from the request or the database.

For more details how to use handler aspects, see the [handler aspect documentation](./../reference/aop/handler_aspect.md).
