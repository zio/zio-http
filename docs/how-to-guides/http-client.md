---
id: http-client
title: HTTP Client 
---


This example provided demonstrates how to perform an HTTP client request using the zio-http library in Scala with ZIO.


## `build.sbt` Setup 

```scala
scalaVersion := "2.13.6"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio-http" % "3.0.0-RC2"
)
```


## code 

```scala
import zio._
import zio.http.Client

object SimpleClient extends ZIOAppDefault {
  val url = "http://sports.api.decathlon.com/groups/water-aerobics"

  val program = for {
    res  <- Client.request(Request.get(url))
    data <- res.body.asString
    _    <- Console.printLine(data).catchAll(e => ZIO.logError(e.getMessage))
  } yield ()

  override val run = program.provide(Client.default, Scope.default)


}

```
