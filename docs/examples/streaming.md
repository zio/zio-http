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

The code demonstrate a simple http server that can be use to stream data to a client and streaming responses:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/StreamingResponse.scala")
```

## Streaming File

This code showcases the utilization of ZIO HTTP to enable file streaming in an HTTP server:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/FileStreaming.scala")
```
