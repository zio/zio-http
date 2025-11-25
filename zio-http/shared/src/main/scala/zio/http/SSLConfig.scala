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

import scala.annotation.unroll

import zio.Config
import zio.Config.Secret

import zio.http.SSLConfig._

sealed trait ClientAuth

object ClientAuth {
  case object Required       extends ClientAuth
  case object NoneClientAuth extends ClientAuth
  case object Optional       extends ClientAuth

}

final case class SSLConfig(
  behaviour: HttpBehaviour,
  data: Data,
  provider: Provider,
  clientAuth: Option[ClientAuth] = None,
  includeClientCert: Boolean = false,
  @unroll
  protocols: Seq[String] = Seq("TLSv1.3", "TLSv1.2"),
)

object SSLConfig {

  def apply(data: Data): SSLConfig =
    new SSLConfig(HttpBehaviour.Redirect, data, Provider.JDK, None)

  def apply(data: Data, clientAuth: ClientAuth): SSLConfig =
    new SSLConfig(HttpBehaviour.Redirect, data, Provider.JDK, Some(clientAuth))

  val config: Config[SSLConfig] =
    (
      HttpBehaviour.config.nested("behaviour") ++
        Data.config.nested("data") ++
        Provider.config.nested("provider")
    ).map { case (behaviour, data, provider) =>
      SSLConfig(behaviour, data, provider)
    }

  def fromFile(certPath: String, keyPath: String): SSLConfig =
    fromFile(HttpBehaviour.Redirect, certPath, keyPath)

  def fromFile(certPath: String, keyPath: String, clientAuth: ClientAuth): SSLConfig =
    fromFile(HttpBehaviour.Redirect, certPath, keyPath, Some(clientAuth))

  def fromFile(
    behaviour: HttpBehaviour,
    certPath: String,
    keyPath: String,
    clientAuth: Option[ClientAuth] = None,
    trustCertCollectionPath: Option[String] = None,
    includeClientCert: Boolean = false,
    @unroll
    protocols: Seq[String] = Seq("TLSv1.3", "TLSv1.2"),
  ): SSLConfig =
    new SSLConfig(
      behaviour,
      Data.FromFile(certPath, keyPath, trustCertCollectionPath),
      Provider.JDK,
      clientAuth,
      includeClientCert,
      protocols,
    )

  def fromResource(certPath: String, keyPath: String): SSLConfig =
    fromResource(HttpBehaviour.Redirect, certPath, keyPath, None)

  def fromResource(certPath: String, keyPath: String, clientAuth: ClientAuth): SSLConfig =
    fromResource(HttpBehaviour.Redirect, certPath, keyPath, Some(clientAuth))

  def fromResource(
    behaviour: HttpBehaviour,
    certPath: String,
    keyPath: String,
    clientAuth: Option[ClientAuth] = None,
    trustCertCollectionPath: Option[String] = None,
    includeClientCert: Boolean = false,
    @unroll
    protocols: Seq[String] = Seq("TLSv1.3", "TLSv1.2"),
  ): SSLConfig =
    new SSLConfig(
      behaviour,
      Data.FromResource(certPath, keyPath, trustCertCollectionPath),
      Provider.JDK,
      clientAuth,
      includeClientCert,
      protocols,
    )

  def fromJavaxNetSslKeyStoreFile(
    keyManagerFile: String,
    keyManagerPassword: Option[Secret] = None,
    behaviour: HttpBehaviour = HttpBehaviour.Redirect,
    keyManagerKeyStoreType: String = "JKS",
    trustManagerKeyStore: Option[Data.TrustManagerKeyStore] = None,
    clientAuth: Option[ClientAuth] = None,
    includeClientCert: Boolean = false,
    @unroll
    protocols: Seq[String] = Seq("TLSv1.3", "TLSv1.2"),
  ): SSLConfig =
    new SSLConfig(
      behaviour,
      Data.FromJavaxNetSsl(
        keyManagerKeyStoreType,
        Data.FromJavaxNetSsl.File(keyManagerFile),
        keyManagerPassword,
        trustManagerKeyStore,
      ),
      Provider.JDK,
      clientAuth,
      includeClientCert,
      protocols,
    )

  def fromJavaxNetSslKeyStoreFile(keyManagerFile: String, keyManagerPassword: Secret): SSLConfig =
    fromJavaxNetSslKeyStoreFile(keyManagerFile, Some(keyManagerPassword))

  def fromJavaxNetSslKeyStoreResource(
    keyManagerResource: String,
    keyManagerPassword: Option[Secret] = None,
    keyManagerKeyStoreType: String = "JKS",
    trustManagerKeyStore: Option[Data.TrustManagerKeyStore] = None,
    clientAuth: Option[ClientAuth] = None,
    includeClientCert: Boolean = false,
    @unroll
    protocols: Seq[String] = Seq("TLSv1.3", "TLSv1.2"),
  ): SSLConfig = {
    fromJavaxNetSsl(
      Data.FromJavaxNetSsl(
        keyManagerKeyStoreType,
        Data.FromJavaxNetSsl.Resource(keyManagerResource),
        keyManagerPassword,
        trustManagerKeyStore,
      ),
      HttpBehaviour.Redirect,
      clientAuth,
      includeClientCert,
      protocols,
    )
  }

