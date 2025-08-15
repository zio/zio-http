# Routing

ZIO HTTP routing does some things differently than other (Scala) HTTP libraries.
This document explains the differences and the reasons behind them.

## Declarative routing

ZIO HTTP uses a declarative routing DSL. This means that a data structures describe the routing logic.
This is in contrast to other libraries that often use functions to describe routing logic. In the Scala world usually partial functions.

The main advantage of a declarative routing is the ability to inspect the routes at runtime. This gives ZIO HTTP the ability to generate not only a lookup tree but also generate documentation.
Partial functions are opaque and can't be inspected at runtime. A request must therefore in the worst case traverse all routes to find the correct one.
A service with 1000 routes build on partial functions would need to check all 1000 routes just to generate a 404 response.
ZIO HTTPs tree based lookup can immediately tell if a route is not present just by inspecting the first segment of the path.


## Type-safe routing
Path parameters are typed in ZIO HTTP. So a segment that is a variable must have a type. If a request does not match the type the route is not considered a match.
ZIO HTTP will then reject the request automatically. The user defined handler will not be called.

```scala mdoc:compile-only
import zio.http._
val routes = Routes(
  Method.GET / "hello" / string("name") -> handler { (name: String, _: Request) =>
    Response.text(s"Hello $name")
  }
)
```

For more details and code examples see the [routing pattern documentation](./../reference/routing/route_pattern.md).

## Query Parameters are not part of the routing
Query parameters are not part of the routing. They are part of the request handling.



