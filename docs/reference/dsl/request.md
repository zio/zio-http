---
id: request
title: Request
---
 
**ZIO HTTP** `Request` is designed in the simplest way possible to decode HTTP Request into a ZIO HTTP request. It supports all HTTP request methods (as defined in [RFC2616](https://datatracker.ietf.org/doc/html/rfc2616) ) and headers along with custom methods and headers.

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

Request(method = Method.GET, url = URL(Root))
```

There are also some helper methods to create requests for different HTTP methods inside the `Request`'s companion object: `delete`, `get`, `head`, `options`, `patch`, `post`, `put`.

We can access the request's details using the below fields:

- `method` to access request method
- `headers` to get all the headers in the Request
- `body` to access the content of request as a `Body`
- `url` to access request URL
- `remoteAddress` to access request's remote address if available
- `version` to access the HTTP version

:::note
Please note that usually, we don't create requests manually, and creating requests is useful while writing unit tests.
:::

## Request with Query Params

Query params can be added in the request using `url` in `Request`, `URL` stores query params as `Map[String, List[String]]`.

The below snippet creates a request with query params: `?q=a&q=b&q=c` 

```scala mdoc:compile-only
import zio._
import zio.http._

Request.get(url = URL(Root, queryParams = QueryParams("q" -> Chunk("a","b","c"))))
```

The `Request#url.queryParams` can be used to read query params from the request.
