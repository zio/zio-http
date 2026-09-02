// Copyright (C) 2019-2020 Eaglescience Software B.V.
package zio.http

import zio.{Chunk, Config}

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

  /**
   * Client certificate and private key supplied directly as in-memory PEM bytes
   */
  final case class FromClientCertBytes(certBytes: Chunk[Byte], keyBytes: Chunk[Byte]) extends ClientSSLCertConfig {
    override def toString: String = s"FromClientCertBytes(<${certBytes.size} cert bytes>, <redacted key>)"
  }
  object FromClientCertBytes {
    def apply(certBytes: Array[Byte], keyBytes: Array[Byte]): FromClientCertBytes =
      FromClientCertBytes(Chunk.fromArray(certBytes), Chunk.fromArray(keyBytes))
  }

  /**
   * Same as [[FromClientCertBytes]] but for a password-encrypted private key.
   */
  final case class FromClientCertBytesWithPassword(
    certBytes: Chunk[Byte],
    keyBytes: Chunk[Byte],
    keyPassword: Config.Secret,
  ) extends ClientSSLCertConfig {

    /** @see [[FromClientCertBytes.toString]] */
    override def toString: String =
      s"FromClientCertBytesWithPassword(<${certBytes.size} cert bytes>, <redacted key>, <redacted password>)"
  }
  object FromClientCertBytesWithPassword {
    def apply(
      certBytes: Array[Byte],
      keyBytes: Array[Byte],
      keyPassword: Config.Secret,
    ): FromClientCertBytesWithPassword =
      FromClientCertBytesWithPassword(Chunk.fromArray(certBytes), Chunk.fromArray(keyBytes), keyPassword)
  }

}
