# ZIO Http

ZIO Http is a scala library for building http apps. It is powered by [ZIO] and [netty] and aims at being the defacto solution for writing, highly scalable and [performant](#benchmarks) web applications using idiomatic scala.

[![Build Status](https://travis-ci.com/dream11/zio-http.svg?branch=master)](https://travis-ci.com/dream11/zio-http)
[![Discord Chat](https://img.shields.io/discord/629491597070827530.svg?logo=discord)](https://discord.gg/)
[![Generic badge](https://img.shields.io/badge/Nexus-v1.0.0_RC1-blue.svg)](https://s01.oss.sonatype.org/content/repositories/releases/io/d11/zhttp/1.0.0-RC1/)

[zio]: https://zio.dev
[netty]: http://netty.io

**Table of Contents**

- [ZIO Http](#zio-http)
- [Getting Started](#getting-started)
- [Installation](#installation)
- [Benchmarks](#benchmarks)

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

# Installation

Setup via `build.sbt`

```scala
libraryDependencies += "io.d11" %% "zhttp" % "1.0.0-RC2.1"
```

# Benchmarks

These are some basic benchmarks of how ZIO Http performs wrt other main-stream libraries.

| **Benchmark (req/sec)** |  `json`   | `plain-text` |
| :---------------------- | :-------: | -----------: |
| **ZIO-Http**            | 700073.31 |    719576.04 |
| **Vert.x**              | 644854.27 |    707991.69 |
| **Finagle**             | 567496.97 |    572231.69 |
| **Play**                | 261223.68 |    263819.25 |
| **Http4s**              | 135565.22 |    139573.98 |

More details are available [here](https://github.com/dream11/zio-http/blob/master/BENCHMARKS.md).
