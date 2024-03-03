---
id: cookies
title: Cookies
---

Cookies are small pieces of data that websites store on a user's browser. They are sent between the client (browser) and server in HTTP requests and responses. Cookies serve various purposes, including session management, user authentication, personalization, and tracking.

When a user visits a website, the server can send one or more cookies to the browser, which stores them locally. The browser then includes these cookies in subsequent requests to the same website, allowing the server to retrieve and utilize the stored information.

In ZIO HTTP, cookies are represented by the `Cookie` data type, which encompasses both request cookies and response cookies:

We can think of a `Cookie` as an immutable and type-safe representation of HTTP cookies that contains the name, content:

```scala
sealed trait Cookie {
  def name: String
  def content: String
}

object Cookie {
  case class Request(name: String, content: String) extends Cookie { self =>
    // Request Cookie methods
  }
  case class Response(
    name: String,
    content: String,
    domain: Option[String] = None,
    path: Option[Path] = None,
    isSecure: Boolean = false,
    isHttpOnly: Boolean = false,
    maxAge: Option[Duration] = None,
    sameSite: Option[SameSite] = None,
  ) extends Cookie { self =>
    // Response Cookie methods   
  }
}
```

Request cookies (`Cookie.Request`) are sent by the client to the server, while response cookies (`Cookie.Response`) are sent by the server to the client.

## Response Cookie

### Creating a Response Cookie

A Response `Cookie` can be created with params `name`, `content`, `expires`, `domain`, `path`, `isSecure`, `isHttpOnly`, `maxAge`, `sameSite` and `secret` according to HTTP [Set-Cookie](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie)

```scala mdoc
import zio._
import zio.http._

val responseCookie = Cookie.Response("user_id", "user123", maxAge = Some(5.days))
```

By adding the above cookie to a `Response`, it will add a `Set-Cookie` header with the respective cookie name and value and other optional attributes.

Let's write a simple example to see how it works:

```scala mdoc:compile-only
import zio.http._

object ResponseCookieExample extends ZIOAppDefault {
  val httpApp = Routes(
    Method.GET / "cookie" -> handler {
      Response.ok.addCookie(
        Cookie.Response(name = "user_id", content = "user123", maxAge = Some(5.days))
      )
    },
  ).toHttpApp

  def run = Server.serve(httpApp).provide(Server.default)
}
```

When we call the `/cookie` endpoint, it will return a response with a `Set-Cookie` header:

```
~> curl -X GET http://127.0.0.1:8080/cookie -i
HTTP/1.1 200 OK
set-cookie: user_id=user123; Max-Age=432000; Expires=Fri, 08 Mar 2024 10:41:52 GMT
content-length: 0
```

To convert a request cookie to a response cookie, use the `toResponse` method:

```scala mdoc:silent:nest
import zio.http._

val requestCookie = Cookie.Request("id", "abc")
val responseCookie = requestCookie.toResponse
```

### Updating a Response Cookie

`Cookie.Response` is a case class, so it can be updated by its `copy` method:

- `maxAge` updates the max-age of the cookie:

```scala mdoc:compile-only
responseCookie.copy(maxAge = Some(5.days))
```

- `domain` updates the host to which the cookie will be sent:

```scala mdoc:compile-only
responseCookie.copy(domain = Some("example.com"))
```

- `path` updates the path of the cookie:

```scala mdoc:compile-only
responseCookie.copy(path = Some(Root / "cookie"))
```

- `isSecure` enables cookie only on https server:

```scala mdoc:compile-only
responseCookie.copy(isSecure = true)
```

- `isHttpOnly` forbids JavaScript from accessing the cookie:

```scala mdoc:compile-only
responseCookie.copy(isHttpOnly = true)
```

- `sameSite` updates whether or not a cookie is sent with cross-origin requests:

```scala mdoc:compile-only
responseCookie.copy(sameSite = Some(Cookie.SameSite.Strict))
```

## Request Cookie

### Creating a Request Cookie

A request cookie consists of `name` and `content` and can be created with `Cookie.Request`:

```scala mdoc
val cookie: Cookie = Cookie.Request("user_id", "user123")
```

### Updating a Request Cookie

The `Cookie#name` method updates the name of cookie:

```scala mdoc
cookie.name("session_id")
```

The `Cookie#content` method updates the content of the cookie:

```scala mdoc
cookie.content("abc123xyz789")
```

## Signing a Cookie

The cookies can be signed with a signature.

- Using `Response#sign`:

```scala mdoc:silent
val cookie2 = Cookie.Response("key", "hello", maxAge = Some(5.days))
val app = 
  Routes(
    Method.GET / "cookie" -> handler(Response.ok.addCookie(cookie2.sign("secret")))
  ).toHttpApp
```

- Using `signCookies` middleware:

To sign all the cookies in your routes, you can use `signCookies` middleware:

```scala mdoc:compile-only
import Middleware.signCookies

private val app2 = Routes(
  Method.GET / "cookie" -> handler(Response.ok.addCookie(cookie2)),
  Method.GET / "secure-cookie" -> handler(Response.ok.addCookie(cookie2.copy(isSecure = true)))
).toHttpApp

// Run it like any simple app
def run(args: List[String]): ZIO[Any, Throwable, Nothing] =
  Server.serve(app2 @@ signCookies("secret"))
        .provide(Server.default)
``` 

## Adding Cookie in a Response

The cookies can be added in `Response` headers:

```scala mdoc:compile-only
val res = Response.ok.addCookie(responseCookie)
```

It updates the response header `Set-Cookie` as ```Set-Cookie: <cookie-name>=<cookie-value>```

## Getting Cookie from a Request

From HTTP requests, a single cookie can be retrieved with `cookie`:

```scala mdoc:compile-only
 private val app4 = 
  Routes(
    Method.GET / "cookie" -> handler { (req: Request) =>
      val cookieContent = req.cookie("sessionId").map(_.content)
      Response.text(s"cookie content: $cookieContent")
    }
  )
```

## Getting Cookie from a Header

In HTTP requests, cookies are stored in the `cookie` header:

```scala mdoc:compile-only
 private val app3 = 
  Routes(
    Method.GET / "cookie" -> handler { (req: Request) =>
      Response.text(req.header(Header.Cookie).map(_.value.toChunk).getOrElse(Chunk.empty).mkString(""))
    }
  )
```

## Examples

Here are some simple examples of using cookies in a ZIO HTTP application.

### Server Side Example

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/CookieServerSide.scala")
```

### Signed Cookies

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/SignCookies.scala")
```
