---
id: graceful-shutdown
title: "Graceful Shutdown Example"
sidebar_label: "Graceful Shutdown"
description: "Shut a ZIO HTTP server down gracefully, letting in-flight requests complete before releasing resources."
---

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/GracefulShutdown.scala")
```
