# ZIO Http

ZIO Http is a library for building http apps. It is powered by [ZIO] and [netty] and aims at being the defacto solution for writing, highly scalable and performant web applications using idiomatic scala.

[![Build Status](https://travis-ci.com/dream11/zio-http.svg?branch=master)](https://travis-ci.com/dream11/zio-http)

[zio]: https://zio.dev
[netty]: http://netty.io

# Table of Contents

- [ZIO Http](#zio-http)
- [Table of Contents](#table-of-contents)
- [Getting Started](#getting-started)
- [Installation](#installation)
- [Usage](#usage)
  - [Advanced Usage](#advanced-usage)
  - [Performance Tuning](#performance-tuning)
- [Benchmarks](#benchmarks)

# Getting Started

A simple Http server can be built using a few lines of code.

```scala
import zhttp.http._
import zhttp.service.Server
import zio._

object HelloWorld extends App {

  val app = Http.route {
    case Method.GET -> Root / "text" => Response.text("Hello World!")
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode

}
```

# Installation

Setup via **build.sbt**

```scala
lazy val zhttp = ProjectRef(uri(s"git://github.com/dream11/zio-http.git"), "zhttp")

lazy val root = (project in file(".")).dependsOn(zhttp)
```

# Usage

???

## Advanced Usage

???

## Performance Tuning

???

# Benchmarks
