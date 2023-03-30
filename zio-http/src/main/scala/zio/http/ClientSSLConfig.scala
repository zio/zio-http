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
  val config: Config[ClientSSLConfig] = {
    val tpe                = Config.string("type")
    val certPath           = Config.string("certPath")
    val trustStorePath     = Config.string("trustStorePath")
    val trustStorePassword = Config.secret("trustStorePassword").map(d => new String(d.value.toArray))

    val default                = Config.succeed(Default)
    val fromCertFile           = certPath.map(FromCertFile(_))
    val fromCertResource       = certPath.map(FromCertResource(_))
    val fromTrustStoreFile     = trustStorePath.zipWith(trustStorePassword)(FromTrustStoreFile(_, _))
    val fromTrustStoreResource = trustStorePath.zipWith(trustStorePassword)(FromTrustStoreResource(_, _))

    tpe.switch(
      "Default"                -> default,
      "FromCertFile"           -> fromCertFile,
      "FromCertResource"       -> fromCertResource,
      "FromTrustStoreFile"     -> fromTrustStoreFile,
      "FromTrustStoreResource" -> fromTrustStoreResource,
    )
  }

  case object Default                                                                         extends ClientSSLConfig
  final case class FromCertFile(certPath: String)                                             extends ClientSSLConfig
  final case class FromCertResource(certPath: String)                                         extends ClientSSLConfig
  final case class FromTrustStoreResource(trustStorePath: String, trustStorePassword: String) extends ClientSSLConfig
  final case class FromTrustStoreFile(trustStorePath: String, trustStorePassword: String)     extends ClientSSLConfig
}
