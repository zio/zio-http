---
id: fullstack-with-react-apps
title: "Creating Full Stack Apps with React and Zio Http"
sidebar_label: "Serve React App"
---

```scala mdoc
import zio._
import zio.http._
import zio.stream.ZStream

import java.io.File
import java.nio.file.Paths

object HelloWorld extends ZIOAppDefault {
    // Create the build relative directory path
    private val buildDirectory = "../../react/build"
    
    // Create HTTP route
    val app = Routes(
        Method.GET -> Root / "api" / "hello" => Handler.text("Hello World!").toHttp,

        // Uses netty's capability to write file content to the Channel
        // Content-type response headers are automatically identified and added
        // Adds content-length header and does not use Chunked transfer encoding
        Method.GET -> Root => Handler.fromFile(new File(s"$buildDirectory/index.html")),
        Method.GET -> "" /: file => Handler.fromFile(new File(s"$buildDirectory/$file")),
    ).sandbox.toHttpApp

    // Run it like any simple app
    override val run = Server.serve(app.withDefaultErrorResponse).provide(Server.default)
}
```
