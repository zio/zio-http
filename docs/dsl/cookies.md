---
id: cookies
title: Cookies
---

**ZIO HTTP** has special support for Cookie headers using the `Cookie` Domain to add and invalidate cookies. Adding a cookie will generate the correct [Set-Cookie](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie) headers

## Create a Cookie

`Cookie` can be created with params `name`, `content`, `expires`, `domain`, `path`, `isSecure`, `isHttpOnly`, `maxAge`, `sameSite` and `secret` according to HTTP [Set-Cookie](https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie)  

The below snippet creates a cookie `name` as `id` and `content` as `abc` with default params:

```scala
 val cookie: Cookie = Cookie("id", "abc")
```

### Update a Cookie

- `withContent` updates the content of cookie

```scala
 val newCookie = cookie.withContent("def")
```

- `withExpiry` updates the expiration date of cookie

```scala
 val newCookie = cookie.withExpiry(Instant.MAX)
```

- `withMaxAge` updates the max-age of the cookie

```scala
 val newCookie = cookie.withMaxAge(5 days)
```

- `withDomain` updates the host to which the cookie will be sent

```scala
 val newCookie = cookie.withDomain("example.com")
```

- `withPath` updates the path of the cookie

```scala
 val newCookie = cookie.withPath(!! / "cookie")
```

- `withSecure` enables cookie only on https server 

```scala
 val newCookie = cookie.withSecure
```

- `withHttpOnly` forbids JavaScript from accessing the cookie

```scala
 val newCookie = cookie.withHttpOnly
```

- `withSameSite` updates whether or not a cookie is sent with cross-origin requests

```scala
 val newCookie = cookie.withSameSite(Instant.MAX)
```

## Reset a Cookie

you can reset cookie params using:
- `withoutSecure` resets `isSecure` to `false` in cookie
- `withoutHttpOnly` resets `isHttpOnly` to `false` in cookie
- `withoutExpiry` resets `expires` to `None`
- `withoutDomain` resets `domain` to `None`
- `withoutPath` resets `path` to `None`
- `withoutMaxAge` resets `maxAge` to `None`
- `withoutSameSite` resets `sameSite` to `None`

## Sign a Cookie

The cookies can be signed with a signature:
 
 - Using `sign`
 To sign a cookie, you can use `sign`

```scala
 val cookie = Cookie("key", "hello").withMaxAge(5 days)
 val app = Http.collect[Request] { case Method.GET -> !! / "cookie" =>
    Response.ok.addCookie(cookie.sign("secret"))
  }
```

- Using `signCookies` middleware

To sign all the cookies in your `HttpApp`, you can use `signCookies` middleware:

```scala
  private val cookie = Cookie("key", "hello").withMaxAge(5 days)
  private val app = Http.collect[Request] {
    case Method.GET -> !! / "cookie" => Response.ok.addCookie(cookie)
    case Method.GET -> !! / "secure-cookie" => Response.ok.addCookie(cookie.withSecure)
  }

  // Run it like any simple app
  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app @@ signCookies("secret")).exitCode
``` 

## Adding Cookie in Response

The cookies can be added in `Response` headers:

```scala
 val cookie1: Cookie = Cookie("id", "abc")
 val res = Response.ok.addCookie(cookie1)
```

It updates the response header `Set-Cookie` as ```Set-Cookie: <cookie-name>=<cookie-value>```

## Getting Cookie from Request

In HTTP requests, cookies are stored in the `cookie` header. `cookiesDecoded` can be used to get all the cookies in the request:

```scala
 private val app = Http.collect[Request] {
    case req @  Method.GET -> !! / "cookie" =>
      Response.text(req.cookiesDecoded.mkString(""))
  }
```
