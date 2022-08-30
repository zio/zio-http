# ZIO Http

ZIO Http is a scala library for building http apps. It is powered by [ZIO] and [netty] and aims at being the defacto solution for writing, highly scalable and [performant](#benchmarks) web applications using idiomatic scala.

Check out the full documentation here: [Documentation]

[Documentation]: https://dream11.github.io/zio-http

![Continuous Integration](https://github.com/dream11/zio-http/workflows/Continuous%20Integration/badge.svg)
[![Discord Chat](https://img.shields.io/discord/629491597070827530.svg?logo=discord)](https://discord.com/channels/629491597070827530/819703129267372113)
[![Sonatype Nexus (Releases)](https://img.shields.io/nexus/r/io.d11/zhttp_2.13?server=https%3A%2F%2Fs01.oss.sonatype.org)](https://oss.sonatype.org/content/repositories/releases/io/d11/zhttp_2.13/)
[![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/io.d11/zhttp_2.13?server=https%3A%2F%2Fs01.oss.sonatype.org)](https://s01.oss.sonatype.org/content/repositories/snapshots/io/d11/zhttp_2.13/)
[![Average time to resolve an issue](http://isitmaintained.com/badge/resolution/dream11/zio-http.svg)](http://isitmaintained.com/project/dream11/zio-http "Average time to resolve an issue")
[![Open in Visual Studio Code](https://open.vscode.dev/badges/open-in-vscode.svg)](https://open.vscode.dev/dream11/zio-http)

[zio]: https://zio.dev
[netty]: http://netty.io

**Table of Contents**

- [ZIO Http](#zio-http)
- [Getting Started](#getting-started)
- [Installation](#installation)
- [Documentation](https://dream11.github.io/zio-http/)

# Getting Started

A simple Http server can be built using a few lines of code.

```scala
import zhttp.http._
import zhttp.service.Server
import zio._

object HelloWorld extends ZIOAppDefault {

  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
  }

  override val run =
    Server.start(8090, app)
}
```
#### Examples

You can checkout more examples in the [example](https://github.com/dream11/zio-http/tree/main/example/src/main/scala/example) project —

- [Simple Server](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/example/HelloWorld.scala)
- [Advanced Server](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/example/HelloWorldAdvanced.scala)
- [WebSocket Server](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/example/WebSocketEcho.scala)
- [Streaming Response](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/example/StreamingResponse.scala)
- [Simple Client](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/example/SimpleClient.scala)
- [File Streaming](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/example/FileStreaming.scala)
- [Basic Authentication](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/example/BasicAuth.scala)
- [JWT Authentication Client](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/example/AuthenticationClient.scala)
- [JWT Authentication Server](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/example/AuthenticationServer.scala)

#### Steps to run an example

1. Edit the [RunSettings](https://github.com/dream11/zio-http/blob/main/project/BuildHelper.scala#L109) - modify `className` to the example you'd like to run.
2. From sbt shell, run `~example/reStart`. You should see `Server started on port: 8090`.
3. Send curl request for defined `http Routes`, for eg : `curl -i "http://localhost:8090/text"` for `example.HelloWorld`.

# Installation

Setup via `build.sbt`

```scala
libraryDependencies += "io.d11" %% "zhttp"      % "[version]"
```

**NOTE:** ZIO Http is compatible with `ZIO 1.x` and `ZIO 2.x`.


# Watch Mode

You can use the [sbt-revolver] plugin to start the server and run it in watch mode using `~ reStart` command on the SBT console.

[sbt-revolver]: https://github.com/spray/sbt-revolver

