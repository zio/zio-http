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
    val certPath           = Config.string("certPath")
    val trustStorePath     = Config.string("trustStorePath")
    val trustStorePassword = Config.secret("trustStorePassword")

    val keyManagerKeyStoreType   = Config.string("keyManagerKeyStoreType")
    val keyManagerFile           = Config.string("keyManagerFile")
    val keyManagerResource       = Config.string("keyManagerResource")
    val keyManagerPassword       = Config.secret("keyManagerPassword")
    val trustManagerKeyStoreType = Config.string("trustManagerKeyStoreType")
    val trustManagerFile         = Config.string("trustManagerFile")
    val trustManagerResource     = Config.string("trustManagerResource")
    val trustManagerPassword     = Config.secret("trustManagerPassword")

    val default                = Config.succeed(Default)
    val fromCertFile           = certPath.map(FromCertFile(_))
    val fromCertResource       = certPath.map(FromCertResource(_))
    val fromTrustStoreFile     = trustStorePath.zipWith(trustStorePassword)(FromTrustStoreFile(_, _))
    val fromTrustStoreResource = trustStorePath.zipWith(trustStorePassword)(FromTrustStoreResource(_, _))

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
          List[(Option[String], FromJavaxNetSsl => String => FromJavaxNetSsl)](
            (kmkst, b => b.keyManagerKeyStoreType(_)),
            (kmf, b => b.keyManagerFile),
            (kmr, b => b.keyManagerResource),
            (kmpass, b => b.keyManagerPassword(_)),
            (tmkst, b => b.trustManagerKeyStoreType(_)),
            (tmf, b => b.trustManagerFile),
            (tmr, b => b.trustManagerResource),
            (tmpass, b => b.trustManagerPassword(_)),
          )
            // Use an empty FromJavaxNetSsl as a builder.  Config validation above
            // ensures we will create a valid result.
            .foldLeft(FromJavaxNetSsl()) { case (bldr, (maybe, lens)) =>
              maybe.fold(bldr)(s => lens(bldr)(s))
            }
            .build()
        }
    }

    tpe.switch(
      "Default"                -> default,
      "FromCertFile"           -> fromCertFile,
      "FromCertResource"       -> fromCertResource,
      "FromTrustStoreFile"     -> fromTrustStoreFile,
      "FromTrustStoreResource" -> fromTrustStoreResource,
      "FromJavaxNetSsl"        -> fromJavaxNetSsl,
    )
  }

  case object Default                                                                         extends ClientSSLConfig
  final case class FromCertFile(certPath: String)                                             extends ClientSSLConfig
  final case class FromCertResource(certPath: String)                                         extends ClientSSLConfig
  final case class FromTrustStoreResource(trustStorePath: String, trustStorePassword: Secret) extends ClientSSLConfig
  object FromTrustStoreResource {
    def apply(trustStorePath: String, trustStorePassword: String): FromTrustStoreResource =
      FromTrustStoreResource(trustStorePath, Secret(trustStorePassword))
  }
  final case class FromTrustStoreFile(trustStorePath: String, trustStorePassword: Secret) extends ClientSSLConfig
  object FromTrustStoreFile     {
    def apply(trustStorePath: String, trustStorePassword: String): FromTrustStoreFile =
      FromTrustStoreFile(trustStorePath, Secret(trustStorePassword))
  }

  /**
   * Provide the values needed to instantiate a java.net.ssl.TrustManagerFactory
   * and (optionally) a java.net.ssl.KeyManagerFactory to be used for
   * SSLContext.
   *
   * A valid build must at least specify the trustManagerSource (path or
   * resource).
   *
   * {{{
   * val sslConfig =
   *   FromJavaxNetSsl.builder()
   *     .trustManagerPath("truststore.jks")
   *     .build()
   *
   * if (sslConfig.isValidBuild) ...
   * }}}
   */
  final case class FromJavaxNetSsl(
    keyManagerKeyStoreType: String = "JKS",
    keyManagerSource: FromJavaxNetSsl.Source = FromJavaxNetSsl.Empty,
    keyManagerPassword: Option[String] = None,
    trustManagerKeyStoreType: String = "JKS",
    trustManagerSource: FromJavaxNetSsl.Source = FromJavaxNetSsl.Empty,
    trustManagerPassword: Option[String] = None,
  ) extends ClientSSLConfig { self =>

    /**
     * Indicate if the build is invalid.
     */
    def isInvalidBuild: Boolean = trustManagerSource == FromJavaxNetSsl.Empty

    /**
     * Indicate if the build is valid.
     */
    def isValidBuild: Boolean = !isInvalidBuild

    /**
     * Finalize the build of this FromJavaNetSsl.
     */
    def build(): FromJavaxNetSsl = this

    /**
     * Specify the instance type of the KeyStore to be used by the
     * KeyManagerFactory. Defaults to "JKS".
     */
    def keyManagerKeyStoreType(tpe: String): FromJavaxNetSsl = self.copy(keyManagerKeyStoreType = tpe)

    /**
     * Specify the path to be used to load the KeyStore to be used by the
     * KeyManagerFactory.
     *
     * @note
     *   If a keyManagerResource has been specified it will take precedence over
     *   a path.
     */
    def keyManagerFile(file: String): FromJavaxNetSsl =
      keyManagerSource match {
        case FromJavaxNetSsl.Resource(_) => this
        case _                           => self.copy(keyManagerSource = FromJavaxNetSsl.File(file))
      }

    /**
     * Specify the resource to be used to load the KeyStore to be used by the
     * KeyManagerFactory.
     */
    def keyManagerResource(path: String): FromJavaxNetSsl = self.copy(keyManagerSource = FromJavaxNetSsl.Resource(path))

    /**
     * Specify the password associated with the source that will be used to load
     * the Keystore to be used by the KeyManagerFactory.
     * @param password
     * @return
     */
    def keyManagerPassword(password: String): FromJavaxNetSsl = self.copy(keyManagerPassword = Some(password))

    def trustManagerKeyStoreType(tpe: String): FromJavaxNetSsl = self.copy(trustManagerKeyStoreType = tpe)

    def trustManagerFile(file: String): FromJavaxNetSsl =
      trustManagerSource match {
        case FromJavaxNetSsl.Resource(_) => this
        case _                           => self.copy(trustManagerSource = FromJavaxNetSsl.File(file))
      }

    def trustManagerResource(path: String): FromJavaxNetSsl =
      self.copy(trustManagerSource = FromJavaxNetSsl.Resource(path))

    def trustManagerPassword(password: String): FromJavaxNetSsl = self.copy(trustManagerPassword = Some(password))
  }

  object FromJavaxNetSsl {

    sealed trait Source                         extends Product with Serializable
    case object Empty                           extends Source
    final case class File(file: String)         extends Source
    final case class Resource(resource: String) extends Source

    /**
     * Entrypoint for a FromJavaxNetSsl builder.
     *
     * The odd form insures that the result of .build() will have enough
     * information to be a valid clientSSLConfig.
     */
    def builderWithTrustManagerFile(file: String): FromJavaxNetSsl =
      FromJavaxNetSsl().trustManagerFile(file)

    /**
     * Entrypoint for a FromJavaxNetSsl builder.
     *
     * The odd form insures that the result of .build() will have enough
     * information to be a valid clientSSLConfig.
     */
    def builderWithTrustManagerResource(resource: String): FromJavaxNetSsl =
      FromJavaxNetSsl().trustManagerResource(resource)
  }
}
