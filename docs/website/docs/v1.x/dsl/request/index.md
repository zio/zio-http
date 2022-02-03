# Request
 
## Server-side

**ZIO HTTP** `Request` is designed in the simplest way possible to, decode HTTP `Request` into a ZIO HTTP request.
 It supports all HTTP request methods (as defined in [RFC2616](https://datatracker.ietf.org/doc/html/rfc2616) ) and headers along with custom methods and headers.
 
## Creating a Request

`Request` can be created with `method`, `url`, `headers`, `remoteAddress` and `data`. 
Creating requests using `Request` is useful while writing unit tests.

The below snippet creates a request with default params, `method` as `Method.GET`, `url` as `URL.root`, `headers` as `Headers.empty`, `data` as `HttpData.Empty`, `remoteAddress` as `None`
```scala
val request: Request = Request()
```

## Using Request in creating HTTP apps

You can create an HTTP app which accepts an input of type `Request` and produces output of type `Response`:
 ```scala
 val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
    case Method.GET -> !! / "json" => Response.json("""{"greetings": "Hello World!"}""")
  }
```

## Accessing the Request

- `getBody` to access the content of request as a Chunk of Bytes
```scala
  val app = Http.collectZIO[Request] { case req => req.getBody.as(Response.ok) }
``` 
- `getBodyAsString` to access the content of request as string
```scala
  val app = Http.collectZIO[Request] { case req => req.getBodyAsString.as(Response.ok) }
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

### Creating and reading a Request with query params

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
