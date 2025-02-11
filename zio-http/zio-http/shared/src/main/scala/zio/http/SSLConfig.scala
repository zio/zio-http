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
  ): SSLConfig =
    new SSLConfig(
      behaviour,
      Data.FromFile(certPath, keyPath, trustCertCollectionPath),
      Provider.JDK,
      clientAuth,
      includeClientCert,
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
  ): SSLConfig =
    new SSLConfig(
      behaviour,
      Data.FromResource(certPath, keyPath, trustCertCollectionPath),
      Provider.JDK,
      clientAuth,
      includeClientCert,
    )

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
