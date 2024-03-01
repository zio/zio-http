---
id: https-client-server
title: HTTPS Client and Server Example
sidebar_label: Https Client and Server
---

## Client Example

This code demonstrate a simple HTTPS client that send an HTTP GET request to a specific URL and retrieve the response:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/HttpsClient.scala")
```

## Server Example

This example demonstrates how to use ZIO to create an HTTP server with HTTPS support and configure SSL using a keystore:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/HttpsHelloWorld.scala")
```
