---
id: binary_codecs
title: BinaryCodecs for Request/Response Bodies
sidebar_label: BinaryCodecs
---

ZIO HTTP has built-in support for encoding and decoding request/response bodies. This is achieved using generating codecs for our custom data types powered by [ZIO Schema](https://zio.dev/zio-schema).

ZIO Schema is a library for defining the schema for any custom data type, including case classes, sealed traits, and enumerations, other than the built-in types. It provides a way to derive codecs for these custom data types, for encoding and decoding data to/from JSON, Protobuf, Avro, and other formats.

Having codecs for our custom data types allows us to easily serialize/deserialize data to/from request/response bodies in our HTTP applications.

The `Body` data type in ZIO HTTP represents the body message of a request or a response. It has two main functionality for encoding and decoding request/response bodies, both of which require an implicit `BinaryCodec` for the corresponding data type:

* **`Body#to[A]`** — It decodes the request body to a custom data of type `A` using the implicit `BinaryCodec` for `A`.
* **`Body.from[A]`** — It encodes custom data of type `A` to a response body using the implicit `BinaryCodec` for `A`.

```scala
trait Body {
  def to[A](implicit codec: BinaryCodec[A]): Task[A] =
}

object Body {
  def from[A](a: A)(implicit codec: BinaryCodec[A]): Body = ???
}
```

To use these two methods, we need to have an implicit `BinaryCodec` for our custom data type, `A`. Let's assume we have a `Book` case class with `title`, `author`, and `year` fields:

```scala mdoc:silent
case class Book(title: String, author: String, year: Int)
```

To create a `BinaryCodec[Book]` for our `Book` case class, we can implement the `BinaryCodec` interface:

```scala mdoc:compile-only
import zio._ 
import zio.stream._
import zio.schema.codec._

implicit val bookBinaryCodec = new BinaryCodec[Book] {
  override def encode(value: Book): Chunk[Byte] = ???
  override def streamEncoder: ZPipeline[Any, Nothing, Book, Byte] = ???
  override def decode(whole: Chunk[Byte]): Either[DecodeError, Book] = ???
  override def streamDecoder: ZPipeline[Any, DecodeError, Byte, Book] = ???
} 
```

Now, when we call `Body.from(Book("Zionomicon", "John De Goes", 2021"))`, it will encode the `Book` case class to a response body using the implicit `BinaryCodec[Book]`. But, what happens if we add a new field to the `Book` case class, or change one of the existing fields? We would need to update the `BinaryCodec[Book]` implementation to reflect these changes. Also, if we want to support body response bodies with multiple book objects, we would need to implement a new codec for `List[Book]`. So, maintaining these codecs can be cumbersome and error-prone.

ZIO Schema simplifies this process by providing a way to derive codecs for our custom data types. For each custom data type, `A`, if we write/derive a `Schema[A]` using ZIO Schema, then we can derive a `BinaryCodec[A]` for any format supported by ZIO Schema, including JSON, Protobuf, Avro, and Thrift.

So, let's generate a `Schema[Book]` for our `Book` case class:

```scala mdoc:compile-only
import zio.schema._

object Book {
  implicit val schema: Schema[Book] = DeriveSchema.gen[Book]
}
```

Based on what format we want, we can add one of the following codecs to our `build.sbt` file:

```scala
libraryDependencies += "dev.zio" %% "zio-schema-json"     % "@ZIO_SCHEMA_VERSION@"
libraryDependencies += "dev.zio" %% "zio-schema-protobuf" % "@ZIO_SCHEMA_VERSION@"
libraryDependencies += "dev.zio" %% "zio-schema-avro"     % "@ZIO_SCHEMA_VERSION@"
libraryDependencies += "dev.zio" %% "zio-schema-thrift"   % "@ZIO_SCHEMA_VERSION@"
```

After adding the required codec's dependency, we can import the right binary codec inside the `zio.schema.codec` package:

| Codecs   | Schema Based BinaryCodec (`zio.schema.codec` package)              | Output         |
|----------|--------------------------------------------------------------------|----------------|
| JSON     | `JsonCodec.schemaBasedBinaryCodec[A](implicit schema: Schema[A])`  | BinaryCodec[A] |
| Protobuf | `ProtobufCodec.protobufCodec[A](implicit schema: Schema[A])`       | BinaryCodec[A] | 
| Avro     | `AvroCodec.schemaBasedBinaryCodec[A](implicit schema: Schema[A])`  | BinaryCodec[A] |
| Thrift   | `ThriftCodec.thriftBinaryCodec[A](implicit schema: Schema[A])`     | BinaryCodec[A] |
| MsgPack  | `MessagePackCodec.messagePackCodec[A](implicit schema: Schema[A])` | BinaryCodec[A] |

That is very simple! To have a `BinaryCodec` of type `A` we only need to derive a `Schema[A]` and then use an appropriate codec from the `zio.schema.codec` package.

## JSON Codec Example

### JSON Serialization of Response Body

Assume want to write an HTTP API that returns a list of books in JSON format:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/codecs/ResponseBodyJsonSerializationExample.scala")
```

### JSON Deserialization of Request Body

In the example below, we have an HTTP API that accepts a JSON request body containing a `Book` object and adds it to a list of books:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/codecs/RequestBodyJsonDeserializationExample.scala")
```

To send a POST request to the `/books` endpoint with a JSON body containing a `Book` object, we can use the following `curl` command:

```shell
$ curl -X POST -d '{"title": "Zionomicon", "authors": ["John De Goes", "Adam Fraser"]}' http://localhost:8080/books
```

After sending the POST request, we can retrieve the list of books by sending a GET request to the `/books` endpoint:

```shell
$ curl http://localhost:8080/books
```
