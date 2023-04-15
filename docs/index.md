---
id: index
title: "Introduction to ZIO Http"
sidebar_label: "ZIO Http"
---

ZIO Http is a scala library for building http apps. It is powered by ZIO and [netty](https://netty.io/) and aims at being the defacto solution for writing, highly scalable and performant web applications using idiomatic scala.

@PROJECT_BADGES@

## Installation

Setup via `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-http" % "@VERSION@"
```

**NOTES ON VERSIONING:**

- Older library versions `1.x` or `2.x` of ZIO Http are derived from Dream11, the organization that donated ZIO Http to the ZIO organization in 2022. 
- Newer library versions, starting in 2023 and resulting from the ZIO organization start with `0.x`.

## Getting Started

A simple Http server can be built using a few lines of code.

```scala
import zio._
import zio.http._
import zio.http.model.Method

object HelloWorld extends ZIOAppDefault {

  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
  }

  override val run =
    Server.serve(app).provide(Server.default)
}
```

## Steps to run an example

1. Edit the [RunSettings](https://github.com/zio/zio-http/blob/main/project/BuildHelper.scala#L107) - modify `className` to the example you'd like to run.
2. From sbt shell, run `~example/reStart`. You should see `Server started on port: 8080`.
3. Send curl request for defined `http Routes`, for eg : `curl -i "http://localhost:8080/text"` for `example.HelloWorld`.

## Watch Mode

You can use the [sbt-revolver] plugin to start the server and run it in watch mode using `~ reStart` command on the SBT console.

[sbt-revolver]: https://github.com/spray/sbt-revolver
