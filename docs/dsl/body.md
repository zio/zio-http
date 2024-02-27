---
id: body
title: Body
---

`Body` is a domain to model content for `Request` and `Response`. ZIO HTTP uses Netty at its core and Netty handles content as `ByteBuf`. `Body` helps you decode and encode this content into simpler, easier-to-use data types while creating a Request or Response.

## Usages

The `Body` is used on both the server and client side.

### Server-side

On the server side, `ZIO-HTTP` models content in `Request` and `Response` as `Body` with `Body.empty` as the default value. To add content while creating a `Response` you can use the `Response` constructor:

```scala mdoc:compile-only
import zio._
import zio.http._

object HelloExample extends ZIOAppDefault {
  val app: HttpApp[Any] =
    Routes(
      Method.GET / "hello" ->
        handler { req: Request =>
          for {
            name <- req.body.asString
          } yield Response(body = Body.fromString(s"Hello $name!"))
        }.sandbox,
    ).toHttpApp

  override val run = Server.serve(app).provide(Server.default)
}
```

### Client-side

On the client side, `ZIO-HTTP` models content in `Client` as `Body` with `Body.Empty` as the default value.

To add content while making a request using ZIO HTTP you can use the `Client.request` method:

```scala mdoc:silent
import zio._
import zio.stream._
import zio.http._

object HelloClientExample extends ZIOAppDefault {
  val app: ZIO[Client & Scope, Throwable, Unit] =
    for {
      name <- Console.readLine("What is your name? ")
      resp <- Client.request(Request.post("http://localhost:8080/hello", Body.fromString(name)))
      body <- resp.body.asString
      _    <- Console.printLine(s"Response: $body")
    } yield ()

  def run = app.provide(Client.default, Scope.default)
}
```

In the above example, we are making a `POST` request to the `/hello` endpoint with a `Body` containing the name of the user. Then we read the response body as a `String` and printed it:

```
What is your name? John
Response: Hello John!
```

## Creating a Body

### Empty Body

To create an empty body:

```scala mdoc:compile-only
val emptyBody: Body = Body.empty
```

### From a String and CharSequence

To create a `Body` that encodes a `String` or `CharSequence` we can use `Body.fromString` or `Body.fromCharSequence`:

```scala mdoc:silent
Body.fromString("any string", Charsets.Http)
Body.fromCharSequence("any string", Charsets.Http)
```

### From Array/Chunk of Bytes

To create a `Body` that encodes a chunk of bytes you can use `Body.fromChunk`:

```scala mdoc:silent
val chunkHttpData: Body = Body.fromChunk(Chunk.fromArray("Some String".getBytes(Charsets.Http)))
val byteArrayHttpData: Body = Body.fromArray("Some String".getBytes(Charsets.Http))
```

### From a Value with ZIO Schema Binary Codec

We can construct a body from an arbitrary value using zio-schema's binary codec:

```scala
object Body {
  def from[A](a: A)(implicit codec: BinaryCodec[A], trace: Trace): Body =
    fromChunk(codec.encode(a))
}
```

For example, if you have a case class Person:

```scala mdoc:compile-only
import zio.schema.DeriveSchema
import zio.schema.codec.JsonCodec.schemaBasedBinaryCodec

case class Person(name: String, age: Int)
implicit val schema = DeriveSchema.gen[Person]

val person = Person("John", 42)
val body = Body.from(person)
```

In the above example, we used a JSON codec to encode the person object into a body. Similarly, we can use other codecs like Avro, Protobuf, etc.

### From ZIO Streams

There are several ways to create a `Body` from a ZIO Stream:

#### From Stream of Bytes

To create a `Body` that encodes a stream of bytes, we can utilize the `Body.fromStream` and `Body.fromStreamChunked` constructors:

```scala
object Body {
  def fromStream(
    stream: ZStream[Any, Throwable, Byte],
    contentLength: Long
  ): Body = ???

  def fromStreamChunked(
    stream: ZStream[Any, Throwable, Byte]
  ): Body = ???
}
```

If we know the content length of the stream, we can use `Body.fromStream`. It will set the `content-length` header in the response to the given value:

```scala mdoc:silent
val chunk = Chunk.fromArray("Some String".getBytes(Charsets.Http))
val streamHttpData1: Body = Body.fromStream(ZStream.fromChunk(chunk), contentLength = chunk.length)
```

Otherwise, we can use `Body.fromStreamChunked`, which is useful for streams with an unknown content length. Assume we have a service that generates a response to a request in chunks; we can stream the response to the client while we don't know the exact length of the response. Therefore, the `transfer-encoding` header will be set to `chunked` in the response:


#### From Stream of Values with ZIO Schema Binary Codec

To create a `Body` that encodes a stream of values of type `A`, we can use `Body.fromStream` with a `BinaryCodec`:

```scala
object Body {
  def fromStream[A](stream: ZStream[Any, Throwable, A])(implicit codec: BinaryCodec[A], trace: Trace): Body = ???
}
```

Let's create a `Body` from a stream of `Person`:

```scala mdoc:compile-only
import zio.schema.DeriveSchema
import zio.schema.codec.JsonCodec.schemaBasedBinaryCodec

case class Person(name: String, age: Int)
implicit val schema = DeriveSchema.gen[Person]

val persons: ZStream[Any, Nothing, Person] =
  ZStream.fromChunk(Chunk(Person("John", 42), Person("Jane", 40)))

val body = Body.fromStream(persons)
```

The header `transfer-encoding` will be set to `chunked` in the response.

#### From Stream of CharSequence

To create a `Body` that encodes a stream of `CharSequence`, we can use `Body.fromCharSequenceStream` and `Body.fromCharSequenceStreamChunked` constructors.

If we know the content length of the stream, we can use `Body.fromCharSequenceStream`, which will set the `content-length` header in the response to the given value. Otherwise, we can use `Body.fromCharSequenceStreamChunked`, which is useful for streams with an unknown content length. In this case, the `transfer-encoding` header will be set to `chunked` in the response.

### From a File

To create an `Body` that encodes a `File` we can use `Body.fromFile`:

```scala mdoc:silent:crash
val fileHttpData: ZIO[Any, Nothing, Body] = 
  Body.fromFile(new java.io.File(getClass.getResource("/fileName.txt").getPath))
```

### From WebSocketApp

Any `WebSocketApp[Any]` can be converted to a `Body` using `Body.fromWebSocketApp`:

```scala
object Body {
  def fromSocketApp(app: WebSocketApp[Any]): WebsocketBody = ???
}
```
