package example

import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues}
import zio._
import zio.http.model.Headers
import zio.http.{Client, ClientConfig}

object ClientWithDecompression extends ZIOAppDefault {
  val url = "http://sports.api.decathlon.com/groups/water-aerobics"

  val program = for {
    res  <- Client.request(url, headers = Headers(HttpHeaderNames.ACCEPT_ENCODING -> HttpHeaderValues.GZIP_DEFLATE))
    data <- res.body.asString
    _    <- Console.printLine(data)
  } yield ()

  val config       = ClientConfig.empty.requestDecompression(true)
  override val run = program.provide(ClientConfig.live(config), Client.fromConfig, Scope.default)

}
