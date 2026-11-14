---
id: streaming
title: "Streaming Examples"
sidebar_label: "Streaming"
---

## Streaming Request

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/RequestStreaming.scala")
```

## Streaming Response

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/StreamingResponse.scala")
```

## Streaming File

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/FileStreaming.scala")
```

## Streaming Off-Heap Memory Without a JVM Copy

`NettyBody.fromByteBufStream` writes direct (off-heap) `ByteBuf`s to the socket as they are: no `Chunk[Byte]`, no `Array[Byte]`, no copy into a heap buffer. The stream carries tickets into a producer-owned registry, the writer claims each buffer as it hands it to Netty, and the registry releases whatever was never claimed when the stream ends, whether by completion, failure or interruption:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/OffHeapStreaming.scala")
```
