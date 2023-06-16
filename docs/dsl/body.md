---
id: body
title: Body
---

`Body` is a domain to model content for `Request`, `Response` and `ClientRequest`. ZIO HTTP uses Netty at it's core and Netty handles content as `ByteBuf`. `Body` helps you decode and encode this content into simpler, easier to use data types while creating a Request or Response.

## Server-side usage of `Body`

On the server-side, `ZIO-HTTP` models content in `Request` and `Response` as `Body` with `Body.Empty` as the default value. To add content while creating a `Response` you can use the `Response` constructor:

```scala mdoc:silent
  import zio._
  import zio.http._
  import zio.stream._

  val res: Response = Response( body = Body.fromString("Some String"))
```

To add content while creating a `Request` for unit tests, you can use the `Request` constructor:

```scala mdoc:silent
  val req: Request = Request.post(URL(Root / "save"), Body.fromString("Some String"))
```

## Client-side usage of `Body`

On the client-side, `ZIO-HTTP` models content in `ClientRequest` as `Body` with `Body.Empty` as the default value.

To add content while making a request using ZIO HTTP you can use the `Client.request` method:

```scala mdoc:silent
  val actual: ZIO[Client with Scope, Throwable, Response] = 
    Client.request(Request.post("https://localhost:8073/success", Body.fromString("Some string")))
```

## Creating a Body

### Creating a Body from a `String`

To create an `Body` that encodes a String you can use `Body.fromString`:

```scala mdoc:silent
  val textHttpData: Body = Body.fromString("any string", Charsets.Http)
```

### Creating a Body from `Chunk of Bytes`

To create an `Body` that encodes chunk of bytes you can use `Body.fromChunk`:

```scala mdoc:silent
  val chunkHttpData: Body = Body.fromChunk(Chunk.fromArray("Some Sting".getBytes(Charsets.Http)))
```

### Creating a Body from a `Stream`

To create an `Body` that encodes a Stream you can use `Body.fromStream`.

- Using a Stream of Bytes

```scala mdoc:silent
  val streamHttpData1: Body = Body.fromStream(ZStream.fromChunk(Chunk.fromArray("Some String".getBytes(Charsets.Http))))
```

- Using a Stream of String

```scala mdoc:silent
  val streamHttpData2: Body = Body.fromStream(ZStream("a", "b", "c"), Charsets.Http)
```

### Creating a Body from a `File`

To create an `Body` that encodes a File you can use `Body.fromFile`:

```scala mdoc:silent:crash
  val fileHttpData: Body = Body.fromFile(new java.io.File(getClass.getResource("/fileName.txt").getPath))
```
