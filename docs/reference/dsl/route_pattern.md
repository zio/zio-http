---
id: route_pattern
title: RoutePattern
---

`RoutePattern` defines a pattern for matching routes by examining both the HTTP method and the path. In addition to specifying a method, patterns contain segment patterns, which can consist of literals, integers, longs, and other segment types.

A `RoutePattern` is composed of a `Method` and a `PathCodec`:

```scala
case class RoutePattern[A](method: Method, pathCodec: PathCodec[A])
```

To create a `Route` we need to create a `RoutePattern` and then bind it to a handler using the `->` operator:

```scala mdoc:silent
import zio.http._

Routes(
  Method.GET / "health-check" -> Handler.ok,
)
```

In the above example, `Method.Get / "health-check"` represents a `RoutePattern` used to match incoming requests with the appropriate path and method.

## Building RoutePatterns

Typically, the entry point for creating a route pattern is `Method`:

```scala mdoc:compile-only
// GET /users
val pattern: RoutePattern[Unit] =
  Method.GET / "users"   
```

To match a path segment, various methods like `string`, `int`, `long`, and `uuid` are available.

For example, let's enhance the previous example to match a user id of type `Int`:

```scala mdoc:compile-only
// GET /users/:user-id
val pattern2: RoutePattern[Int] =
  Method.GET / "users" / int("user-id")
```

The type of the `RoutePattern` becomes `Int` because it matches a path segment of type `Int`.

Multiple path segments can be matched by combining multiple `PathCodec` values. Let's extend the example to match a post id of type `String`:


```scala mdoc:compile-only
// GET /users/:user-id/posts/:post-id
val pattern2: RoutePattern[(Int, String)] =
  Method.GET / "users" / int("user-id") / "posts" / string("post-id")
```

With more path segments, the type of the `RoutePattern` becomes a tuple of the types of the path segments, in this case, (Int, String).
