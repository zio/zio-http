---
id: cli
title: "CLI Client-Server Examples"
sidebar_label: "CLI"
---

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/endpoint/CliExamples.scala")
```

The code defines a set of endpoints for a ZIO-based HTTP server and client application. 

- It declares data models for `User` and `Post`, each with their respective schemas.
- It defines three endpoints:
  1. `getUser`: Retrieves user information by ID.
  2. `getUserPosts`: Retrieves a user's posts by user ID and post ID.
  3. `createUser`: Creates a new user.

The `TestCliApp` object sets up a CLI application using these endpoints, while the `TestCliServer` object implements routes for handling HTTP requests corresponding to the defined endpoints. Finally, the `TestCliClient` object demonstrates how to interact with these endpoints using a client application.