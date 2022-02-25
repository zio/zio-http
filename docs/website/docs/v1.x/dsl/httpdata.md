---
sidebar_position: "5"
---
# HttpData
`HttpData` is a domain to model content for `Request`, `Response` and `ClientRequest`. ZIO HTTP uses Netty at it's core and Netty handles content as `ByteBuf`. `HttpData` helps you decode and encode this content into simpler, easier to use data types while creating a Request or Response.
## Server-side usage of `HttpData`
On the server-side, `ZIO-HTTP` models content in `Request` and `Response` as `HttpData` with `HttpData.Empty` as the default value.
To add content while creating a `Response` you can use the `Response` constructor.
```scala
  val res: Response = Response( data = HttpData.fromString("Some String"))
```
To add content while creating a `Request` for unit tests, you can use the `Request` constructor.
```scala
  val req: Request = Request( data = HttpData.fromString("Some String"))
```
## Client-side usage of `HttpData`
On the client-side, `ZIO-HTTP` models content in `ClientRequest` as `HttpData` with `HttpData.Empty` as the default value.
To add content while making a request using ZIO HTTP you can use the `Client.request` method.
```scala
  val actual: ZIO[EventLoopGroup with ChannelFactory, Throwable, Client.ClientResponse] = 
    Client.request("https://localhost:8073/success", content = HttpData.fromString("Some string"))
```

## Creating an HttpData
### Creating an HttpData from a `String`
To create an `HttpData` that encodes a String you can use `HttpData.fromString`.
```scala
  val textHttpData: HttpData = HttpData.fromString("any string", CharsetUtil.UTF_8)
```
### Creating an HttpData from a `ByteBuf`
To create an `HttpData` that encodes a ByteBuf you can use `HttpData.fromByteBuf`.
```scala
  val binaryByteBufHttpData: HttpData = HttpData.fromByteBuf(Unpooled.copiedBuffer("Some string", CharsetUtil.UTF_8))
```
### Creating an HttpData from `Chunk of Bytes`
To create an `HttpData` that encodes chunk of bytes you can use `HttpData.fromChunk`.
```scala
  val chunkHttpData: HttpData = HttpData.fromChunk(Chunk.fromArray("Some Sting".getBytes(CharsetUtil.UTF_8)))
```
### Creating an HttpData from a `Stream`
To create an `HttpData` that encodes a Stream you can use `HttpData.fromStream`.
- Using a Stream of Bytes
```scala
  val streamHttpData: HttpData = HttpData.fromStream(ZStream.fromChunk(Chunk.fromArray("Some String".getBytes(HTTP_CHARSET))))
```
- Using a Stream of String
```scala
  val streamHttpData: HttpData = HttpData.fromStream(ZStream("a", "b", "c"), CharsetUtil.UTF_8)
```
### Creating an HttpData from a `File`
To create an `HttpData` that encodes a File you can use `HttpData.fromFile`.
```scala
  val fileHttpData: HttpData = HttpData.fromFile(new io.File(getClass.getResource("/fileName.txt").getPath))
```
## Converting `HttpData` to `ByteBuf`
To convert an `HttpData` to `ByteBuf`  you can call the `toButeBuf` method on it, which returns a `Task[ByteBuf]`.
```scala
  val textHttpData: HttpData = HttpData.fromString("any string", CharsetUtil.UTF_8)
  val textByteBuf: Task[ByteBuf] = textHttpData.toByteBuf
```
