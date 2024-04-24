---
id: request
title: Request
---

**ZIO HTTP** `Request` is designed in the simplest way possible to decode an HTTP Request into a ZIO HTTP request. It supports all HTTP request methods (as defined in [RFC2616](https://datatracker.ietf.org/doc/html/rfc2616) ) and headers along with custom methods and headers.

## Accessing Incoming Request

To access the incoming request, we can use a `Handler` which takes a `Request` as input and returns a `Response`:

```scala mdoc:compile-only
import zio._
import zio.http._

Routes(
  Method.POST / "echo" ->
    handler { (req: Request) => 
      req.body.asString(Charsets.Utf8).map(Response.text(_)).sandbox 
    }
)
```

To learn more about handlers, please refer to the [Handler](./handler.md) section.

## Creating a Request

The default constructor of `Request` takes the following parameters as input: `version`, `method`, `url`, `headers`, `body`, `remoteAddress`:

```scala
final case class Request(
  version: Version = Version.Default,
  method: Method = Method.ANY,
  url: URL = URL.empty,
  headers: Headers = Headers.empty,
  body: Body = Body.empty,
  remoteAddress: Option[InetAddress] = None,
) extends HeaderOps[Request]
```

The below snippet creates a request with default params, `headers` as `Headers.empty`, `data` as `Body.Empty`, `remoteAddress` as `None`:

```scala mdoc
import zio.http._

Request(method = Method.GET, url = URL(Path.root))
```

There are also some helper methods to create requests for different HTTP methods inside the `Request`'s companion object: `delete`, `get`, `head`, `options`, `patch`, `post`, and `put`.

We can access the request's details using the below fields:

- `method` to access request method
- `headers` to get all the headers in the Request
- `body` to access the content of the request as a `Body`
- `url` to access request URL
- `remoteAddress` to access the request's remote address if available
- `version` to access the HTTP version

:::note
Please note that usually, we don't create requests on the server-side. Creating requests is useful while writing unit tests or when we call other services using the ZIO HTTP Client.
:::

## Request with Query Params

Query params can be added in the request using `url` in `Request`, `URL` stores query params as `Map[String, List[String]]`.

The below snippet creates a request with query params: `?q=a&q=b&q=c`

```scala mdoc:compile-only
import zio._
import zio.http._

Request.get(url = URL(Path.root, queryParams = QueryParams("q" -> Chunk("a","b","c"))))
```

The `Request#url.queryParams` can be used to read query params from the request.

## Operations

### Leading/Trailing Slash

The `Request` class provides the following methods to add or drop leading/trailing slashes from the URL:

- `addLeadingSlash`
- `addTrailingSlash`
- `dropLeadingSlash`
- `dropTrailingSlash`

### Patching Requests

To patch a request, we can use the `patch` method, which takes a `Request.Patch` as input:

```scala mdoc
import zio._
import zio.http._

Request
  .get("http://localhost:8080/users")
  .patch(
    Request.Patch(
      addHeaders = Headers(Header.ContentType(MediaType.application.`json`)),
      addQueryParams = QueryParams("role" -> Chunk("reviewer", "editor"))
    )
  )
```

### Request Headers

There are several methods available to get, update, and remove headers from a `Request`:

1. To access headers, we can use the following methods:
    - `Request#header` to get a single header
    - `Request#headerOrFail` to get a single header or fail if it doesn't exist
    - `Request#headers` to get all headers
    - `Request#rawHeader` to get a single header as a string

2. To update headers, the `Request#updateHeaders` takes a `Headers => Headers` function as input and returns a new `Request` with updated headers.

3. To add headers, the `Request#addHeader` and `Request#addHeaders` methods are available.

4. To remove headers, the `Request#removeHeader` and `Request#removeHeaders` methods are available.

5. To set headers, the `Request#setHeaders` method is available.

### Request Body

There are several methods available to get, update, and remove body from a `Request`.

- The `Request#body` accesses the body of the request.
- The `Request#withBody` takes a `Body` as input and returns a new `Request` with the updated body.
- The `Request#updateBody` and `Request#updateBody` a `Body => Body` or `Body => ZIO[R, E, Body]` function as input and returns a new `Request` with the updated body.
- The `Request#collect` collects the streaming body of the request and returns a new `Request` with the collected body.
- The `Request#ignoreBody` consumes the streaming body fully and returns a new `Request` with an empty body.


### Retrieving Query Parameters

There are several methods available to access query parameters from a `Request`.

To get a single query parameter, we can use the `Request#queryParam` method that takes a `String` as the input key and returns an `Option[String]`:

```scala mdoc:compile-only
// curl -X GET https://localhost:8080/search?q=value -i
import zio._
import zio.http._

object QueryParamExample extends ZIOAppDefault {

  val app =
    Routes(
      Method.GET / "search" -> handler { (req: Request) =>
        val queries = req.queryParam("q")
        queries match {
          case Some(value) =>
            Response.text(s"Value of query param q is $value")
          case None        =>
            Response.badRequest(s"The q query parameter is missing!")
        }
      },
    ).toHttpApp

  def run = Server.serve(app).provide(Server.default)
}
```

The typed version of `Request#queryParam` is `Request#queryParamTo` which takes a key and a type parameter of type `T` and finally returns a `Either[QueryParamsError, T]` value:

```scala mdoc:compile-only
// curl -X GET https://localhost:8080/search?age=42 -i
import zio.http._
object TypedQueryParamExample extends ZIOAppDefault {
  val app =
    Routes(
      Method.GET / "search" -> Handler.fromFunctionHandler { (req: Request) =>
        val response: ZIO[Any, QueryParamsError, Response] =
          ZIO.fromEither(req.queryParamTo[Int]("age"))
             .map(value => Response.text(s"The value of age query param is: $value"))

        Handler.fromZIO(response).catchAll {
          case QueryParamsError.Missing(name)                  =>
            Handler.badRequest(s"The $name query param is missing")
          case QueryParamsError.Malformed(name, codec, values) =>
            Handler.badRequest(s"The value of $name query param is malformed")
        }
      },
    ).toHttpApp

  def run = Server.serve(app).provide(Server.default)
}
```

:::info
In the above example, instead of using `ZIO.fromEither(req.queryParamTo[Int]("age"))` we can use `req.queryParamToZIO[Int]("age")` to get a `ZIO` value directly which encodes the error type in the ZIO effect.
:::

To retrieve all query parameter values for a key, we can use the `Request#queryParams` method that takes a `String` as the input key and returns a `Chunk[String]`:

```scala mdoc:compile-only
// curl -X GET https://localhost:8080/search?q=value1&q=value2 -i

import zio._
import zio.http._

object QueryParamsExample extends ZIOAppDefault {
  val app =
    Routes(
      Method.GET / "search" -> handler { (req: Request) =>
        val queries = req.queryParams("q")
        if (queries.nonEmpty) {
          val text = queries.mkString("Here is the list of values for the q query param: [", ",", "]")
          Response.text(text)
        } else {
          Response.badRequest(s"The q query parameter is missing!")
        }
      },
    ).toHttpApp

  def run = Server.serve(app).provide(Server.default)
}
```

The typed version of `Request#queryParams` is `Request#queryParamsTo` which takes a key and a type parameter of type `T` and finally returns a `Either[QueryParamsError, Chunk[T]]` value.

:::note
All the above methods also have `OrElse` versions which take a default value as input and return the default value if the query parameter is not found, e.g. `Request#queryParamOrElse`, `Request#queryParamToOrElse`, `Request#queryParamsOrElse`, `Request#queryParamsToOrElse`.
:::

Using the `Request#queryParameters` method, we can access the query parameters of the request which returns a `QueryParams` object.

### Modifying Query Parameters

When we are working with ZIO HTTP‌ Client, we need to create a new `Request` and may need to set/update/remove query parameters. In such cases, we have the following methods available: `addQueryParam`, `addQueryParams`, `removeQueryParam`, `removeQueryParams`, `setQueryParams`, and `updateQueryParams`.

```scala mdoc:compile-only
import zio._
import zio.http._

object QueryParamClientExample extends ZIOAppDefault {
  def run =
    Client.request(
      Request
        .get("http://localhost:8080/search")
        .addQueryParam("language", "scala")
        .addQueryParam("q", "How to Write HTTP App")
        .addQueryParams("tag", Chunk("zio", "http", "scala")),
    ).provide(Client.default, Scope.default)
}
```

The above example sends a GET request to `http://localhost:8080/search?language=scala&q=How+to+Write+HTTP+App&tag=zio&tag=http&tag=scala`.

### Retrieving URL/Path

To access the URL of the request, we can utilize the `Request#url` method, which yields a `URL` object. For updating the URL of the request, we can use the `Request#updateURL` method, which takes a `URL => URL` function as input. This function allows us to update the URL and return a new `Request` object with the updated URL.

If we want to access the path of the request, we can use the `Request#path` method which returns a `Path` object. Also, we can use the `Request#path` method which takes a `Path` and returns a new `Request` with the updated path.

### Retrieving Cookies and Flashes

Cookies and Flashes

```scala mdoc:invisible
import zio._
import zio.http._

val request = Request(
  method = Method.GET,
  url = URL(Path.root),
  headers = Headers(
    Header.Cookie(
      NonEmptyChunk(
        Cookie.Request("key1", "value1"),
        Cookie.Request("key2", "value2")
      )
    )
  ),
)
```

To access all cookies in the request, we can use the `Request#cookies` method which returns a `Chunk[Cookie]`:

```scala mdoc
val cookies = request.cookies
```

To access a single cookie, we can use the `Request#cookie` method which takes the name of the cookie as input and returns an `Option[Cookie]`.

```scala mdoc
val cookie = request.cookie("key1")
```

To encode errors in the ZIO effect when a cookie is not found, we can use the `Request#cookieWithOrFail` method which takes three groups of parameters: name of the cookie, error message, and finally a function that takes a cookie and returns a `ZIO` effect:

```scala
trait Request {
  def cookieWithOrFail[R, E, A](name: String)(missingCookieError: E)(f: Cookie => ZIO[R, E, A]): ZIO[R, E, A]
}
```

Here is an example of using `Request#cookieWithOrFail`:

```scala mdoc:compile-only
case class CookieNotFound(cookie: String)

val key = "key3"
val effect: ZIO[Any, CookieNotFound, Cookie] = 
  request.cookieWithOrFail(key)(CookieNotFound(key))(c => ZIO.succeed(c))
```

Or simply use the `Request#cookieWithZIO` method which does the same but `Throwable` is used as the error type:

```scala mdoc:compile-only
val effect: ZIO[Any, Throwable, Cookie] = 
  request.cookieWithZIO("key3")(c => ZIO.succeed(c))
```

To get a flash message of type `A` with the given key, we can use the `Request#flash` method which takes a `Flash[A]` as input and returns an `Option[A]`:

```scala mdoc:compile-only
val flashValue = request.flash(Flash.get[Int]("key1"))
```

## Client-side Example

In the below example, we are creating a `Request` using the `Request.get` method and then calling the `Client.request` method to send the request to the servers:

```scala mdoc:compile-only
import zio._
import zio.http._

object ClientExample extends ZIOAppDefault {
  def run = Client
    .request(Request.get("http://localhost:8080/users/2"))
    .flatMap(_.body.asString)
    .debug("Response Body: ")
    .provide(Client.default, Scope.default)

}
```
