/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http

import zio.Config
import zio.Config.Secret

sealed trait ClientSSLConfig

object ClientSSLConfig {
  val config: Config[ClientSSLConfig] = {
    val tpe                = Config.string("type")
    val certPath           = Config.string("cert-path")
    val trustStorePath     = Config.string("trust-store-path")
    val trustStorePassword = Config.secret("trust-store-password")

    val keyManagerKeyStoreType   = Config.string("keyManagerKeyStoreType")
    val keyManagerFile           = Config.string("keyManagerFile")
    val keyManagerResource       = Config.string("keyManagerResource")
    val keyManagerPassword       = Config.secret("keyManagerPassword")
    val trustManagerKeyStoreType = Config.string("trustManagerKeyStoreType")
    val trustManagerFile         = Config.string("trustManagerFile")
    val trustManagerResource     = Config.string("trustManagerResource")
    val trustManagerPassword     = Config.secret("trustManagerPassword")

    val default                 = Config.succeed(Default)
    val fromCertFile            = certPath.map(FromCertFile(_))
    val fromCertResource        = certPath.map(FromCertResource(_))
    val fromTrustStoreFile      = trustStorePath.zipWith(trustStorePassword)(FromTrustStoreFile(_, _))
    val fromTrustStoreResource  = trustStorePath.zipWith(trustStorePassword)(FromTrustStoreResource(_, _))
    val fromClientAndServerCert = Config.defer {
      val serverCertConfig = config.nested("cert", "server")
      val clientCertConfig = ClientSSLCertConfig.config.nested("cert", "client")
      serverCertConfig.zipWith(clientCertConfig)(FromClientAndServerCert(_, _))
    }

    val fromJavaxNetSsl = {
      keyManagerKeyStoreType.optional
        .zip(keyManagerFile.optional)
        .zip(keyManagerResource.optional)
        .zip(keyManagerPassword.optional)
        .zip(trustManagerKeyStoreType.optional)
        .zip(
          trustManagerFile.optional
            .zip(trustManagerResource.optional)
            .validate("must supply trustManagerFile or trustManagerResource")(pair =>
              pair._1.isDefined || pair._2.isDefined,
            ),
        )
        .zip(trustManagerPassword.optional)
        .map { case (kmkst, kmf, kmr, kmpass, tmkst, (tmf, tmr), tmpass) =>
          val bldr0 =
            List[(Option[String], FromJavaxNetSsl => String => FromJavaxNetSsl)](
              (kmkst, b => b.keyManagerKeyStoreType(_)),
              (kmf, b => b.keyManagerFile),
              (kmr, b => b.keyManagerResource),
              (tmkst, b => b.trustManagerKeyStoreType(_)),
              (tmf, b => b.trustManagerFile),
              (tmr, b => b.trustManagerResource),
            )
              .foldLeft(FromJavaxNetSsl()) { case (bldr, (maybe, lens)) =>
                maybe.fold(bldr)(s => lens(bldr)(s))
              }

          List[(Option[Secret], FromJavaxNetSsl => Secret => FromJavaxNetSsl)](
            (kmpass, b => b.keyManagerPassword(_)),
            (tmpass, b => b.trustManagerPassword(_)),
          )
            .foldLeft(bldr0) { case (bldr, (maybe, lens)) =>
              maybe.fold(bldr)(s => lens(bldr)(s))
            }
            .build()
        }
    }

    tpe.switch(
      "Default"                 -> default,
      "FromCertFile"            -> fromCertFile,
      "FromCertResource"        -> fromCertResource,
      "FromTrustStoreFile"      -> fromTrustStoreFile,
      "FromTrustStoreResource"  -> fromTrustStoreResource,
      "FromClientAndServerCert" -> fromClientAndServerCert,
    )
  }

  case object Default                                                                         extends ClientSSLConfig
  final case class FromCertFile(certPath: String)                                             extends ClientSSLConfig
  final case class FromCertResource(certPath: String)                                         extends ClientSSLConfig
  final case class FromTrustStoreResource(trustStorePath: String, trustStorePassword: Secret) extends ClientSSLConfig
  final case class FromClientAndServerCert(
    serverCertConfig: ClientSSLConfig,
    clientCertConfig: ClientSSLCertConfig,
  ) extends ClientSSLConfig

  final case class FromJavaxNetSsl(
    keyManagerKeyStoreType: String = "JKS",
    keyManagerSource: FromJavaxNetSsl.Source = FromJavaxNetSsl.Empty,
    keyManagerPassword: Option[Secret] = None,
    trustManagerKeyStoreType: String = "JKS",
    trustManagerSource: FromJavaxNetSsl.Source = FromJavaxNetSsl.Empty,
    trustManagerPassword: Option[Secret] = None,
  ) extends ClientSSLConfig { self =>

    def isValidBuild: Boolean    = trustManagerSource != FromJavaxNetSsl.Empty
    def isInvalidBuild: Boolean  = !isValidBuild
    def build(): FromJavaxNetSsl = this

    def keyManagerKeyStoreType(tpe: String): FromJavaxNetSsl  = self.copy(keyManagerKeyStoreType = tpe)
    def keyManagerFile(file: String): FromJavaxNetSsl         =
      keyManagerSource match {
        case FromJavaxNetSsl.Resource(_) => this
        case _                           => self.copy(keyManagerSource = FromJavaxNetSsl.File(file))
      }
    def keyManagerResource(path: String): FromJavaxNetSsl = self.copy(keyManagerSource = FromJavaxNetSsl.Resource(path))
    def keyManagerPassword(password: Secret): FromJavaxNetSsl = self.copy(keyManagerPassword = Some(password))
    def keyManagerPassword(password: String): FromJavaxNetSsl = keyManagerPassword(Secret(password))

    def trustManagerKeyStoreType(tpe: String): FromJavaxNetSsl  = self.copy(trustManagerKeyStoreType = tpe)
    def trustManagerFile(file: String): FromJavaxNetSsl         =
      trustManagerSource match {
        case FromJavaxNetSsl.Resource(_) => this
        case _                           => self.copy(trustManagerSource = FromJavaxNetSsl.File(file))
      }
    def trustManagerResource(path: String): FromJavaxNetSsl     =
      self.copy(trustManagerSource = FromJavaxNetSsl.Resource(path))
    def trustManagerPassword(password: Secret): FromJavaxNetSsl = self.copy(trustManagerPassword = Some(password))
    def trustManagerPassword(password: String): FromJavaxNetSsl = trustManagerPassword(Secret(password))
  }

  object FromJavaxNetSsl {

    sealed trait Source                         extends Product with Serializable
    case object Empty                           extends Source
    final case class File(file: String)         extends Source
    final case class Resource(resource: String) extends Source

    def builderWithTrustManagerFile(file: String): FromJavaxNetSsl =
      FromJavaxNetSsl().trustManagerFile(file)

    def builderWithTrustManagerResource(resource: String): FromJavaxNetSsl =
      FromJavaxNetSsl().trustManagerResource(resource)
  }

  object FromTrustStoreResource {
    def apply(trustStorePath: String, trustStorePassword: String): FromTrustStoreResource =
      FromTrustStoreResource(trustStorePath, Secret(trustStorePassword))
  }
  final case class FromTrustStoreFile(trustStorePath: String, trustStorePassword: Secret) extends ClientSSLConfig
  object FromTrustStoreFile     {
    def apply(trustStorePath: String, trustStorePassword: String): FromTrustStoreFile =
      FromTrustStoreFile(trustStorePath, Secret(trustStorePassword))
  }
}
