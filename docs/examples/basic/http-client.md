---
id: http-client
title: Http Client Example
sidebar_label: Http Client
---

```scala mdoc:silent
import zio._

import zio.http.Client

object SimpleClient extends ZIOAppDefault {
  val url = "http://sports.api.decathlon.com/groups/water-aerobics"

  val program = for {
    res  <- Client.request(url)
    data <- res.body.asString
    _    <- Console.printLine(data)
  } yield ()

  override val run = program.provide(Client.default)

}

```