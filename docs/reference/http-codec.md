---
id: http-codec
title: "HttpCodec"
---

In ZIO HTTP when we work with HTTP requests and responses, we are not dealing with raw bytes but with structured data. This structured data is represented by the `Request` and `Response` types. But under the hood, these types are serialized and deserialized to and from raw bytes. This process is handled by HTTP Codecs. We can think of `HttpCodec` as a pair of functions both for encoding and decoding requests and responses:

```scala
sealed trait HttpCodec[-AtomTypes, Value] {
  final def decodeRequest(request: Request)(implicit trace: Trace): Task[Value]
  final def decodeResponse(response: Response)(implicit trace: Trace): Task[Value]

  final def encodeRequest(value: Value): Request
  final def encodeResponse[Z](value: Value, outputTypes: Chunk[MediaTypeWithQFactor]): Response
}
```

HTTP messages consist of various parts, such as headers, body, and status codes. ZIO HTTP needs to know how to encode and decode each part of the HTTP message. So it has a set of built-in codecs that each one is responsible for a specific part of the HTTP message.

## Built-in Codecs

ZIO HTTP provides a set of built-in codecs for common HTTP message parts. Here is a list of built-in codecs:

```scala
type ContentCodec[A] = HttpCodec[HttpCodecType.Content, A]
type HeaderCodec[A]  = HttpCodec[HttpCodecType.Header, A]
type MethodCodec[A]  = HttpCodec[HttpCodecType.Method, A]
type QueryCodec[A]   = HttpCodec[HttpCodecType.Query, A]
type StatusCodec[A]  = HttpCodec[HttpCodecType.Status, A]
```
These codecs are nothing different from the `HttpCodec` type we saw earlier. They are just specialized versions of `HttpCodec` for specific parts of the HTTP message.

### ContentCodec

The `ContentCodec[A]` is a codec for the body of the HTTP message with type `A`. To create a `ContentCodec` we can use the `HttpCodec.content` method. If we want to have codec for a stream of content we can use `HttpCodec.contentStream` or `HttpCodec.binaryStream`:

```scala mdoc:compile-only
import zio.http._
import zio.http.codec._

val stringCodec           : ContentCodec[String] = HttpCodec.content[String]
val contentTypedCodec     : ContentCodec[String] = HttpCodec.content[String](MediaType.text.plain)
val namedContentCodec     : ContentCodec[Int]    = HttpCodec.content[Int](name = "age")
val namedContentTypedCodec: ContentCodec[Int]    = HttpCodec.content[Int](name = "age", MediaType.text.plain)
```

HttpCodecs are composable, we can use `++` to combine two codecs:

```scala
val nameAndAgeCodec: ContentCodec[(String, Int)] = HttpCodec.content[String]("name") ++ HttpCodec.content[Int]("age")
```

We can also `transform` a codec to another codec. In the following example, we transform the previous codec, which is a codec for a tuple of `(String, Int)`, to a codec for a case class `User`:

```scala
val userContentCodec: ContentCodec[User] =
  nameAndAgeCodec.transform[User] {
    case (name: String, age: Int) => User(name, age)
  }(user => (user.name, user.age))
```

