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

## Named and typed path variables

Captured path segments can now carry both a name and a type. That means a route can say not just "this segment is an `Int`", but also "this is the `page` segment".

```scala mdoc:compile-only
import zio.http._
import zio.http.RouteBinding._
import zio.blocks.endpoint.PathCodec._
import zio.blocks.endpoint.RoutePattern.{MethodSyntax, RoutePatternOps}

val route = Method.GET / int("page") -> handler((page: Int) => Response.text(s"page $page"))
```

The handler parameter name must match the path variable name, and the type must match too. If both line up, ZIO HTTP binds the segment directly to that parameter.

### Matching rules

When a route is paired with `handler(...)`, parameter binding follows this order:

1. Match a path variable by exact name and type.
2. If there is no path-variable match, look for a value in `Context` by type.
3. Then bind the built-in `Request` and `Scope` parameters by type.
4. If nothing matches, the code fails to compile.

That means this works even though `basketId` is not a path variable at all. It is pulled from `Context` by type, while `customerId` still comes from the path.

```scala mdoc:compile-only
import java.util.UUID
import zio.http._
import zio.http.RouteBinding._
import zio.blocks.endpoint.PathCodec._
import zio.blocks.endpoint.RoutePattern.{MethodSyntax, RoutePatternOps}

final case class BasketId(value: String)

val route = Method.GET / uuid("customerId") -> handler(
  (customerId: UUID, basketId: BasketId) => Response.text(s"customer=$customerId basket=$basketId")
)
```

Handler parameter order does not matter. The same route can bind the same values in any order, and unused path variables produce a compiler warning.

```scala mdoc:compile-only
import zio.http._
import zio.http.RouteBinding._
import zio.blocks.endpoint.PathCodec._
import zio.blocks.endpoint.RoutePattern.{MethodSyntax, RoutePatternOps}

val route = Method.GET / int("userId") / string("postId") -> handler(
  (postId: String, userId: Int) => Response.text(s"post=$postId user=$userId")
)

// warning: Variable postId:String was defined in the path but is never used
val partial = Method.GET / int("userId") / string("postId") -> handler(
  (userId: Int) => Response.text(s"user=$userId")
)
```

The warning is informational only. It does not block compilation.

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

Path-variable metadata stays attached to the pattern through composition too, so a prefixed pattern can still be bound later with `pattern -> handler(...)`.

```scala mdoc:compile-only
import zio.http._
import zio.http.RouteBinding._
import zio.blocks.endpoint.PathCodec._
import zio.blocks.endpoint.RoutePattern.{MethodSyntax, RoutePatternOps}

val userPrefix  = Method.GET / "users" / int("userId")
val fullPattern = userPrefix / "posts" / string("postId")

val route = fullPattern -> handler((userId: Int, postId: String) => Response.text(s"u=$userId p=$postId"))
```

Middleware still lives at the `Routes(...) @@ mw` level. This syntax does not introduce route-level middleware.

```scala mdoc:compile-only
import zio.http._
import zio.http.RouteBinding._
import zio.blocks.endpoint.PathCodec._
import zio.blocks.endpoint.RoutePattern.{MethodSyntax, RoutePatternOps}

val routes = Routes(
  Method.GET / int("id") -> handler((id: Int) => Response.text(s"user $id")),
  Method.POST / "logout" -> handler(() => Response.ok),
) @@ Middleware.identity
```

## Matching Methods

The `Method` data type represent an HTTP method, and it offers the following predefined HTTP methods: `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `DELETE`, `TRACE`, `OPTIONS`, `HEAD`, `TRACE` and `CONNECT`.

The `Method.ANY` is a shortcut to create routes for all default http methods.