  def fromJavaxNetSsl(
    data: Data.FromJavaxNetSsl,
    behaviour: HttpBehaviour = HttpBehaviour.Redirect,
    clientAuth: Option[ClientAuth] = None,
    includeClientCert: Boolean = false,
    @unroll
    protocols: Seq[String] = Seq("TLSv1.3", "TLSv1.2"),
  ): SSLConfig =
    new SSLConfig(
      behaviour,
      data,
      Provider.JDK,
      clientAuth,
      includeClientCert,
      protocols,
    )

  def fromJavaxNetSslKeyStoreResource(keyManagerResource: String, keyManagerPassword: Secret): SSLConfig =
    fromJavaxNetSslKeyStoreResource(keyManagerResource, Some(keyManagerPassword))

  def generate: SSLConfig =
    generate(HttpBehaviour.Redirect, None)

  def generate(clientAuth: ClientAuth): SSLConfig =
    generate(HttpBehaviour.Redirect, Some(clientAuth))

  def generate(behaviour: HttpBehaviour, clientAuth: Option[ClientAuth] = None): SSLConfig =
    new SSLConfig(behaviour, Data.Generate, Provider.JDK, clientAuth)

  sealed trait HttpBehaviour
  object HttpBehaviour {
    case object Accept   extends HttpBehaviour
    case object Fail     extends HttpBehaviour
    case object Redirect extends HttpBehaviour

    val config: Config[HttpBehaviour] =
      Config.string.mapOrFail {
        case "accept"   => Right(Accept)
        case "fail"     => Right(Fail)
        case "redirect" => Right(Redirect)
        case other      => Left(Config.Error.InvalidData(message = s"Invalid Http behaviour: $other"))
      }
  }

  sealed trait Data
  object Data {

    /**
     * A new public/private key pair will be generated and self-signed. Useful
     * for testing/developer mode.
     */
    case object Generate extends Data

    final case class FromFile(certPath: String, keyPath: String, trustCertCollectionPath: Option[String]) extends Data

    final case class FromResource(certPath: String, keyPath: String, trustCertCollectionPath: Option[String])
        extends Data

    final case class TrustManagerKeyStore(
      trustManagerKeyStoreType: String = "JKS",
      trustManagerSource: FromJavaxNetSsl.Source,
      trustManagerPassword: Option[Secret] = None,
    ) { self =>
      def build(): TrustManagerKeyStore = this

      def trustManagerKeyStoreType(tpe: String): TrustManagerKeyStore = self.copy(trustManagerKeyStoreType = tpe)

      def trustManagerFile(file: String): TrustManagerKeyStore =
        trustManagerSource match {
          case FromJavaxNetSsl.Resource(_) => this
          case _                           => self.copy(trustManagerSource = FromJavaxNetSsl.File(file))
        }

      def trustManagerResource(path: String): TrustManagerKeyStore =
        self.copy(trustManagerSource = FromJavaxNetSsl.Resource(path))

      def trustManagerPassword(password: Secret): TrustManagerKeyStore =
        self.copy(trustManagerPassword = Some(password))

      def trustManagerPassword(password: String): TrustManagerKeyStore = trustManagerPassword(Secret(password))
    }

    object TrustManagerKeyStore {
      def fromFile(
        file: String,
        trustManagerPassword: Option[Secret] = None,
        trustManagerKeyStoreType: String = "JKS",
      ): TrustManagerKeyStore =
        TrustManagerKeyStore(trustManagerKeyStoreType, FromJavaxNetSsl.File(file), trustManagerPassword)

      def fromResource(
        resource: String,
        trustManagerPassword: Option[Secret] = None,
        trustManagerKeyStoreType: String = "JKS",
      ): TrustManagerKeyStore =
        TrustManagerKeyStore(trustManagerKeyStoreType, FromJavaxNetSsl.Resource(resource), trustManagerPassword)

    }

