---
sidebar_position: "4"
---
# Response

**ZIO HTTP** `Response` is designed to encode HTTP Response.
It supports all HTTP status codes and headers along with custom methods and headers (as defined in [RFC2616](https://datatracker.ietf.org/doc/html/rfc2616) )

## Creating a Response

`Response` can be created with `status`, `headers` and `data`.  

The below snippet creates a response with default params, `status` as `Status.OK`, `headers` as `Headers.empty` and `data` as `Body.Empty`.
```scala
 val res: Response = Response()
```
### Empty Response

- `ok` creates an empty response with status code 200
```scala
 val res: Response = Response.ok
```

- `status` creates an empty response with provided status code.
```scala
 val res: Response = Response.status(Status.CONTINUE)
```

### Specialized Response Constructors

- `text` creates a response with data as text, content-type header set to text/plain and status code 200 
```scala
 val res: Response = Response.text("hey")
```
- `json` creates a response with data as json, content-type header set to application/json and status code 200 
```scala
 val res: Response = Response.json("""{"greetings": "Hello World!"}""")
```
- `html` creates a response with data as html, content-type header set to text/html and status code 200
```scala
 val res: Response = Response.html(Html.fromString("html text"))
```

### Specialized Response Operators

- `setStatus` to update the `status` of `Response`

```scala
val res: Response = Response.text("Hello World!").setStatus(Status.NOT_FOUND)
```

- `updateHeaders` to update the `headers` of `Response`

```scala
 val res: Response = Response.ok.updateHeaders(_ => Headers("key", "value"))
```
### Response from HttpError

`fromHttpError` creates a response with provided `HttpError`
```scala
 val res: Response = Response.fromHttpError(HttpError.BadRequest())
```

## Adding Cookie to Response

`addCookie` adds cookies in the headers of the response.
```scala
 val cookie = Cookie("key", "value")
 val res = Response.ok.addCookie(cookie)
```