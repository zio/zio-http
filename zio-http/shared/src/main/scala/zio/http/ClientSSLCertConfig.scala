// Copyright (C) 2019-2020 Eaglescience Software B.V.
package zio.http

import zio.Config

sealed trait ClientSSLCertConfig

object ClientSSLCertConfig {
  val config: Config[ClientSSLCertConfig] = {
    val tpe         = Config.string("type")
    val certPath    = Config.string("cert-path")
    val keyPath     = Config.string("key-path")
    val keyPassword = Config.secret("key-password")

    val fromCertFile                 = certPath.zipWith(keyPath)(FromClientCertFile(_, _))
    val fromCertResource             = certPath.zipWith(keyPath)(FromClientCertResource(_, _))
    val fromCertFileWithPassword     =
      certPath.zip(keyPath).zip(keyPassword).map(t => FromClientCertFileWithPassword(t._1, t._2, t._3))
    val fromCertResourceWithPassword =
      certPath.zip(keyPath).zip(keyPassword).map(t => FromClientCertResourceWithPassword(t._1, t._2, t._3))

    tpe.switch(
      "FromCertFile"                 -> fromCertFile,
      "FromCertResource"             -> fromCertResource,
      "FromCertFileWithPassword"     -> fromCertFileWithPassword,
      "FromCertResourceWithPassword" -> fromCertResourceWithPassword,
    )
  }

  final case class FromClientCertFile(certPath: String, keyPath: String)     extends ClientSSLCertConfig
  final case class FromClientCertResource(certPath: String, keyPath: String) extends ClientSSLCertConfig

  final case class FromClientCertFileWithPassword(certPath: String, keyPath: String, keyPassword: Config.Secret)
      extends ClientSSLCertConfig
  final case class FromClientCertResourceWithPassword(certPath: String, keyPath: String, keyPassword: Config.Secret)
      extends ClientSSLCertConfig

}
