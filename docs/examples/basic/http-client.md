---
id: http-client
title: Http Client Example
sidebar_label: Http Client
---

```scala mdoc:silent
import zio._

import zio.http._

object SimpleClient extends ZIOAppDefault {
  val url = "http://sports.api.decathlon.com/groups/water-aerobics"

  val program = for {
    res  <- Client.request(Request.get(url))
    data <- res.body.asString
    _    <- Console.printLine(data)
  } yield ()

  override val run = program.provide(Client.default, Scope.default)

}

```