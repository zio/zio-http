# ZIO Http

ZIO Http is a scala library for building http apps. It is powered by [ZIO] and [netty] and aims at being the defacto solution for writing, highly scalable and performant web applications using idiomatic scala.


[![Build Status](https://travis-ci.com/dream11/zio-http.svg?branch=master)](https://travis-ci.com/dream11/zio-http)
[![Discord Chat](https://img.shields.io/discord/629491597070827530.svg?logo=discord)](https://discord.gg/)  

[zio]: https://zio.dev
[netty]: http://netty.io

# Table of Contents

- [ZIO Http](#zio-http)
- [Table of Contents](#table-of-contents)
- [Getting Started](#getting-started)
- [Installation](#installation)
- [Anatomy of an Http app](#anatomy-of-an-http-app)
- [Usage](#usage)
  - [Creating a "_Hello World_" app](#creating-a-hello-world-app)
  - [Starting an Http App](#starting-an-http-app)
  - [Route Matching](#route-matching)
  - [Composition](#composition)
  - [WebSocket Support](#websocket-support)

# Getting Started

A simple Http server can be built using a few lines of code.

```scala
import zhttp.http._
import zhttp.service.Server
import zio._

object HelloWorld extends App {

  val app = Http.collect[Request] {
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

# Anatomy of an Http app

```scala
type Http[R, E, A, B]
```

| Param | Description                                          |
| ----- | ---------------------------------------------------- |
| `R`   | Environment needed by the app (similar to ZIO's `R`) |
| `E`   | Failure type (similar to ZIO's `E`) ?                |
| `A`   | Any Request type                                     |
| `B`   | Any Response type                                    |

# Usage

## Creating a "_Hello World_" app

```scala
import zhttp.http._

val app = Http.text("Hello World!")
```

An application can be made using any of the available operators on `zhttp.Http`. In the above program for any Http request, the response is always `"Hello World!"`.

## Starting an Http App

```scala
import zhttp.http._
import zhttp.service.Server
import zio._

object HelloWorld extends App {

  val app = Http.ok

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    Server.start(8090, app).exitCode
}
```

A simple Http app that responds with empty content and a `200` status code is deployed on port `8090` using `Server.start`.

## Route Matching

```scala
import zhttp.http._

val app = Http.collect[Request] {
  case Method.GET -> Root / "fruits" / "a"  => Response.text("Apple")
  case Method.GET -> Root / "fruits" / "b"  => Response.text("Banana")
}
```

Pattern matching on route is supported by the framework

## Composition

```scala
import zhttp.http._

val a = Http.collect[Request] { case Method.GET -> Root / "a"  => Response.ok }
val b = Http.collect[Request] { case Method.GET -> Root / "b"  => Response.ok }

val app = a <> b
```

Apps can be composed using the `<>` operator. The way it works is, if none of the routes match in `a` , or a `NotFound` error is thrown from `a`, and then the control is passed on to the `b` app.

## WebSocket Support

ZIO Http comes with first-class support for web sockets.

```scala
import zhttp.socket._
import zhttp.http._
import zio.stream._

val socket = Socket.forall(_ => ZStream.repeat(WebSocketFrame.text("Hello!")).take(10))

val app = Http.collect[Request] {
  case Method.GET -> Root / "health"       => Response.ok
  case _          -> Root / "subscription" => socket.asResponse
}

```

<!-- ## Advanced Usage

???

## Performance Tuning

??? -->

# Benchmarks

## Benchmark Results

| **Benchmark (req/sec)** | **ZIO-Http** | **Http4s** | **Play** | **Vert.x** |
| :---         |     :---:      |          ---: |          ---: |          ---: |
| `json` | 700073.31 | 135565.22 | 261223.68 | 644854.27 |
| `plain-text` |719576.04 | 139573.98 | 263819.25 | 707991.69 |

### [Benchmark Details](https://github.com/dream11/zio-http/blob/master/BENCHMARKS.md)