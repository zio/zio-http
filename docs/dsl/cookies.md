---
id: cookies
title: Cookies
---

**ZIO HTTP** has special support for Cookie headers using the `Cookie` Domain to add and invalidate cookies. Adding a
cookie will generate the correct [Set-Cookie](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie)
headers

## Create a Request Cookie

A request cookie consists of `name` and `content` and can be created with `Cookie.Request`:

```scala mdoc
import zio._
import zio.http._

val cookie: Cookie = Cookie.Request("id", "abc")
```

### Updating a request cookie

- `content` updates the content of cookie

```scala mdoc
cookie.content("def")
```

- `name` updates the name of cookie

```scala mdoc
cookie.name("id2")
```

## Create a Response Cookie

A Response `Cookie` can be created with
params `name`, `content`, `expires`, `domain`, `path`, `isSecure`, `isHttpOnly`, `maxAge`, `sameSite` and `secret`
according to HTTP [Set-Cookie](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie)

The below snippet creates a response cookie from the above request cookie:

```scala mdoc
val responseCookie = cookie.toResponse
```

### Update a Response Cookie

`Cookie.Response` is a case class so it can be updated by its `copy` method: 

- `maxAge` updates the max-age of the cookie

```scala mdoc
responseCookie.copy(maxAge = Some(5.days))
```

- `domain` updates the host to which the cookie will be sent

```scala mdoc
responseCookie.copy(domain = Some("example.com"))
```

- `path` updates the path of the cookie

```scala mdoc
responseCookie.copy(path = Some(Root / "cookie"))
```

- `isSecure` enables cookie only on https server

```scala mdoc
responseCookie.copy(isSecure = true)
```

- `isHttpOnly` forbids JavaScript from accessing the cookie

```scala mdoc
responseCookie.copy(isHttpOnly = true)
```

- `sameSite` updates whether or not a cookie is sent with cross-origin requests

```scala mdoc
responseCookie.copy(sameSite = Some(Cookie.SameSite.Strict))
```

## Sign a Cookie

The cookies can be signed with a signature:

- Using `sign`
  To sign a cookie, you can use `sign`

```scala mdoc
val cookie2 = Cookie.Response("key", "hello", maxAge = Some(5.days))
val app = 
  Routes(
    Method.GET / "cookie" -> handler(Response.ok.addCookie(cookie2.sign("secret")))
  ).toHttpApp
```

- Using `signCookies` middleware

To sign all the cookies in your routes, you can use `signCookies` middleware:

```scala mdoc
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

## Adding Cookie in Response

The cookies can be added in `Response` headers:

```scala mdoc
val res = Response.ok.addCookie(responseCookie)
```

It updates the response header `Set-Cookie` as ```Set-Cookie: <cookie-name>=<cookie-value>```

## Getting Cookie from Request

In HTTP requests, cookies are stored in the `cookie` header. `cookiesDecoded` can be used to get all the cookies in the request:

```scala mdoc
 private val app3 = 
  Routes(
    Method.GET / "cookie" -> handler { (req: Request) =>
      Response.text(req.header(Header.Cookie).map(_.value.toChunk).getOrElse(Chunk.empty).mkString(""))
    }
  )
```
