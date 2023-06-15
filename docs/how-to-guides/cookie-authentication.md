---
id: cookie-authentication
title: "Cookie Authentication in ZIO Server"
---

This guide will demonstrate how to implement cookie-based authentication in a ZIO server-side application. You'll learn how to set and validate authentication cookies to secure your endpoints.

## Code

```scala
package example

import zio._

import zio.http._

/**
 * Example to make app using cookies
 */
object CookieServerSide extends ZIOAppDefault {

  // Setting cookies with an expiry of 5 days
  private val cookie = Cookie.Response("key", "value", maxAge = Some(5 days))
  val res            = Response.ok.addCookie(cookie)

  private val app = Http.collect[Request] {
    case Method.GET -> Root / "cookie" =>
      Response.ok.addCookie(cookie.copy(path = Some(Root / "cookie"), isHttpOnly = true))

    case Method.GET -> Root / "secure-cookie" =>
      Response.ok.addCookie(cookie.copy(isSecure = true, path = Some(Root / "secure-cookie")))

    case Method.GET -> Root / "cookie" / "remove" =>
      res.addCookie(Cookie.clear("key"))
  }

  // Run it like any simple app
  val run =
    Server.serve(app).provide(Server.default)
}
```

## Explaination 

- It imports necessary dependencies from the ZIO and ZIO HTTP modules.

- It defines an object called `CookieServerSide` that extends the `ZIOAppDefault` trait, which provides a default implementation for running the ZIO application.

- It creates a `Cookie.Response` object named `cookie` with a key-value pair and an expiry of 5 days.

- It creates a `Response` object named `res` with an `ok` status and adds the cookie to it.

- It defines a `Http.collect` function that takes a `Request` object and pattern matches on different routes.

- For the route `GET /cookie`, it returns a response with an added cookie using the `cookie` object, specifying the path and setting the `isHttpOnly` flag to `true`.

- For the route `GET /secure-cookie`, it returns a response with an added cookie using the `cookie` object, setting the `isSecure` flag to `true` and specifying the path.

- For the route `GET /cookie/remove`, it returns a response with the res object, which contains the original `cookie` object with a cleared value for the "key" field.

- Finally, it defines a `run` value that runs the server by serving the `app` using the `Server.serve` method and providing a default server configuration `(Server.default)`.