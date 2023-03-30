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

sealed trait ClientSSLConfig

object ClientSSLConfig {
  case object Default                                                                         extends ClientSSLConfig
  final case class FromCertFile(certPath: String)                                             extends ClientSSLConfig
  final case class FromCertResource(certPath: String)                                         extends ClientSSLConfig
  final case class FromTrustStoreResource(trustStorePath: String, trustStorePassword: String) extends ClientSSLConfig
  final case class FromTrustStoreFile(trustStorePath: String, trustStorePassword: String)     extends ClientSSLConfig

  lazy val config: Config[ClientSSLConfig] = {
    val default                = Config.string.mapOrFail {
      case "default" => Right(Default)
      case other     => Left(Config.Error.InvalidData(message = s"Invalid value for ClientSSLConfig: $other"))
    }
    val fromCertFile           = Config.string("cert-file").map(FromCertFile.apply)
    val fromCertResource       = Config.string("cert-resource").map(FromCertResource.apply)
    val fromTrustStoreResource = Config.string("trust-store-resource").zip(Config.string("trust-store-password")).map {
      case (path, password) => FromTrustStoreResource(path, password)
    }
    val fromTrustStoreFile     = Config.string("trust-store-file").zip(Config.string("trust-store-password")).map {
      case (path, password) => FromTrustStoreFile(path, password)
    }
    default.orElse(fromCertFile).orElse(fromCertResource).orElse(fromTrustStoreResource).orElse(fromTrustStoreFile)
  }
}
