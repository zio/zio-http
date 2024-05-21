---
id: cli-app-endpoint
title: "Generating ZIO CLI App from Endpoint API"
---

# Generating ZIO CLI App from Endpoint API

ZIO HTTP allows to generate a ZIO CLI client application from `Endpoint` definitions using the `HttpCliApp.fromEndpoints` constructor.

```scala mdoc:passthrough
import zio._
import zio.cli._

import zio.http._
import zio.http.codec._
import zio.http.endpoint.cli._

val cliApp =
  HttpCliApp
    .fromEndpoints(
      name = "users-mgmt",
      version = "0.0.1",
      summary = HelpDoc.Span.text("Users management CLI"),
      footer = HelpDoc.p("Copyright 2024"),
      host = "localhost",
      port = 8080,
      endpoints = Chunk(getUserRoute, createUserRoute),
      cliStyle = true,
    )
    .cliApp
```

This code will generate a ZIO CLI application named `users-mgmt` with version `0.0.1`, providing a command-line interface for interacting with your API endpoints.