    final case class FromJavaxNetSsl(
      keyManagerKeyStoreType: String = "JKS",
      keyManagerSource: FromJavaxNetSsl.Source,
      keyManagerPassword: Option[Secret] = None,
      trustManagerKeyStore: Option[TrustManagerKeyStore] = None,
    ) extends Data { self =>
      def build(): FromJavaxNetSsl                             = this
      def keyManagerKeyStoreType(tpe: String): FromJavaxNetSsl = self.copy(keyManagerKeyStoreType = tpe)

      def keyManagerFile(file: String): FromJavaxNetSsl         =
        keyManagerSource match {
          case FromJavaxNetSsl.Resource(_) => this
          case _                           => self.copy(keyManagerSource = FromJavaxNetSsl.File(file))
        }
      def keyManagerResource(path: String): FromJavaxNetSsl     =
        self.copy(keyManagerSource = FromJavaxNetSsl.Resource(path))
      def keyManagerPassword(password: Secret): FromJavaxNetSsl = self.copy(keyManagerPassword = Some(password))
      def keyManagerPassword(password: String): FromJavaxNetSsl = keyManagerPassword(Secret(password))
      def trustManagerKeyStore(trustManagerKeyStore: TrustManagerKeyStore): FromJavaxNetSsl =
        self.copy(trustManagerKeyStore = Some(trustManagerKeyStore))

    }

    object FromJavaxNetSsl {

      sealed trait Source                         extends Product with Serializable
      final case class File(file: String)         extends Source
      final case class Resource(resource: String) extends Source
    }

    val config: Config[Data] = {
      val generate     = Config.string.mapOrFail {
        case "generate" => Right(Generate)
        case other      => Left(Config.Error.InvalidData(message = s"Invalid Data.Generate: $other"))
      }
      val fromFile     =
        (Config.string("certPath") ++ Config.string("keyPath") ++ Config.Optional(
          Config.string("trust-cert-collection-path"),
        )).map { case (certPath, keyPath, trustCertCollectionPath) =>
          FromFile(certPath, keyPath, trustCertCollectionPath)
        }
      val fromResource =
        (Config.string("cert-resource") ++ Config.string("key-resource") ++ Config.Optional(
          Config.string("trust-cert-collection-resource"),
        )).map { case (certPath, keyPath, trustCertCollectionPath) =>
          FromResource(certPath, keyPath, trustCertCollectionPath)
        }

      val keyManagerKeyStoreType = Config.string("keyManagerKeyStoreType").optional
      val keyManagerPassword     = Config.secret("keyManagerPassword").optional

      val keyManagerFileConfig = {
        val keyManagerFile = Config.string("keyManagerFile")
        keyManagerKeyStoreType ++ keyManagerFile ++ keyManagerPassword
      }

      val trustManagerKeyStoreType = Config.string("trustManagerKeyStoreType").optional
      val trustManagerPassword     = Config.secret("trustManagerPassword").optional

      val trustManagerFileConfig = {
        val trustManagerFile = Config.string("trustManagerFile")
        trustManagerKeyStoreType ++ trustManagerFile ++ trustManagerPassword
      }.optional

      val keyManagerResourceConfig = {
        val keyManagerResource = Config.string("keyManagerResource")
        keyManagerKeyStoreType ++ keyManagerResource ++ keyManagerPassword

      }

      val trustManagerResourceConfig = {
        val trustManagerResource = Config.string("trustManagerResource")
        trustManagerKeyStoreType ++ trustManagerResource ++ trustManagerPassword
      }.optional

      val keyStoreTrustStoreFileConfig = keyManagerFileConfig ++ trustManagerFileConfig

      val keyStoreTrustStoreResourceConfig = keyManagerResourceConfig ++ trustManagerResourceConfig

      val fromJavaxNetSslFile = {
        keyStoreTrustStoreFileConfig.map { case (kmkst, kmf, kmpass, trustStoreInfo) =>
          val trustManagerKeyStore = trustStoreInfo.map { case (tmkst, tmf, tmpass) =>
            TrustManagerKeyStore(tmkst.getOrElse("JKS"), FromJavaxNetSsl.File(tmf), tmpass)
          }
          FromJavaxNetSsl(kmkst.getOrElse("JKS"), FromJavaxNetSsl.File(kmf), kmpass, trustManagerKeyStore)
        }
      }

      val fromJavaxNetSslResource = {
        keyStoreTrustStoreResourceConfig.map { case (kmkst, kmf, kmpass, trustStoreInfo) =>
          val trustManagerKeyStore = trustStoreInfo.map { case (tmkst, tmf, tmpass) =>
            TrustManagerKeyStore(tmkst.getOrElse("JKS"), FromJavaxNetSsl.Resource(tmf), tmpass)
          }
          FromJavaxNetSsl(kmkst.getOrElse("JKS"), FromJavaxNetSsl.Resource(kmf), kmpass, trustManagerKeyStore)
        }
      }
      generate orElse fromFile orElse fromResource orElse fromJavaxNetSslFile orElse fromJavaxNetSslResource
      generate orElse fromFile orElse fromResource
    }
  }

  sealed trait Provider
  object Provider {
    case object JDK     extends Provider
    case object OpenSSL extends Provider

    val config: Config[Provider] =
      Config.string.mapOrFail {
        case "jdk"     => Right(JDK)
        case "openssl" => Right(OpenSSL)
        case other     => Left(Config.Error.InvalidData(message = s"Invalid Provider: $other"))
      }
  }
}
