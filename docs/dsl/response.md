---
id: response
title: Response
---

**ZIO HTTP** `Response` is designed to encode HTTP Response.
It supports all HTTP status codes and headers along with custom methods and headers (as defined in [RFC2616](https://datatracker.ietf.org/doc/html/rfc2616) )

## Creating a Response

A `Response` can be created with `status`, `headers`, and `data` using the default constructor:

```scala
case class Response(
  status: Status = Status.Ok,
  headers: Headers = Headers.empty,
  body: Body = Body.empty,
)
```

The below snippet creates a response with default params, `status` as `Status.OK`, `headers` as `Headers.empty` and `data` as `Body.Empty`:

```scala mdoc
import zio.http._
import zio._

Response()
```

### Status Codes

ZIO HTTP has several constructors for the most common status codes:

| Method                                | Description                                 | Status Code        |
|---------------------------------------|---------------------------------------------|--------------------|
| `Response.ok`                         | Successful request                          | 200 OK             |
| `Response.badRequest`                 | The server cannot or will not process the request due to an apparent client error | 400 Bad Request    |
| `Response.unauthorized`               | Similar to 403 Forbidden, but specifically for use when authentication is required and has failed or has not yet been provided | 401 Unauthorized  |
| `Response.forbidden`                  | The client does not have access rights to the content; that is, it is unauthorized | 403 Forbidden     |
| `Response.notFound`                   | The requested resource could not be found but may be available in the future | 404 Not Found     |
| `Response.internalServerError`        | A generic error message, given when an unexpected condition was encountered and no more specific message is suitable | 500 Internal Server Error |
| `Response.serviceUnavailable`         | The server cannot handle the request (because it is overloaded or down for maintenance) | 503 Service Unavailable |
| `Response.redirect`                   | Used to inform the client that the resource they're requesting is located at a different URI | 302 Found (Moved Temporarily) |
| `Response.seeOther`                   | Tells the client to look at a different URL for the requested resource | 303 See Other      |
| `Response.gatewayTimeout`             | The server was acting as a gateway or proxy and did not receive a timely response from the upstream server | 504 Gateway Timeout |
| `Response.httpVersionNotSupported`    | The server does not support the HTTP protocol version that was used in the request | 505 HTTP Version Not Supported |
| `Response.networkAuthenticationRequired` | The client needs to authenticate to gain network access | 511 Network Authentication Required |
| `Response.notExtended`                | Further extensions to the request are required for the server to fulfill it | 510 Not Extended   |
| `Response.notImplemented`             | The server either does not recognize the request method, or it lacks the ability to fulfill the request | 501 Not Implemented |

For example, to create a response with status code 200, we can use `Response.ok`:

```scala mdoc
Response.ok
```

And also to create a response with status code 404, we can use `Response.badRequest`:

```scala mdoc
Response.notFound

Response.notFound("The requested resource could not be found!")
```

If we want to create a response with a more specific status code, we can use the `status` method. It takes a `Status` as a parameter and returns a new `Response` with the corresponding status code:

```scala mdoc:compile-only
Response.status(Status.Continue)
```

To learn more about status codes, see [Status](status.md) page.

### From Plain Text, JSON, and HTML

**`Response.text`** creates a response with data as text, content-type header set to text/plain, and status code 200:

```scala mdoc
Response.text("hey")
```

**`Response.json`** creates a response with data as JSON, content-type header set to `application/json`, and status code 200:

```scala mdoc
Response.json("""{"greetings": "Hello World!"}""")
```

**`Response.html`** creates a response with data as HTML, content-type header set to `text/html`, and status code 200.

```scala mdoc
import zio.http.template._

Response.html(Html.fromString("html text"))
```

### Converting Failures to Responses

The `Response` companion object provides some constructors to convert exceptions into responses. These constructors are useful for error handling by converting failures into appropriate HTTP responses:

**`Response.fromThrowable`** Creates a new HTTP response based on the type of throwable provided. This method facilitates the conversion of various types of exceptions into appropriate HTTP responses, making error handling more streamlined:

```scala
object Response {
  def fromThrowable(throwable: Throwable): Response = ???
}
```

Here is the table of exceptions and their corresponding status code:

| Throwable Type                      | Status Class       | Status Code | Description                                    |
|-------------------------------------|--------------------|-------------|------------------------------------------------|
| `AccessDeniedException`             | `Forbidden`        | 403         | Access to a resource is denied.               |
| `IllegalAccessException`           | `Forbidden`        | 403         | Illegal access to a resource is attempted.    |
| `IllegalAccessError`                | `Forbidden`        | 403         | Illegal access to a resource occurs.          |
| `NotDirectoryException`             | `BadRequest`       | 400         | The specified path is not a directory.        |
| `IllegalArgumentException`          | `BadRequest`       | 400         | An invalid argument is provided.              |
| `java.io.FileNotFoundException`     | `NotFound`         | 404         | The specified file or resource is not found.  |
| `java.net.ConnectException`         | `ServiceUnavailable` | 503     | Unable to connect to a service.               |
| `java.net.SocketTimeoutException`   | `GatewayTimeout`   | 504         | Connection or read operation timed out.       |
| Others (unrecognized throwable)    | `InternalServerError` | 500 | An unexpected error occurred.                 |

Another low-level method for error handling is `Response.fromCause` which creates a response from a `Cause`:

```scala
object Response {
  def fromCause(cause: Cause[Any]): Response = ???
}
```

This constructor is similar to `Response.fromThrowable`, but it also captures the interruption of the fiber. If the provided `Cause` is a failure due to interruption, the status code of the response will be `Status.RequestTimeout`.

We can use `Response.fromCause` in combination with the `Handler#mapErrorCause`, `Route#handleErrorCause`, and `Routes#handleErrorCause` methods. These methods take a function that maps the `Cause[Err] => Err` and return a `Handler`, `Route` or a `Routes` with the error handling logic applied:

```scala mdoc
import zio.http._
import java.io.IOException

val failedHandler = Handler.fail(new IOException())

failedHandler.mapErrorCause(Response.fromCause)
```

:::note
In many cases, it is more convenient to use the `sandbox` method to automatically convert all failures into a corresponding `Response`. But in some cases, to have more granular control over the error handling, we may want to use `Response.fromCause` and `Response.fromThrowable` directly.
:::

:::info
The `Cause` is a data structure that represents the result of a failed computation in ZIO. To learn more about `Cause`, see the [Cause](https://zio.dev/reference/core/cause) page on the ZIO core documentation.
:::

### Specialized Response Operators

`status` to update the `status` of `Response`

```scal mdoca
Response.text("Hello World!").status(Status.NOT_FOUND)
```

`updateHeaders` to update the `headers` of `Response`

```scala mdoc
Response.ok.updateHeaders(_ => Headers("key", "value"))
```

### Response from HTTP Errors

`error` creates a response with a provided status code and message.

```scala mdoc
 Response.error(Status.BadRequest, "It's not good!")
```

## Adding Cookie to Response

`addCookie` adds cookies in the headers of the response.

```scala mdoc
val cookie = Cookie.Response("key", "value")
Response.ok.addCookie(cookie)
```
