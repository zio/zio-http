---
id: path_codec
title: PathCodec
---

`PathCodec[A]` represents a codec for paths of type `A`, comprising segments where each segment can be a literal, an integer, a long, a string, a UUID, or the trailing path.

The `PathCodec` data type offers several predefined codecs for common types:

- `PathCodec.bool` - A codec for a boolean path segment.
- `PathCodec.emtpy` - A codec for an empty path.
- `PathCodec.literal` - A codec for a literal path segment.
- `PathCodec.long` - A codec for a long path segment.
- `PathCodec.string` - A codec for a string path segment.
- `PathCodec.uuid` - A codec for a UUID path segment.

Complex `PathCodecs` can be constructed by combining them using the `/` operator:

```scala mdoc:compile-only
import zio.http.codec.PathCodec
import PathCodec._

val pathCodec = empty / "users" / int("user-id") / "posts" / string("post-id")
```

By combining `PathCodec` values, the resulting `PathCodec` type reflects the types of the path segments it matches. In the provided example, the type of `pathCodec` is `(Int, String)` because it matches a path with two segments of type `Int` and `String`, respectively.

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

  val app =
    Routes(
      Method.GET / "static" / trailing ->
        Handler.fromFunctionHandler[(Path, Request)] { case (path: Path, _: Request) =>
          staticFileHandler(path).contramap[(Path, Request)](_._2)
        },
    ).sandbox.toHttpApp @@ HandlerAspect.requestLogging()

  val run = Server.serve(app).provide(Server.default)
}
```

In the provided example, if an incoming request matches the route pattern `GET /static/*`, the `trailing` codec will match the remaining path segments and bind them to the `Path` type. Therefore, a request to `/static/foo/bar/baz.txt` will match the route pattern, and the `Path` will be `foo/bar/baz.txt`.
