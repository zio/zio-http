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

import zio.http._

object SimpleClient extends ZIOAppDefault {
  val url = URL.decode("http://sports.api.decathlon.com/groups/water-aerobics").toOption.get

  val program = for {
    client <- ZIO.service[Client]
    res    <- client.url(url).get("/")
    data   <- res.body.asString
    _      <- Console.printLine(data)
  } yield ()

  override val run = program.provide(Client.default, Scope.default)

}

```