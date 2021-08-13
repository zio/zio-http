# ZIO Http

ZIO Http is a scala library for building http apps. It is powered by [ZIO] and [netty] and aims at being the defacto solution for writing, highly scalable and [performant](#benchmarks) web applications using idiomatic scala.

Check out the full documentation here: [![Documentation](https://dream11.github.io/zio-http/)](https://dream11.github.io/zio-http/)]

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
- [Benchmarks](#benchmarks)
- [Documentation](https://dream11.github.io/zio-http/)

# Getting Started

A simple Http server can be built using a few lines of code.

```scala
import zio._
import zhttp.http._
import zhttp.service.Server

object HelloWorld extends App {
  val app = Http.collect[Request] {
    case Method.GET -> Root / "text" => Response.text("Hello World!")
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
```

You can checkout more examples in the examples project —

- [Simple Server](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/HelloWorld.scala)
- [Advanced Server](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/HelloWorldAdvanced.scala)
- [WebSocket Server](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/SocketEchoServer.scala)
- [Streaming Response](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/StreamingResponse.scala)
- [Simple Client](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/SimpleClient.scala)
- [File Streaming](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/FileStreaming.scala)
- [Authentication](https://github.com/dream11/zio-http/blob/main/example/src/main/scala/Authentication.scala)

# Installation

Setup via `build.sbt`

```scala
libraryDependencies += "io.d11" %% "zhttp"      % "[version]"
libraryDependencies += "io.d11" %% "zhttp-test" % "[version]" % Test
```

# Benchmarks

These are some basic benchmarks of how ZIO Http performs wrt other main-stream libraries.

| **Benchmark (req/sec)** |   `json`   |  `plain-text` |
| :---------------------- | :--------: | ------------: |
| **ZIO-Http**            | 700,073.31 |    719,576.04 |
| **Vert.x**              | 644,854.27 |    707,991.69 |
| **Finagle**             | 567,496.97 |    572,231.69 |
| **Play**                | 261,223.68 |    263,819.25 |
| **Http4s**              | 135,565.22 |    139,573.98 |

More details are available [here](https://github.com/dream11/zio-http/blob/main/BENCHMARKS.md).
