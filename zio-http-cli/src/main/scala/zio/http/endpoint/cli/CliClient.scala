package zio.http.endpoint.cli

import zio._

import zio.http._
import zio.http.netty.client.NettyClient

/**
 * CliClient is a wrapper for the Http Client of a HttpCliApp. It allows to
 * provide a Client in different ways. DefaultClient provides the ZLayer
 * NettyClient.default.
 */
private[cli] sealed trait CliClient

private[cli] final case class CliZIOClient(client: ZClient.Client)                            extends CliClient
private[cli] final case class CliZLayerClient(client: ZLayer[Any, Throwable, ZClient.Client]) extends CliClient
private[cli] final case class DefaultClient()                                                 extends CliClient
