# ZIO Http [![Build Status](https://travis-ci.com/dream11/zio-http.svg?branch=master)](https://travis-ci.com/dream11/zio-http)

ZIO Http is a library for building high performance Http apps using [ZIO].

[zio]: https://zio.dev

# Key Features

- **ZIO Powered Frontend:** ???
- **Netty Backend:** ???
- **Socket Support:** ???
- **Middleware:** ???

# Table of Content

- [ZIO Http ![Build Status](https://travis-ci.com/dream11/zio-http)](#zio-http-)
- [Getting Started](#getting-started)
- [Installation](#installation)
- [Features](#features)
- [Usage](#usage)
  - [Advanced Usage](#advanced-usage)
  - [Server Tuning](#server-tuning)
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

# Features

???

# Usage

???

## Advanced Usage

???

## Performance Tuning

???

# Benchmarks
