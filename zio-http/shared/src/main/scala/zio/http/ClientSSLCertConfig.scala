// Copyright (C) 2019-2020 Eaglescience Software B.V.
package zio.http

import java.io.{ByteArrayInputStream, InputStream}
import java.nio.charset.StandardCharsets

import zio.Config

sealed trait ClientSSLCertConfig

object ClientSSLCertConfig {
  val config: Config[ClientSSLCertConfig] = {
    val tpe      = Config.string("type")
    val certPath = Config.string("cert-path")
    val keyPath  = Config.string("key-path")

    val certContent = Config.string("cert-content")
    val keyContent  = Config.string("key-content")

    val fromCertFile     = certPath.zipWith(keyPath)(FromClientCertFile(_, _))
    val fromCertResource = certPath.zipWith(keyPath)(FromClientCertResource(_, _))
    val fromEnv          = certContent.zipWith(keyContent)(FromClientCertContent(_, _))

    tpe.switch(
      "FromCertFile"     -> fromCertFile,
      "FromCertResource" -> fromCertResource,
      "FromCertEnv"      -> fromEnv,
    )
  }

  def toInputStream(content: String): InputStream =
    new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8))

  final case class FromClientCertFile(certPath: String, keyPath: String)     extends ClientSSLCertConfig
  final case class FromClientCertResource(certPath: String, keyPath: String) extends ClientSSLCertConfig
  final case class FromClientCertContent(cert: String, key: String)          extends ClientSSLCertConfig

}
