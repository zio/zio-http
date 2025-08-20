---
id: path_codec
title: PathCodec
---

`PathCodec[A]` represents a codec for paths of type `A`, comprising segments where each segment can be a literal, an integer, a long, a string, a UUID, or the trailing path.

The three basic operations that `PathCodec` supports are:

- **decode**: converting a path into a value of type `A`.
- **format**: converting a value of type `A` into a path.
- **++ or /**: combining two `PathCodec` values to create a new `PathCodec` that matches both paths, so the resulting of the decoding operation will be a tuple of the two values.

So we can think of `PathCodec` as the following simplified trait:

```scala
trait PathCodec[A] {
  def /[B](that: PathCodec[B]): PathCodec[(A, B)]

  def decode(path: Path): Either[String, A]
  def format(value: A): : Either[String, Path]
}
```

## Building PathCodecs

The `PathCodec` data type offers several predefined codecs for common types:

| PathCodec           | Description                         |
|---------------------|-------------------------------------|
| `PathCodec.bool`    | A codec for a boolean path segment. |
| `PathCodec.empty`   | A codec for an empty path.          |
| `PathCodec.literal` | A codec for a literal path segment. |
| `PathCodec.long`    | A codec for a long path segment.    |
| `PathCodec.string`  | A codec for a string path segment.  |
| `PathCodec.uuid`    | A codec for a UUID path segment.    |

Complex `PathCodecs` can be constructed by combining them using the `/` operator:

```scala mdoc:silent
import zio.http.codec.PathCodec
import PathCodec._

val pathCodec = empty / "users" / int("user-id") / "posts" / string("post-id")
```

By combining `PathCodec` values, the resulting `PathCodec` type reflects the types of the path segments it matches. In the provided example, the type of `pathCodec` is `(Int, String)` because it matches a path with two segments of type `Int` and `String`, respectively.

## Decoding and Formatting PathCodecs

To decode a path into a value of type `A`, we can use the `PathCodec#decode` method:

```scala mdoc
import zio.http._

pathCodec.decode(Path("users/123/posts/abc"))
```

To format (encode) a value of type `A` into a path, we can use the `PathCodec#format` method:

```scala mdoc
pathCodec.format((123, "abc"))
```

## Rendering PathCodecs

If we render the previous `PathCodec` to a string using `PathCodec#render` or `PathCodec#toString`, we get the following result:

```scala mdoc
pathCodec.render

pathCodec.toString
```

## Attaching Documentation to PathCodecs

The `PathCodec#??` operator, takes a `Doc` and annotate the `PathCodec` with it. It is useful for generating developer-friendly documentation for the API:

```scala mdoc
import zio.http.codec._

val users = PathCodec.literal("users") ?? (Doc.p("Managing users including CRUD operations"))
```

When generating OpenAPI documentation, these annotations will be used to generate the API documentation.

## Attaching Examples to PathCodecs

Similarly to attaching documentation, we can attach examples to `PathCodec` using the `PathCodec#example` operator:

```scala mdoc
import zio.http.codec._

val userId = PathCodec.int("user-id") ?? (Doc.p("The user id")) example ("user-id", 123)
```

## Using Value Objects with PathCodecs

Other than the common `PathCodec` constructors, it's also possible to transform a `PathCodec` into a more specific data type using the `transform` method.

This becomes particularly useful when adhering to domain-driven design principles and opting for value objects instead of primitive types:

```scala mdoc:compile-only
import zio.http.codec.PathCodec
import PathCodec._

case class UserId private(value: Int)

object UserId {
  def apply(value: Int): UserId =
    if (value > 0) 
      new UserId(value)
    else 
      throw new IllegalArgumentException("User id must be positive")
}


val userIdPathCodec: PathCodec[UserId] = int("user-id").transform(UserId.apply)(_.value)
```

This approach enables us to use the `UserId` value object in our routes, and the `PathCodec` will take care of the conversion between the path segment and the value object.

In the previous example, instead of throwing an exception, we can model the failure using the `Either` data type and then use the `transformOrFailLeft` to create a `PathCodec`:

```scala mdoc:compile-only
import zio.http.codec.PathCodec
import PathCodec._

case class UserId private(value: Int)
object UserId {
  def apply(value: Int): Either[String, UserId] =
    if (value > 0) 
      Right(new UserId(value))
    else 
      Left("User id must be positive")
}

val userIdPathCodec: PathCodec[UserId] = int("user-id").transformOrFailLeft(UserId.apply)(_.value)
```

Here is a list of the available transformation methods:

```scala
trait PathCodec[A] {
  def transform[A2](f: A => A2)(g: A2 => A): PathCodec[A2]
  def transformOrFail[A2](f: A => Either[String, A2])(g: A2 => Either[String, A]): PathCodec[A2]
  def transformOrFailLeft[A2](f: A => Either[String, A2])(g: A2 => A): PathCodec[A2]
  def transformOrFailRight[A2](f: A => A2)(g: A2 => Either[String, A]): PathCodec[A2]
}
```

Here is a complete example:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.Cause.{Die, Stackless}
import zio.http.codec.PathCodec

object Main extends ZIOAppDefault {

  import zio.http.codec.PathCodec
  import PathCodec._

  case class UserId private (value: Int)

  object UserId {
    def apply(value: Int): Either[String, UserId] =
      if (value > 0)
        Right(new UserId(value))
      else
        Left("User id must be greater than zero")
  }

  val userId: PathCodec[UserId] = int("user-id").transformOrFailLeft(UserId.apply)(_.value)

  val routes: Routes[Any, Response] =
    Routes(
      Method.GET / "users" / userId ->
        Handler.fromFunctionHandler[(UserId, Request)] { case (userId: UserId, request: Request) =>
          Handler.text(userId.value.toString)
        },
    ).handleErrorCause { case Stackless(cause, _) =>
      cause match {
        case Die(value, _) =>
          if (value.getMessage == "User id must be greater than zero")
            Response.badRequest(value.getMessage)
          else
            Response.internalServerError
      }
    }

  def run = Server.serve(routes).provide(Server.default)
}
```

## Trailing Path Segments

Sometimes, there may be a need to match a path with a trailing segment, regardless of the number of segments it contains. This is where the trailing codec comes into play:

```scala mdoc:compile-only
import zio._
import zio.http._

object TrailingExample extends ZIOAppDefault {
  def staticFileHandler(path: Path): Handler[Any, Throwable, Request, Response] =
    for {
      file <- Handler.getResourceAsFile(path.encode)
      http <-
        if (file.isFile)
          Handler.fromFile(file)
        else
          Handler.notFound
    } yield http

  val routes =
    Routes(
      Method.GET / "static" / trailing ->
        Handler.fromFunctionHandler[(Path, Request)] { case (path: Path, _: Request) =>
          staticFileHandler(path).contramap[(Path, Request)](_._2)
        },
      ).sandbox @@ HandlerAspect.requestLogging()

  val run = Server.serve(routes).provide(Server.default)
}
```

In the provided example, if an incoming request matches the route pattern `GET /static/*`, the `trailing` codec will match the remaining path segments and bind them to the `Path` type. Therefore, a request to `/static/foo/bar/baz.txt` will match the route pattern, and the `Path` will be `foo/bar/baz.txt`.
