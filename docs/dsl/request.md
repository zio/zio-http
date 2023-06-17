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

## Matching and Extracting Requests

`Request` can be extracted into an HTTP Method and Path via `->`. On the left side is the `Method`, and on the right side, the `Path`.

```scala
Method.GET -> Root / "text"
```

### Method

`Method` represents HTTP methods like POST, GET, PUT, PATCH, and DELETE. You can create existing HTTP methods such as `Method.GET`, `Method.POST` etc or create a custom one.

### Path
 `Path` can be created using
  - `Root` which represents the root
  - `/` which represents the path delimiter and starts the extraction from the left-hand side of the expression
  - `/:` which represents the path delimiter and starts the extraction from the right-hand side of the expression and can match paths partially 

The below snippet creates an `HttpApp` that accepts an input of type `Request` and output of type `Response` with two paths.
According to the request path, it will respond with the corresponding response:
- if the request has path `/name` it will match the first route.
- if the request has path `/name/joe/wilson` it will match the second route as `/:` matches the path partially as well.  

```scala mdoc:silent
  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
     case Method.GET -> Root / a => Response.text(s"$a")
     case Method.GET -> "" /: "name" /: a => Response.text(s"$a")
   }
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
