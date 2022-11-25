---
sidebar_position: "5"
---
# Body
`Body` is a domain to model content for `Request`, `Response` and `ClientRequest`. ZIO HTTP uses Netty at it's core and Netty handles content as `ByteBuf`. `Body` helps you decode and encode this content into simpler, easier to use data types while creating a Request or Response.
## Server-side usage of `Body`
On the server-side, `ZIO-HTTP` models content in `Request` and `Response` as `Body` with `Body.Empty` as the default value.
To add content while creating a `Response` you can use the `Response` constructor.
```scala
  val res: Response = Response( body = Body.fromString("Some String"))
```
To add content while creating a `Request` for unit tests, you can use the `Request` constructor.
```scala
  val req: Request = Request( body = Body.fromString("Some String"))
```
## Client-side usage of `Body`
On the client-side, `ZIO-HTTP` models content in `ClientRequest` as `Body` with `Body.Empty` as the default value.
To add content while making a request using ZIO HTTP you can use the `Client.request` method.
```scala
  val actual: ZIO[EventLoopGroup with ChannelFactory, Throwable, Client.ClientResponse] = 
    Client.request("https://localhost:8073/success", content = Body.fromString("Some string"))
```

## Creating an Body
### Creating an Body from a `String`
To create an `Body` that encodes a String you can use `Body.fromString`.
```scala
  val textHttpData: Body = Body.fromString("any string", CharsetUtil.UTF_8)
```
### Creating an Body from a `ByteBuf`
To create an `Body` that encodes a ByteBuf you can use `Body.fromByteBuf`.
```scala
  val binaryByteBufHttpData: Body = Body.fromByteBuf(Unpooled.copiedBuffer("Some string", CharsetUtil.UTF_8))
```
### Creating an Body from `Chunk of Bytes`
To create an `Body` that encodes chunk of bytes you can use `Body.fromChunk`.
```scala
  val chunkHttpData: Body = Body.fromChunk(Chunk.fromArray("Some Sting".getBytes(CharsetUtil.UTF_8)))
```
### Creating an Body from a `Stream`
To create an `Body` that encodes a Stream you can use `Body.fromStream`.
- Using a Stream of Bytes
```scala
  val streamHttpData: Body = Body.fromStream(ZStream.fromChunk(Chunk.fromArray("Some String".getBytes(HTTP_CHARSET))))
```
- Using a Stream of String
```scala
  val streamHttpData: Body = Body.fromStream(ZStream("a", "b", "c"), CharsetUtil.UTF_8)
```
### Creating an Body from a `File`
To create an `Body` that encodes a File you can use `Body.fromFile`.
```scala
  val fileHttpData: Body = Body.fromFile(new io.File(getClass.getResource("/fileName.txt").getPath))
```
## Converting `Body` to `ByteBuf`
To convert an `Body` to `ByteBuf`  you can call the `toButeBuf` method on it, which returns a `Task[ByteBuf]`.
```scala
  val textHttpData: Body = Body.fromString("any string", CharsetUtil.UTF_8)
  val textByteBuf: Task[ByteBuf] = textHttpData.toByteBuf
```
