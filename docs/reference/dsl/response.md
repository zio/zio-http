---
id: response
title: Response
---

**ZIO HTTP** `Response` is designed to encode HTTP Response.
It supports all HTTP status codes and headers along with custom methods and headers (as defined in [RFC2616](https://datatracker.ietf.org/doc/html/rfc2616) )

## Creating a Response

`Response` can be created with `status`, `headers` and `data`.  

The below snippet creates a response with default params, `status` as `Status.OK`, `headers` as `Headers.empty` and `data` as `Body.Empty`:

```scala mdoc
import zio.http._

Response()
```

### Empty Response

**`Response.ok`** creates an empty response with status code 200:

```scala mdoc
Response.ok
```

**`Response.status`** creates an empty response with provided status code:

```scala mdoc
Response.status(Status.Continue)
```

### `Response.text`

Creates a response with data as text, content-type header set to text/plain and status code 200:

```scala mdoc
Response.text("hey")
```

### `Response.json`

Creates a response with data as json, content-type header set to application/json and status code 200:

```scala mdoc
Response.json("""{"greetings": "Hello World!"}""")
```

### `Response.html`

Creates a response with data as html, content-type header set to text/html and status code 200:

```scala mdoc
import zio.http.template._

Response.html(Html.fromString("html text"))
```

### `Response.error`

Creates a response with a provided status code and message:

```scala mdoc
Response.error(Status.BadRequest, "It's not good!")
```

## Operators

### Updating Response Status

`Response#status` updates the `status` of `Response`:

```scal mdoc
Response.text("Hello World!").status(Status.NOT_FOUND)
```

### Updating Response Headers

`Response#updateHeaders` updates the `headers` of `Response`:

```scala mdoc
Response.ok.updateHeaders(_ => Headers("key", "value"))
```

### Adding Cookie to Response

**`Response#addCookie`** adds cookies in the headers of the response:

```scala mdoc
val cookie = Cookie.Response("key", "value")
Response.ok.addCookie(cookie)
```