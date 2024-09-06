// Copyright (C) 2019-2020 Eaglescience Software B.V.
package zio.http

import zio.Config

sealed trait ClientSSLCertConfig

object ClientSSLCertConfig {
  val config: Config[ClientSSLCertConfig] = {
    val tpe      = Config.string("type")
    val certPath = Config.string("cert-path")
    val keyPath  = Config.string("key-path")

    val fromCertFile     = certPath.zipWith(keyPath)(FromClientCertFile(_, _))
    val fromCertResource = certPath.zipWith(keyPath)(FromClientCertResource(_, _))

    tpe.switch(
      "FromCertFile"     -> fromCertFile,
      "FromCertResource" -> fromCertResource,
    )
  }

  final case class FromClientCertFile(certPath: String, keyPath: String)     extends ClientSSLCertConfig
  final case class FromClientCertResource(certPath: String, keyPath: String) extends ClientSSLCertConfig

}
