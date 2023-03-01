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

import zio.http.SSLConfig._

final case class SSLConfig(behaviour: HttpBehaviour, data: Data, provider: Provider)

object SSLConfig {

  def apply(data: Data): SSLConfig =
    new SSLConfig(HttpBehaviour.Redirect, data, Provider.JDK)

  def fromFile(certPath: String, keyPath: String): SSLConfig =
    new SSLConfig(HttpBehaviour.Redirect, Data.FromFile(certPath, keyPath), Provider.JDK)

  def fromFile(behaviour: HttpBehaviour, certPath: String, keyPath: String): SSLConfig =
    new SSLConfig(behaviour, Data.FromFile(certPath, keyPath), Provider.JDK)

  def fromResource(certPath: String, keyPath: String): SSLConfig =
    new SSLConfig(HttpBehaviour.Redirect, Data.FromResource(certPath, keyPath), Provider.JDK)

  def fromResource(behaviour: HttpBehaviour, certPath: String, keyPath: String): SSLConfig =
    new SSLConfig(behaviour, Data.FromResource(certPath, keyPath), Provider.JDK)

  def generate: SSLConfig =
    new SSLConfig(HttpBehaviour.Redirect, Data.Generate, Provider.JDK)

  def generate(behaviour: HttpBehaviour): SSLConfig =
    new SSLConfig(behaviour, Data.Generate, Provider.JDK)

  sealed trait HttpBehaviour
  object HttpBehaviour {
    case object Accept   extends HttpBehaviour
    case object Fail     extends HttpBehaviour
    case object Redirect extends HttpBehaviour
  }

  sealed trait Data
  object Data {

    /**
     * A new public/private key pair will be generated and self-signed. Useful
     * for testing/developer mode.
     */
    case object Generate extends Data

    final case class FromFile(certPath: String, keyPath: String) extends Data

    final case class FromResource(certPath: String, keyPath: String) extends Data
  }

  sealed trait Provider
  object Provider {
    case object JDK     extends Provider
    case object OpenSSL extends Provider
  }
}
