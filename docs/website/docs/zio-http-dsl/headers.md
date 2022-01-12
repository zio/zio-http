# Headers

Headers API provides a ton of powerful operators that can be used to add, remove and modify headers.

`Headers`      - represents an immutable collection of headers i.e. essentially a `Chunk[(String, String)]`.

`HeaderNames`  - commonly use header names.

`HeaderValues` - commonly use header values

- Constructors:

```scala
import zhttp.http._

// create a simple Accept header:
val acceptHeader: Headers = Headers.accept(HeaderValues.applicationJson)

// create a basic authentication header:
val basicAuthHeader: Headers = Headers.basicAuthorizationHeader("username", "password")
```

- Getters:

```scala
import zhttp.http._

// retrieving the value of Accept header value:
val acceptHeader: Headers = Headers.accept(HeaderValues.applicationJson)
val acceptHeaderValue: Option[CharSequence] = acceptHeader.getAccept


// retrieving a bearer token from Authorization header:
val authorizationHeader: Headers                   = Headers.authorization("Bearer test")
val authorizationHeaderValue: Option[String]       = authorizationHeader.getBearerToken
```

- Modifiers:

```scala
import zhttp.http._

// add Accept header:
val headers = Headers.empty
val updatedHeadersList: Headers = headers.addHeaders(Headers.accept(HeaderValues.applicationJson))

// or if you prefer the builder pattern:

// add Host header:
val moreHeaders: Headers        = headers.withHost("zio-http.dream11.com")

```

- Checks:

```scala
import com.sun.net.httpserver.Headers

// check if Accept header is present
val contentTypeHeader: Headers = Headers.contentType(HeaderValues.applicationJson)
val isHeaderPresent: Boolean   = contentTypeHeader.hasHeader(HeaderNames.contentType)

val isJsonContentType: Boolean = contentTypeHeader.hasJsonContentType


```
