---
id: body
title: Body
---

`Body` is a domain to model content for `Request` and `Response`. The body can be a fixed chunk of bytes, a stream of bytes, or form data, or any type that can be encoded into such representations (such as textual data using some character encoding, the contents of files, JSON, etc.).

ZIO HTTP uses Netty at its core and Netty handles content as `ByteBuf`. `Body` helps you decode and encode this content into simpler, easier-to-use data types while creating a `Request` or `Response`.

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

### From a Multipart Form

Multipart form data is a method for encoding form data within an HTTP request. It allows for the transmission of multiple types of data, including text, files, and binary data, in a single request.

This makes it ideal for scenarios where form submissions require complex data structures, such as file uploads or rich form inputs.

#### Structure of a Multipart Form

A multipart form consists of multiple parts, each representing a different field or file to be transmitted. These parts are separated by a unique boundary string. Each part typically includes headers specifying metadata about the data being transmitted, such as content type and content disposition, followed by the actual data.

In ZIO HTTP, the `Form` data type is used to represent a form that can be either multipart or URL-encoded. It is a wrapper around `Chunk[FormField]`.

#### Creating Response Body from Multipart Form

The `Body.fromMultipartForm` is used to create a `Body` from a multipart form:

```scala
object Body {
  def fromMultipartForm(form: Form, specificBoundary: Boundary): Body = ???
}
```

Let say we create a body from a multipart form:

```scala mdoc:silent
val body = 
  Body.fromMultipartForm(
    Form(
      FormField.simpleField("key1", "value1"),
      FormField.binaryField(
        "file1",
        Chunk.fromArray("Hello, world!".getBytes),
        MediaType.text.`plain`,
        filename = Some("hello.txt"),
      ),
      FormField.binaryField(
        "file2",
        Chunk.fromArray("## Hello, world!".getBytes),
        MediaType.text.`markdown`,
        filename = Some("hello.md"),
      ),
    ),
    Boundary("boundary123"),
  )
```

This will create a `Body` which can be rendered as:

```scala mdoc:passthrough
val res =
  Unsafe.unsafe { 
    implicit unsafe =>
      zio.Runtime.default.unsafe.run(body.asString).getOrThrow()
  }
println(
  s"""|```
      |$res
      |```""".stripMargin,
)
```

:::note
When utilizing MultipartForm for the response body, ensure the correct Content-Type header is included in the response, such as `Content-Type: multipart/<proper-subtype>; boundary=boundary123`.
:::

:::note
Please be aware that utilizing a multipart form for the response body is uncommon and may not be supported by all clients. If you intend to use this method, ensure comprehensive support across various browsers.
:::

### From a URL-encoded Form

URL encoding is a technique used to convert data into a format that can be transmitted over the internet. This is necessary because URLs have certain restrictions on the characters they can contain. URL encoding replaces unsafe characters with a "%" followed by two hexadecimal digits. For example, a space is encoded as "%20", and special characters like "&" become "%26".

A URL-encoded form consists of key-value pairs, where each pair represents a form field and its corresponding value. These pairs are concatenated together into a query string, separated by "&" symbols.

For instance, consider a simple form with fields for "username" and "password". The URL-encoded form data looks like this:

```
username=john&password=secretpassword
```

Similar to `Body.fromMultipartForm`, the `Body.fromURLEncodedForm` is used to create a `Body` from a URL-encoded form:

```scala mdoc:nest:silent
val body = 
  Body.fromURLEncodedForm(
    Form(
      FormField.simpleField("username", "john"),
      FormField.simpleField("password", "secretpassword"),
    )
  )
```

This will create a `Body` which can be rendered as:

```scala mdoc:passthrough
val res =
  Unsafe.unsafe { 
    implicit unsafe =>
      zio.Runtime.default.unsafe.run(body.asString).getOrThrow()
  }
println(
  s"""|```
      |$res
      |```""".stripMargin,
)
```

:::note
URL encoding is primarily useful for encoding data in the query string of a URL or for encoding form data in HTTP requests. It is not typically used for the response body.
:::

## Body Operations

### Decoding Body Content as a String

We can decode the content of the body into a `String` using the `Body#asString` method. It allows decoding with both default and custom charsets:

```scala mdoc:compile-only
import java.nio.charset.Charset

val defaultCharsetString = body.asString
val customCharsetString = body.asString(Charset.forName("UTF-8"))
```

These methods return a `Task` representing the decoded string content of the body.

### Decoding Body Content

By providing a `BinaryCodec[A]` we can decode the body content to a value of type `A`:

```scala mdoc:compile-only
import zio.schema._
import zio.schema.codec.JsonCodec.schemaBasedBinaryCodec

case class Person(name: String, age: Int)

implicit val schema: Schema[Person] = DeriveSchema.gen[Person]

val person        = Person("John", 42)
val body          = Body.from(person)
val decodedPerson = body.to[Person]
```

### Retrieving Raw Body Content

We can access the content of the body as an array of bytes or a chunk of bytes. This is useful when dealing with binary data. Here's how you can do it:

```scala mdoc:compile-only
val byteArray: Task[Array[Byte]] = body.asArray
val byteChunk: Task[Chunk[Byte]] = body.asChunk
```

These methods return the body content as an array of bytes or a ZIO chunk of bytes, respectively.

### Retrieving Body Content as a ZIO Stream

We can access the content of the body as a ZIO stream of bytes:

```scala mdoc
val byteStream = body.asStream
```

### Decoding Multipart Form Data

We can decode the content of the body as multipart form data:

```scala mdoc:compile-only
val multipartFormData: Task[Form] = body.asMultipartForm
```

ZIO HTTP supports streaming, allowing us to handle large files using **multipart/form-data**. By utilizing `Body#asMultipartFormStream`, which gives us a `Task` of `StreamingForm`. Using the `StreamingForm#fields` method we can access a stream of `FormField` representing the form's parts:

```scala mdoc:compile-only
for {
  form  <- body.asMultipartFormStream
  count <- form.fields.flatMap {
    case FormField.Binary(name, data, contentType, transferEncoding, filename) => ???
    case FormField.StreamingBinary(name, contentType, transferEncoding, filename, data) => ???
    case FormField.Text(name, value, contentType, filename) => ???
    case FormField.Simple(name, value) => ???
  }.run(???)
} yield ()

```

Also, if there's sufficient memory available, we can execute `StreamingForm#collectAll` method gather all its parts into memory:

```scala mdoc:compile-only
val streamingForm: Task[StreamingForm] = body.asMultipartFormStream
val collectedForm: Task[Form] = streamingForm.flatMap(_.collectAll)
```
