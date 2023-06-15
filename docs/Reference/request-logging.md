---
id: request-logging
title: "Request Logging"
---

**RequestLogging Traits, Functions, and Classes**

1. `trait RequestLogging`: Represents the trait responsible for request logging.

2. `requestLogging`: A function that creates a request logging middleware.
   - Parameters:
     - `level: Status => LogLevel`: Determines the log level based on the response status.
     - `failureLevel: LogLevel`: Determines the log level for failed requests.
     - `loggedRequestHeaders: Set[HeaderType]`: Defines the set of request headers to be logged.
     - `loggedResponseHeaders: Set[HeaderType]`: Defines the set of response headers to be logged.
     - `logRequestBody: Boolean`: Indicates whether to log the request body.
     - `logResponseBody: Boolean`: Indicates whether to log the response body.
     - `requestCharset: Charset`: Specifies the character set for the request.
     - `responseCharset: Charset`: Specifies the character set for the response.
   - Returns: `RequestHandlerMiddleware[Nothing, Any, Nothing, Any]`: A middleware that performs request logging.

**RequestLogging Usage**

1. Import the necessary dependencies:
   - `import zio.http.internal.middlewares.{RequestLogging, RequestHandlerMiddlewares}`
   - `import zio.http.{Request, Response, HttpError, Status, Header, Method, URL}`
   - `import zio.{Exit, LogAnnotation, LogLevel, Trace, ZIO}`

2. Create an instance of the HTTP application (`app`) that handles various requests.

3. Use the `requestLogging` function to create a middleware for request logging and attach it to the HTTP application using the `@@` operator.
   ```scala
   (app @@ requestLogging())
   ```

4. Customize the request logging behavior by providing appropriate values for the `level`, `failureLevel`, `loggedRequestHeaders`, `loggedResponseHeaders`, `logRequestBody`, `logResponseBody`, `requestCharset`, and `responseCharset` parameters.

5. Optionally, access and analyze the log entries to verify the expected request logging behavior.

Here's an example of how to use the `RequestLogging` middleware:

```scala
val app = Http.collectHandler[Request] {
  case Method.GET -> Root / "ok"     => Handler.ok
  case Method.GET -> Root / "error"  => Handler.error(HttpError.InternalServerError())
  case Method.GET -> Root / "fail"   => Handler.fail(Response.status(Status.Forbidden))
  case Method.GET -> Root / "defect" => Handler.die(new Throwable("boom"))
}

(app @@ requestLogging()).runZIO(Request.get(url = URL(Root / "ok")))
```

This example attaches the `requestLogging` middleware to the `app` HTTP application and runs a `GET` request to the `/ok` endpoint. The request will be logged according to the specified configuration.