More details about [transforming codecs](#transforming-codecs) will be discussed later in this page.

Another simple way to create a `ContentCodec` for a case class is to use ZIO Schema. By using ZIO Schema we can derive a schema for a case class and then use it to create a `ContentCodec`:

```scala mdoc:compile-only
import zio.http.codec._
import zio.schema._

case class User(name: String, age: Int)

object User {
  implicit val schema = DeriveSchema.gen[User]
}

val userCodec: ContentCodec[User] = HttpCodec.content[User]
```

To create a codec for a stream of content we can use `HttpCodec.contentStream`:

```scala mdoc:compile-only
import zio.stream._
import zio.http.codec._

val temperature: ContentCodec[ZStream[Any, Nothing, Double]] = 
  HttpCodec.contentStream[Double](name = "temperature")
```

To create a codec for a binary stream we can use `HttpCodec.binaryStream`:

```scala mdoc:compile-only
import zio.stream._
import zio.http.codec._

val binaryStream: ContentCodec[ZStream[Any, Nothing, Byte]] = 
  HttpCodec.binaryStream(name = "large-file")
```

### HeaderCodec

The `HeaderCodec[A]` is a codec for the headers of the HTTP message with type `A`. To create a `HeaderCodec` we can use the `HttpCodec.header` constructor:

```scala mdoc:compile-only
import zio.http._
import zio.http.codec._

val acceptHeaderCodec: HeaderCodec[Header.Accept] = HttpCodec.header(Header.Accept)
```

Or we can use the `HttpCodec.name`, which takes the name of the header as a parameter, which is useful for custom headers:

```scala mdoc:compile-only
import zio.http._
import zio.http.codec._
import java.util.UUID

val acceptHeaderCodec: HeaderCodec[UUID] = HttpCodec.name[UUID]("X-Correlation-ID")
```

We can also create a codec that encode/decode multiple headers by combining them with `++`:

```scala mdoc:compile-only
import zio.http._
import zio.http.codec._

val acceptHeaderCodec     : HeaderCodec[Header.Accept]      = HttpCodec.header(Header.Accept)
val contentTypeHeaderCodec: HeaderCodec[Header.ContentType] = HttpCodec.header(Header.ContentType)

val acceptAndContentTypeCodec: HeaderCodec[(Header.Accept, Header.ContentType)] = 
  acceptHeaderCodec ++ contentTypeHeaderCodec
```

### MethodCodec

The `MethodCodec[A]` is a codec for the method of the HTTP message with type `A`. We can use `HttpCodec.method` which takes a `Method` as a parameter to create a `MethodCodec`:

```scala mdoc:compile-only
import zio.http._
import zio.http.codec._

val getMethodCodec: HttpCodec[HttpCodecType.Method, Unit] = HttpCodec.method(Method.GET)
```

There are also predefined codecs for all the HTTP methods, e.g. `HttpCodec.connect`, `HttpCodec.delete`, `HttpCodec.get`, `HttpCodec.head`, `HttpCodec.options`, `HttpCodec.patch`, `HttpCodec.post`, `HttpCodec.put`, `HttpCodec.trace`.

### QueryCodec

The `QueryCodec[A]` is a codec for the query parameters of the HTTP message with type `A`. To be able to encode and decode query parameters, ZIO HTTP provides a wide range of query codecs. If we are dealing with a single query parameter we can use `HttpCodec.query`, `HttpCodec.query[Boolean]`, `HttpCodec.query[Boolean]`, and `HttpCodec.queryTo`:

```scala mdoc:compile-only
import zio.http._
import zio.http.codec._
import java.util.UUID

val nameQueryCodec  : QueryCodec[String]         = HttpCodec.query[String]("name")       // e.g. ?name=John
val ageQueryCodec   : QueryCodec[Int]            = HttpCodec.query[Int]("age")     // e.g. ?age=30 
val activeQueryCodec: QueryCodec[Boolean]        = HttpCodec.query[Boolean]("active") // e.g. ?active=true

// e.g. ?uuid=43abea9e-0b0e-11ef-8d07-e755ec5cd767
val uuidQueryCodec  : QueryCodec[UUID]           = HttpCodec.query[UUID]("uuid") 
```

We can combine multiple query codecs with `++`:


If we have multiple query parameters we can use `HttpCodec.queryAll`, `HttpCodec.queryAllBool`, `HttpCodec.queryAllInt`, and `HttpCodec.queryAllTo`:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.codec._
import java.util.UUID

val queryAllCodec    : QueryCodec[Chunk[String]] = HttpCodec.query[Chunk[String]]("q")      // e.g. ?q=one&q=two&q=three
val queryAllIntCodec : QueryCodec[Chunk[Int]]    = HttpCodec.query[Chunk[Int]]("id")  // e.g. ?ids=1&ids=2&ids=3

// e.g. ?uuid=43abea9e-0b0e-11ef-8d07-e755ec5cd767&uuid=43abea9e-0b0e-11ef-8d07-e755ec5cd768
val queryAllUUIDCodec: QueryCodec[Chunk[UUID]]   = HttpCodec.query[Chunk[UUID]]("uuid") 
```

### StatusCodec

The `StatusCodec[A]` is a codec for the status code of the HTTP message with type `A`. To create a `StatusCodec` we can use the `HttpCodec.status` method:

```scala mdoc:compile-only
import zio.http._
import zio.http.codec._

val okStatusCodec: StatusCodec[Unit] = HttpCodec.status(Status.Ok)
```

Also, there are predefined codecs for various status codes, e.g. `HttpCodec.Continue`, `HttpCodec.Accepted`, `HttpCodec.NotFound`, etc.

## Operations

The primary advantage of `HttpCodec` is its composability, which means we can combine multiple codecs to create new ones. This is useful when we want to encode and decode multiple parts of the HTTP message, such as headers, body, and status codes; so we start by creating codecs for each part and then combine them to create a codec for the whole HTTP message.

### Combining Codecs Sequentially

By combining two codecs using the `++` operator, we can create a new codec that sequentially encodes/decodes from left to right:

```scala mdoc:compile-only
import zio.http.codec._

// e.g. ?name=John&age=30
val queryCodec: QueryCodec[(String, Int)]  = HttpCodec.query[String]("name") ++ HttpCodec.query[Int]("age")
```

### Combining Codecs Alternatively

There is also a `|` operator that allows us to create a codec that can decode either of the two codecs. Assume we have two query codecs, one for `q` and the other for `query`. We can create a new codec that tries to decode `q` first and if it fails, it tries to decode `query`:

```scala mdoc:silent
import zio.http.codec._

val eitherQueryCodec: QueryCodec[Either[Boolean, String]] = HttpCodec.query[Boolean]("q") | HttpCodec.query[String]("query")
```

Assume we have a request

```scala mdoc:silent
import zio.http._

val request: Request = Request(url = URL.root.copy(queryParams = QueryParams("query" -> "foo")))
```

We can decode the query parameter using the `decodeRequest` method:

```scala mdoc:silent
import zio._

val result: Task[Either[Boolean, String]] = eitherQueryCodec.decodeRequest(request)
```

#### Scala 3 Union Type Syntax
For Scala 3 the `||` operator is available will return a union type instead of an `Either`.

```scala
import zio.http.codec._

val unionQueryCodec: QueryCodec[Boolean | String] = HttpCodec.query[Boolean]("q") || HttpCodec.query[String]("query")
```

```scala mdoc:invisible:reset
```

### Optional Codecs

Sometimes we want to decode a part of the HTTP message only if it exists. We can use the `optional` method to transform a codec to an optional codec:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.codec._

val optionalQueryCodec: QueryCodec[Option[String]] = HttpCodec.query[String]("q").optional

val request = Request(url = URL.root.copy(queryParams = QueryParams("query" -> "foo")))
val result: Task[Option[String]] = optionalQueryCodec.decodeRequest(request)
```

### Expecting a Specific Value

To write a codec that only accepts a specific value, we can use the `expect` method:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.codec._

val expectHeaderValueCodec: HeaderCodec[Unit] = HttpCodec.name[String]("X-Custom-Header").expect("specific-value")
val request: Request = Request(headers = Headers("X-Custom-Header" -> "specific-value"))
val response: Task[Unit] = expectHeaderValueCodec.decodeRequest(request)
```

The above codec will only accept the request if the value of the header `X-Custom-Header` is `specific-value`.

### Transforming Codecs

HttpCodecs are invariant in their `Value` type parameter, so to transform a codec of type `A` to a codec of type `B`, we need two functions, one for mapping `A` to `B` and the other for mapping `B` to `A`.

For example, assume we have a codec of type `HttpCodec[HttpCodecType.Content, (String, Int)]`. If we want to transform it to a codec of type `HttpCodec[HttpCodecType.Content, User]`, we require two functions:
- A function that maps a value of type `(String, Int)` to a value of type `User`.
- A function that maps a value of type `User` to a value of type `(String, Int)`.

```scala mdoc:compile-only
import zio._
import zio.http.codec._

case class User(name: String, age: Int)

val nameAndAgeCodec: ContentCodec[(String, Int)] = HttpCodec.content[String]("name") ++ HttpCodec.content[Int]("age")

val userContentCodec: ContentCodec[User] =
  nameAndAgeCodec.transform[User] {
    case (name: String, age: Int) => User(name, age)
  }(user => (user.name, user.age))
```

### Annotating Codecs

HttpCodec has several methods for annotating codecs:
- `annotate`: To attach a metadata to the codec.
- `named`: To attach a name to the codec.
- `examples`: To attach examples to the codec.
- `??`: To attach a documentation to the codec.

This additional information can be used for [generating API documentation, e.g. OpenAPI](endpoint.md#openapi-documentation).

## Usage

Having a codec for HTTP messages is useful when we want to program declaratively instead of imperative programming.

Let's compare these two programming styles in ZIO HTTP and see how we can benefit from using `HttpCodec` for writing declarative APIs.

### Imperative Programming

When writing an HTTP API, we have to think about a function that takes a `Request` and returns a `Response`, i.e. the handler function. In imperative programming, we have to deal with the low-level details of how to extract the required information from the `Request`, validate it, and finally construct the proper `Response`. In such a way, we have to write all these logics step by step.

In the following example, we are going to write an API for a bookstore. The API has a single endpoint `/books?id=<book-id>` that returns the book with the given `id` as a query parameter. If the book is found, it returns a `200 OK` response with the book as the body. If the book is not found, it returns a `404 Not Found` response with an error message:

```scala mdoc:passthrough
import utils._
printSource("zio-http-example/src/main/scala/example/endpoint/style/ImperativeProgrammingExample.scala")
```

The type of handler in the above example is `Handler[Any, Response, Request, Response]`, which means we have to write a function that takes a `Request` and returns a `Response` and in case of failure, it will return a failure value of type `Response`. In the handler function, we have to manually extract the `id` from the query parameters, then do the business logic to find the book with the given `id`, and finally construct the proper `Response`. 

### Declarative Programming

In declarative programming, we can separate the two concerns from each other: the definition of the API and its implementation. By having the codecs for the HTTP messages, we can define how the `Request` and `Response` should look like and based on our requirements how they should be encoded and decoded. ZIO Http has the `Endpoint` API that makes it easy to define the API in a declarative way by utilizing `HttpCodec`. After defining the API using `Endpoint`, we can implement it using the `Endpoint#implement` method.

In the following example, we are going to rewrite the previous example using the `Endpoint` API:

```scala mdoc:passthrough
import utils._
printSource("zio-http-example/src/main/scala/example/endpoint/style/DeclarativeProgrammingExample.scala")
```

As we will see, we have declared a clear specification of the API and separately implemented it. The very interesting point about the implementation section is that it is not concerned with the low-level details of how to extract the required information from the `Request` and how to construct the proper `Response`. The `implement` method takes a handler of type `Handler[Any, NotFoundError, String, Book]`, which means we have to write a handler function that takes a `String` and returns a `Book` and in case of failure, it will return a `NotFoundError` error. No manual decoding of `Request` and no manual encoding of `Response` is required. So in the handler function, we only have to focus on the business logic.
