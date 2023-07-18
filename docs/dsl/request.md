---
id: request
title: Request
---
 
**ZIO HTTP** `Request` is designed in the simplest way possible to decode HTTP Request into a ZIO HTTP request.
 It supports all HTTP request methods (as defined in [RFC2616](https://datatracker.ietf.org/doc/html/rfc2616) ) and headers along with custom methods and headers.
 
## Creating a Request

`Request` can be created with `method`, `url`, `headers`, `remoteAddress` and `data`. 

Creating requests using `Request` is useful while writing unit tests.

The below snippet creates a request with default params, `headers` as `Headers.empty`, `data` as `Body.Empty`, `remoteAddress` as `None`:

```scala mdoc
import zio.http._
import zio._

Request(method = Method.GET, url = URL(Root))
```

## Accessing the Request

- `body` to access the content of request as a `Body`
- `headers` to get all the headers in the Request
- `method` to access request method
- `url` to access request URL
- `remoteAddress` to access request's remote address if available
- `version` to access the HTTP version

## Creating and reading a Request with query params

Query params can be added in the request using `url` in `Request`, `URL` stores query params as `Map[String, List[String]]`.

The below snippet creates a request with query params: `?q=a&q=b&q=c` 
```scala mdoc
Request.get(url = URL(Root, queryParams = QueryParams("q" -> Chunk("a","b","c"))))
```

`url.queryParams` can be used to read query params from the request
