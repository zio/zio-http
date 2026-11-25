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

import zio.Config.Secret
import zio.{Chunk, Config}

/**
 * How a client verifies the certificate of the server it connects to, and the
 * certificate it presents itself for mutual TLS. Set it on a client with
 * `ZClient.Config#ssl`. When none is set, `ClientSSLConfig.Default` is used.
 */
sealed trait ClientSSLConfig

/** The ways to configure client TLS, and a `Config` reader for them. */
object ClientSSLConfig {

  /**
   * Reads a config chosen by the `type` key:
   *
   *   - `Default` and `FromSystemTrustStore` need no other key;
   *   - `FromCertFile` and `FromCertResource` read `cert-path`;
   *   - `FromTrustStoreFile` and `FromTrustStoreResource` read
   *     `trust-store-path` and `trust-store-password`;
   *   - `FromClientAndServerCert` reads a server section `cert.server` (a
   *     `ClientSSLConfig`) and a client section `cert.client` (a
   *     `ClientSSLCertConfig`).
   *
   * `FromCertBytes` and `FromJavaxNetSsl` are not read from config: their
   * `type` values are rejected.
   */
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
    val fromSystemTrustStore    = Config.succeed(FromSystemTrustStore)
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
      "FromSystemTrustStore"    -> fromSystemTrustStore,
      "FromCertFile"            -> fromCertFile,
      "FromCertResource"        -> fromCertResource,
      "FromTrustStoreFile"      -> fromTrustStoreFile,
      "FromTrustStoreResource"  -> fromTrustStoreResource,
      "FromClientAndServerCert" -> fromClientAndServerCert,
    )
  }

  /**
   * The configuration a client uses when none is set with `ZClient.Config#ssl`.
   */
  case object Default extends ClientSSLConfig

  /** Validates server certificates against the JVM's default trust store. */
  case object FromSystemTrustStore extends ClientSSLConfig

  /**
   * Verifies the server certificate against the X.509 certificates in the PEM
   * file `certPath`.
   */
  final case class FromCertFile(certPath: String) extends ClientSSLConfig

  /**
   * Verifies the server certificate against the X.509 certificates in the PEM
   * classpath resource `certPath`.
   */
  final case class FromCertResource(certPath: String)    extends ClientSSLConfig
  // No redacted toString: these are server trust bytes (a public certificate), not secret key material.
  /**
   * Verifies the server certificate against the X.509 certificates in
   * `certBytes`, which hold them in PEM format.
   */
  final case class FromCertBytes(certBytes: Chunk[Byte]) extends ClientSSLConfig

  /** Constructors for `FromCertBytes`. */
  object FromCertBytes {

    /**
     * Creates a config from the PEM bytes in the array, which is copied into a
     * `Chunk`.
     */
    def apply(certBytes: Array[Byte]): FromCertBytes = FromCertBytes(Chunk.fromArray(certBytes))
  }

  /**
   * Verifies the server certificate against the `JKS` trust store in the
   * classpath resource `trustStorePath`, opened with `trustStorePassword`.
   */
  final case class FromTrustStoreResource(trustStorePath: String, trustStorePassword: Secret) extends ClientSSLConfig

  /**
   * Mutual TLS: `serverCertConfig` decides how the server certificate is
   * verified, and `clientCertConfig` provides the certificate and private key
   * the client presents.
   */
  final case class FromClientAndServerCert(
    serverCertConfig: ClientSSLConfig,
    clientCertConfig: ClientSSLCertConfig,
  ) extends ClientSSLConfig

  /**
   * Uses `javax.net.ssl` key stores: an optional one with the client's own key,
   * and one with the certificates trusted for the server. A key store whose
   * source is `Empty` is not used. The methods return a changed copy.
   *
   * @param keyManagerKeyStoreType
   *   the type of the key store with the client key, `JKS` by default
   * @param keyManagerSource
   *   where that key store is loaded from
   * @param keyManagerPassword
   *   its password, if any
   * @param trustManagerKeyStoreType
   *   the type of the trust store, `JKS` by default
   * @param trustManagerSource
   *   where the trust store is loaded from
   * @param trustManagerPassword
   *   its password, if any
   */
  final case class FromJavaxNetSsl(
    keyManagerKeyStoreType: String = "JKS",
    keyManagerSource: FromJavaxNetSsl.Source = FromJavaxNetSsl.Empty,
    keyManagerPassword: Option[Secret] = None,
    trustManagerKeyStoreType: String = "JKS",
    trustManagerSource: FromJavaxNetSsl.Source = FromJavaxNetSsl.Empty,
    trustManagerPassword: Option[Secret] = None,
  ) extends ClientSSLConfig { self =>

    /**
     * Returns `true` when a trust store source is set, that is when
     * `trustManagerSource` is not `Empty`.
     */
    def isValidBuild: Boolean = trustManagerSource != FromJavaxNetSsl.Empty

    /** Returns `true` when no trust store source is set. */
    def isInvalidBuild: Boolean = !isValidBuild

    /** Returns this value; it ends a chain of builder calls. */
    def build(): FromJavaxNetSsl = this

    /**
     * Returns a copy with the key store type `tpe` for the client key, for
     * example `PKCS12`.
     */
    def keyManagerKeyStoreType(tpe: String): FromJavaxNetSsl = self.copy(keyManagerKeyStoreType = tpe)

    /**
     * Returns a copy that loads the client key store from the file `file`. A
     * value whose key store source is a classpath resource is returned
     * unchanged.
     */
    def keyManagerFile(file: String): FromJavaxNetSsl =
      keyManagerSource match {
        case FromJavaxNetSsl.Resource(_) => this
        case _                           => self.copy(keyManagerSource = FromJavaxNetSsl.File(file))
      }

    /**
     * Returns a copy that loads the client key store from the classpath
     * resource `path`.
     */
    def keyManagerResource(path: String): FromJavaxNetSsl = self.copy(keyManagerSource = FromJavaxNetSsl.Resource(path))

    /** Returns a copy with the password of the client key store. */
    def keyManagerPassword(password: Secret): FromJavaxNetSsl = self.copy(keyManagerPassword = Some(password))

    /** Returns a copy with the password of the client key store. */
    def keyManagerPassword(password: String): FromJavaxNetSsl = keyManagerPassword(Secret(password))

    /** Returns a copy with the trust store type `tpe`, for example `PKCS12`. */
    def trustManagerKeyStoreType(tpe: String): FromJavaxNetSsl = self.copy(trustManagerKeyStoreType = tpe)

    /**
     * Returns a copy that loads the trust store from the file `file`. A value
     * whose trust store source is a classpath resource is returned unchanged.
     */
    def trustManagerFile(file: String): FromJavaxNetSsl =
      trustManagerSource match {
        case FromJavaxNetSsl.Resource(_) => this
        case _                           => self.copy(trustManagerSource = FromJavaxNetSsl.File(file))
      }

    /**
     * Returns a copy that loads the trust store from the classpath resource
     * `path`.
     */
    def trustManagerResource(path: String): FromJavaxNetSsl =
      self.copy(trustManagerSource = FromJavaxNetSsl.Resource(path))

    /** Returns a copy with the password of the trust store. */
    def trustManagerPassword(password: Secret): FromJavaxNetSsl = self.copy(trustManagerPassword = Some(password))

    /** Returns a copy with the password of the trust store. */
    def trustManagerPassword(password: String): FromJavaxNetSsl = trustManagerPassword(Secret(password))
  }

  /**
   * The sources a key store can be loaded from, and builders for
   * `FromJavaxNetSsl`.
   */
  object FromJavaxNetSsl {

    /** Where a key store is loaded from: `Empty`, `File` or `Resource`. */
    sealed trait Source extends Product with Serializable

    /** No key store. */
    case object Empty extends Source

    /** A key store in the file `file`. */
    final case class File(file: String) extends Source

    /** A key store in the classpath resource `resource`. */
    final case class Resource(resource: String) extends Source

    /**
     * Creates a config whose trust store is loaded from the file `file`, with
     * the defaults for everything else.
     */
    def builderWithTrustManagerFile(file: String): FromJavaxNetSsl =
      FromJavaxNetSsl().trustManagerFile(file)

    /**
     * Creates a config whose trust store is loaded from the classpath resource
     * `resource`, with the defaults for everything else.
     */
    def builderWithTrustManagerResource(resource: String): FromJavaxNetSsl =
      FromJavaxNetSsl().trustManagerResource(resource)
  }

  /** Constructors for `FromTrustStoreResource`. */
  object FromTrustStoreResource {

    /** Creates a config with the password given as a `String`. */
    def apply(trustStorePath: String, trustStorePassword: String): FromTrustStoreResource =
      FromTrustStoreResource(trustStorePath, Secret(trustStorePassword))
  }

  /**
   * Verifies the server certificate against the `JKS` trust store in the file
   * `trustStorePath`, opened with `trustStorePassword`.
   */
  final case class FromTrustStoreFile(trustStorePath: String, trustStorePassword: Secret) extends ClientSSLConfig

  /** Constructors for `FromTrustStoreFile`. */
  object FromTrustStoreFile {

    /** Creates a config with the password given as a `String`. */
    def apply(trustStorePath: String, trustStorePassword: String): FromTrustStoreFile =
      FromTrustStoreFile(trustStorePath, Secret(trustStorePassword))
  }
}
