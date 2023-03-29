package example

import zio._

import zio.http.DnsResolver.Config
import zio.http.model.Headers
import zio.http.netty.NettyConfig
import zio.http.{Client, DnsResolver, ZClient}

import io.netty.handler.codec.http.{HttpHeaderNames, HttpHeaderValues}

object ClientWithDecompression extends ZIOAppDefault {
  val url = "http://sports.api.decathlon.com/groups/water-aerobics"

  val program = for {
    res  <- Client.request(url, headers = Headers(HttpHeaderNames.ACCEPT_ENCODING -> HttpHeaderValues.GZIP_DEFLATE))
    data <- res.body.asString
    _    <- Console.printLine(data)
  } yield ()

  val config       = ZClient.Config.default.requestDecompression(true)
  override val run =
    program.provide(
      ZLayer.succeed(config),
      Client.live,
      ZLayer.succeed(Config.default),
      ZLayer.succeed(NettyConfig.default),
      DnsResolver.default,
    )

}
