# Request
 
## Server-side

`Request` is designed in **ZIO HTTP** in the simplest way possible to make HTTP calls.
 It supports all HTTP request methods (as defined in [RFC2616](https://datatracker.ietf.org/doc/html/rfc2616) ) and headers along with custom methods and headers.
 
## Creating a Request

`Request` can be created with `method`, `url`, `headers`, `remoteAddress` and `data`.

The below snippet creates a request with default params, `method` as `Method.GET`, `url` as `URL.root`, `headers` as `Headers.empty`, `data` as `HttpData.Empty`, `remoteAddress` as `None`
```scala
val request: Request = Request()
```

## Using Request in creating HTTP apps

You can create an HTTP app which accepts a request of type `Request` and produces response of type `Response`:
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

## Client Side
`ClientRequest` is designed to create requests for `Client`.

## Creating a ClientRequest

`ClientRequest` can be created with `method`, `url`, `getHeaders` and `data`.

The below snippet shows how to create `ClientRequest` using default params
```scala
val request = Client.ClientRequest(Method.GET, URL(!!))
```
## Accessing the ClientRequest

- `getBodyAsString` to access the content of request as `Option[String]`
```scala
  val request = Client.ClientRequest(Method.GET, URL(!!))
  val content: Option[String] = request.getBodyAsString
```
- `remoteAddress` to access the remoteAddress of request as `Option[InetAddress]`
```scala
  val request = Client.ClientRequest(Method.GET, URL(!!))
  val address: Option[InetAddress] = request.remoteAddress
```
- `updateHeaders` to update headers of request
```scala
  val request = Client.ClientRequest(Method.GET, URL(!!))
  val updatedRequest = request.updateHeaders(_.addHeader("name", "value"))
```

