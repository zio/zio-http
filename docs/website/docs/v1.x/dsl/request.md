---
sidebar_position: "3"
---
# Request
 
**ZIO HTTP** `Request` is designed in the simplest way possible to decode HTTP Request into a ZIO HTTP request.
 It supports all HTTP request methods (as defined in [RFC2616](https://datatracker.ietf.org/doc/html/rfc2616) ) and headers along with custom methods and headers.
 
## Creating a Request

`Request` can be created with `method`, `url`, `headers`, `remoteAddress` and `data`. 
Creating requests using `Request` is useful while writing unit tests.

The below snippet creates a request with default params, `method` as `Method.GET`, `url` as `URL.root`, `headers` as `Headers.empty`, `data` as `Body.Empty`, `remoteAddress` as `None`
```scala
val request: Request = Request()
```

## Matching and Extracting Requests

`Request` can be extracted into an HTTP Method and Path via `->`. On the left side is the `Method`, and on the right side, the `Path`.

```scala
Method.GET -> !! / "text"
```
### Method
 `Method` represents HTTP methods like POST, GET, PUT, PATCH, and DELETE.
You can create existing HTTP methods such as `Method.GET`, `Method.POST` etc or create a custom one.
 

### Path
 `Path` can be created using
  - `!!` which represents the root
  - `/` which represents the path delimiter and starts the extraction from the left-hand side of the expression
  - `/:` which represents the path delimiter and starts the extraction from the right-hand side of the expression and can match paths partially 

The below snippet creates an `HttpApp` that accepts an input of type `Request` and output of type `Response` with two paths.
According to the request path, it will respond with the corresponding response:
- if the request has path `/name` it will match the first route.
- if the request has path `/name/joe/wilson` it will match the second route as `/:` matches the path partially as well.  

```scala
  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
     case Method.GET -> !! / a => Response.text(s"$a")
     case Method.GET -> "" /: "name" /: a => Response.text(s"$a")
   }
```

## Accessing the Request

- `getBody` to access the content of request as a Chunk[Byte]
```scala
  val app = Http.collectZIO[Request] { case req => req.getBody.as(Response.ok) }
``` 
- `getBodyAsString` to access the content of request as string
```scala
  val app = Http.collectZIO[Request] { case req => req.bodyAsString.as(Response.ok) }
``` 
- `getHeaders` to get all the headers in the Request
```scala
  val app = Http.collect[Request] { case req => Response.text(req.getHeaders.toList.mkString("")) }
```
- `method` to access request method
```scala
val app = Http.collect[Request] { case req => Response.text(req.method.toString())}
```
- `path` to access request path
```scala
  val app = Http.collect[Request] { case req => Response.text(req.path.toString())}
```
- `remoteAddress` to access request's remote address if available
```scala
  val app = Http.collect[Request] { case req => Response.text(req.remoteAddress.toString())}
```
- `url` to access the complete url
```scala
  val app = Http.collect[Request] { case req => Response.text(req.url.toString())}
```

## Creating and reading a Request with query params

Query params can be added in the request using `url` in `Request`, `URL` stores query params as `Map[String, List[String]]`.

The below snippet creates a request with query params: `?q=a&q=b&q=c` 
```scala
      val request: Request = Request(url = URL(!!, queryParams = Map("q" -> List("a","b","c"))))
```

`url.queryParams` can be used to read query params from the request

The below snippet shows how to read query params from request
```scala
  val app = Http.collect[Request] { case req => Response.text(req.url.queryParams.mkString(""))}
```
