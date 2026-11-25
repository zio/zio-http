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

import zio.http.SSLConfig._

import com.lihaoyi.unroll

/**
 * Whether a TLS server asks the connecting client for a certificate (mutual TLS). It is the type of `SSLConfig.clientAuth`.
 */
sealed trait ClientAuth

/** The client authentication modes: `Required`, `NoneClientAuth` and `Optional`. */
object ClientAuth {
  /** The server requires the client to present a certificate. */
  case object Required       extends ClientAuth
  /** The server does not ask the client for a certificate. */
  case object NoneClientAuth extends ClientAuth
  /**
   * The server asks the client for a certificate but also accepts clients that present none.
   */
  case object Optional       extends ClientAuth

}

/**
 * The TLS settings of a server: where its certificate and private key come from, what it does with plain HTTP requests on a TLS port, which TLS implementation and protocols it uses, and whether it asks clients for a certificate.
 *
 * The companion object has constructors that fill in the defaults (`SSLConfig.fromFile`, `SSLConfig.fromResource`, `SSLConfig.generate`, ...). Set it on a server with `Server.Config#ssl`.
 *
 * {{{
 * val config: SSLConfig = SSLConfig.fromFile("server-cert.pem", "server-key.pem", ClientAuth.Required)
 * val protocols: Seq[String] = config.protocols // List(TLSv1.3, TLSv1.2)
 * }}}
 *
 * @param behaviour
 *   what the server does with a plain (non-TLS) HTTP request on the TLS port
 * @param data
 *   where the server certificate and private key come from
 * @param provider
 *   the TLS implementation, `Provider.JDK` or `Provider.OpenSSL`
 * @param clientAuth
 *   whether to ask clients for a certificate. With `None`, no client authentication setting is applied to the TLS context.
 * @param includeClientCert
 *   when `true`, the server reads the client's certificate from the TLS session and sets the first one as `Request#remoteCertificate`. It defaults to `false`.
 * @param protocols
 *   the enabled TLS protocol names. The default is `TLSv1.3` and `TLSv1.2`.
 *
 * @see [[https://zio.dev/zio-http/reference/server/ Server]]
 */
final case class SSLConfig(
  behaviour: HttpBehaviour,
  data: Data,
  provider: Provider,
  clientAuth: Option[ClientAuth] = None,
  includeClientCert: Boolean = false,
  protocols: Seq[String] = Seq("TLSv1.3", "TLSv1.2"),
) {
  // @unroll annotation does not work here, doing manual unroll for binary compatibility

  /** Creates a config with the default `protocols`, `TLSv1.3` and `TLSv1.2`. */
  def this(
    behaviour: HttpBehaviour,
    data: Data,
    provider: Provider,
    clientAuth: Option[ClientAuth],
    includeClientCert: Boolean,
  ) = this(behaviour, data, provider, clientAuth, includeClientCert, Seq("TLSv1.3", "TLSv1.2"))

  /** Returns a copy with the given fields replaced. */
  def copy(
    behaviour: HttpBehaviour = this.behaviour,
    data: Data = this.data,
    provider: Provider = this.provider,
    clientAuth: Option[ClientAuth] = this.clientAuth,
    includeClientCert: Boolean = this.includeClientCert,
    protocols: Seq[String] = this.protocols,
  ): SSLConfig = SSLConfig(behaviour, data, provider, clientAuth, includeClientCert, protocols)

  /**
   * Returns a copy with the given fields replaced. The copy has the `protocols` of this config.
   */
  def copy(
    behaviour: HttpBehaviour,
    data: Data,
    provider: Provider,
    clientAuth: Option[ClientAuth],
    includeClientCert: Boolean,
  ): SSLConfig = SSLConfig(behaviour, data, provider, clientAuth, includeClientCert, this.protocols)
}

/**
 * Constructors and the `Config` reader for `SSLConfig`, and the types of its fields: `HttpBehaviour`, `Data` and `Provider`.
 */
object SSLConfig {

  /**
   * Creates a config for `data` with `HttpBehaviour.Redirect`, `Provider.JDK`, no client authentication and the default protocols.
   */
  def apply(data: Data): SSLConfig =
    new SSLConfig(HttpBehaviour.Redirect, data, Provider.JDK, None)

  /**
   * Creates a config for `data` with the given client authentication, `HttpBehaviour.Redirect`, `Provider.JDK` and the default protocols.
   */
  def apply(data: Data, clientAuth: ClientAuth): SSLConfig =
    new SSLConfig(HttpBehaviour.Redirect, data, Provider.JDK, Some(clientAuth))

  /** Creates a config with the given fields and the default protocols. */
  def apply(
    behaviour: HttpBehaviour,
    data: Data,
    provider: Provider,
    clientAuth: Option[ClientAuth],
    includeClientCert: Boolean,
  ): SSLConfig = new SSLConfig(behaviour, data, provider, clientAuth, includeClientCert)

  /**
   * Reads a config from the keys `behaviour` (`accept`, `fail` or `redirect`), `data` (see `SSLConfig.Data.config`) and `provider` (`jdk` or `openssl`).
   *
   * `clientAuth`, `includeClientCert` and `protocols` are not read: they keep their defaults, `None`, `false` and `TLSv1.3`, `TLSv1.2`.
   */
  val config: Config[SSLConfig] =
    (
      HttpBehaviour.config.nested("behaviour") ++
        Data.config.nested("data") ++
        Provider.config.nested("provider")
    ).map { case (behaviour, data, provider) =>
      SSLConfig(behaviour, data, provider)
    }

  /**
   * Creates a config that loads the PEM certificate chain from the file `certPath` and the PEM private key from the file `keyPath`. It uses `HttpBehaviour.Redirect` and `Provider.JDK`.
   */
  def fromFile(certPath: String, keyPath: String): SSLConfig =
    fromFile(HttpBehaviour.Redirect, certPath, keyPath)

  /** Like `fromFile(certPath, keyPath)`, with the given client authentication. */
  def fromFile(certPath: String, keyPath: String, clientAuth: ClientAuth): SSLConfig =
    fromFile(HttpBehaviour.Redirect, certPath, keyPath, Some(clientAuth))

  /**
   * Creates a config that loads the PEM certificate chain from the file `certPath` and the PEM private key from the file `keyPath`.
   *
   * @param trustCertCollectionPath
   *   a PEM file with the certificates used to verify client certificates, if any
   * @param includeClientCert
   *   see `SSLConfig#includeClientCert`
   */
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

  /**
   * Creates a config that loads the PEM certificate chain and the PEM private key from the classpath resources `certPath` and `keyPath`. It uses `HttpBehaviour.Redirect` and `Provider.JDK`.
   */
  def fromResource(certPath: String, keyPath: String): SSLConfig =
    fromResource(HttpBehaviour.Redirect, certPath, keyPath, None)

  /** Like `fromResource(certPath, keyPath)`, with the given client authentication. */
  def fromResource(certPath: String, keyPath: String, clientAuth: ClientAuth): SSLConfig =
    fromResource(HttpBehaviour.Redirect, certPath, keyPath, Some(clientAuth))

  /**
   * Creates a config that loads the PEM certificate chain and the PEM private key from the classpath resources `certPath` and `keyPath`.
   *
   * @param trustCertCollectionPath
   *   a classpath resource with the PEM certificates used to verify client certificates, if any
   * @param includeClientCert
   *   see `SSLConfig#includeClientCert`
   */
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

  /**
   * Creates a config that loads the server key from the `javax.net.ssl` key store file `keyManagerFile`.
   *
   * @param keyManagerPassword
   *   the key store password, if any
   * @param keyManagerKeyStoreType
   *   the key store type, `JKS` by default
   * @param trustManagerKeyStore
   *   a key store with the certificates used to verify client certificates, if any
   */
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

  /**
   * Creates a config that loads the server key from the `JKS` key store file `keyManagerFile`, protected by `keyManagerPassword`.
   */
  def fromJavaxNetSslKeyStoreFile(keyManagerFile: String, keyManagerPassword: Secret): SSLConfig =
    fromJavaxNetSslKeyStoreFile(keyManagerFile, Some(keyManagerPassword))

  /**
   * Creates a config that loads the server key from the `javax.net.ssl` key store at the classpath resource `keyManagerResource`. It uses `HttpBehaviour.Redirect`.
   *
   * @param keyManagerPassword
   *   the key store password, if any
   * @param keyManagerKeyStoreType
   *   the key store type, `JKS` by default
   * @param trustManagerKeyStore
   *   a key store with the certificates used to verify client certificates, if any
   */
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

  /**
   * Creates a config from a `Data.FromJavaxNetSsl` description of the key store and trust store.
   */
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

  /**
   * Creates a config that loads the server key from the `JKS` key store at the classpath resource `keyManagerResource`, protected by `keyManagerPassword`.
   */
  def fromJavaxNetSslKeyStoreResource(keyManagerResource: String, keyManagerPassword: Secret): SSLConfig =
    fromJavaxNetSslKeyStoreResource(keyManagerResource, Some(keyManagerPassword))

  /**
   * Generates a self-signed certificate at runtime, for testing and development
   * only. Requires `"io.netty" % "netty-pkitesting"` on the classpath: zio-http
   * declares it as an optional dependency, so it has to be added explicitly
   * (`zio-http-testkit` brings it).
   */
  def generate: SSLConfig =
    generate(HttpBehaviour.Redirect, None)

  /** Like `generate`, and needs the same `netty-pkitesting` dependency. */
  def generate(clientAuth: ClientAuth): SSLConfig =
    generate(HttpBehaviour.Redirect, Some(clientAuth))

  /** Like `generate`, and needs the same `netty-pkitesting` dependency. */
  def generate(behaviour: HttpBehaviour, clientAuth: Option[ClientAuth] = None): SSLConfig =
    new SSLConfig(behaviour, Data.Generate, Provider.JDK, clientAuth)

  /**
   * What a TLS server does with a connection that starts with plain HTTP instead of a TLS handshake: `Accept`, `Fail` or `Redirect`.
   */
  sealed trait HttpBehaviour
  /** The behaviours for plain HTTP requests on a TLS port, and their `Config` reader. */
  object HttpBehaviour {
    /** The server handles the request as plain HTTP. */
    case object Accept   extends HttpBehaviour
    /** The server answers with `406 Not Acceptable` and closes the connection. */
    case object Fail     extends HttpBehaviour
    /**
     * The server answers with `308 Permanent Redirect` and closes the connection. The `Location` header is `https://` followed by the value of the request's `Host` header, and is omitted when the request has no `Host` header.
     */
    case object Redirect extends HttpBehaviour

    /**
     * Reads one of the strings `accept`, `fail` or `redirect`. Any other value fails with a message that starts with `Invalid Http behaviour:`.
     */
    val config: Config[HttpBehaviour] =
      Config.string.mapOrFail {
        case "accept"   => Right(Accept)
        case "fail"     => Right(Fail)
        case "redirect" => Right(Redirect)
        case other      => Left(Config.Error.InvalidData(message = s"Invalid Http behaviour: $other"))
      }
  }

  /**
   * Where a server's certificate and private key come from: `Generate`, `FromFile`, `FromResource` or `FromJavaxNetSsl`.
   */
  sealed trait Data
  /** The sources of a server certificate, and their `Config` reader. */
  object Data {

    /**
     * A new public/private key pair will be generated and self-signed. Useful
     * for testing/developer mode. Requires `io.netty:netty-pkitesting` on the
     * classpath (see `SSLConfig.generate`).
     */
    case object Generate extends Data

    /**
     * Loads the PEM certificate chain from the file `certPath` and the PEM private key from the file `keyPath`. `trustCertCollectionPath` is an optional PEM file with the certificates used to verify client certificates.
     */
    final case class FromFile(certPath: String, keyPath: String, trustCertCollectionPath: Option[String]) extends Data

    /**
     * Loads the PEM certificate chain and the PEM private key from the classpath resources `certPath` and `keyPath`. `trustCertCollectionPath` is an optional classpath resource with the certificates used to verify client certificates.
     */
    final case class FromResource(certPath: String, keyPath: String, trustCertCollectionPath: Option[String])
        extends Data

    /**
     * A key store with the certificates a server trusts when it verifies client certificates, for use in `FromJavaxNetSsl`. The methods return a changed copy.
     *
     * @param trustManagerKeyStoreType
     *   the key store type, `JKS` by default
     * @param trustManagerSource
     *   where the key store is loaded from
     * @param trustManagerPassword
     *   the key store password, if any
     */
    final case class TrustManagerKeyStore(
      trustManagerKeyStoreType: String = "JKS",
      trustManagerSource: FromJavaxNetSsl.Source,
      trustManagerPassword: Option[Secret] = None,
    ) { self =>
      /** Returns this key store; it ends a chain of builder calls. */
      def build(): TrustManagerKeyStore = this

      /** Returns a copy with the key store type `tpe`, for example `PKCS12`. */
      def trustManagerKeyStoreType(tpe: String): TrustManagerKeyStore = self.copy(trustManagerKeyStoreType = tpe)

      /**
       * Returns a copy that loads the key store from the file `file`. A key store that already loads from a classpath resource is returned unchanged.
       */
      def trustManagerFile(file: String): TrustManagerKeyStore =
        trustManagerSource match {
          case FromJavaxNetSsl.Resource(_) => this
          case _                           => self.copy(trustManagerSource = FromJavaxNetSsl.File(file))
        }

      /** Returns a copy that loads the key store from the classpath resource `path`. */
      def trustManagerResource(path: String): TrustManagerKeyStore =
        self.copy(trustManagerSource = FromJavaxNetSsl.Resource(path))

      /** Returns a copy with the given key store password. */
      def trustManagerPassword(password: Secret): TrustManagerKeyStore =
        self.copy(trustManagerPassword = Some(password))

      /** Returns a copy with the given key store password. */
      def trustManagerPassword(password: String): TrustManagerKeyStore = trustManagerPassword(Secret(password))
    }

    /** Constructors for `TrustManagerKeyStore`. */
    object TrustManagerKeyStore {
      /** Creates a trust key store loaded from the file `file`. The type defaults to `JKS`. */
      def fromFile(
        file: String,
        trustManagerPassword: Option[Secret] = None,
        trustManagerKeyStoreType: String = "JKS",
      ): TrustManagerKeyStore =
        TrustManagerKeyStore(trustManagerKeyStoreType, FromJavaxNetSsl.File(file), trustManagerPassword)

      /**
       * Creates a trust key store loaded from the classpath resource `resource`. The type defaults to `JKS`.
       */
      def fromResource(
        resource: String,
        trustManagerPassword: Option[Secret] = None,
        trustManagerKeyStoreType: String = "JKS",
      ): TrustManagerKeyStore =
        TrustManagerKeyStore(trustManagerKeyStoreType, FromJavaxNetSsl.Resource(resource), trustManagerPassword)

    }

    /**
     * Loads the server certificate and private key from a `javax.net.ssl` key store, with an optional trust store for verifying client certificates. The methods return a changed copy.
     *
     * @param keyManagerKeyStoreType
     *   the type of the key store, `JKS` by default
     * @param keyManagerSource
     *   where the key store is loaded from
     * @param keyManagerPassword
     *   the key store password, if any
     * @param trustManagerKeyStore
     *   the key store with the certificates used to verify client certificates, if any
     */
    final case class FromJavaxNetSsl(
      keyManagerKeyStoreType: String = "JKS",
      keyManagerSource: FromJavaxNetSsl.Source,
      keyManagerPassword: Option[Secret] = None,
      trustManagerKeyStore: Option[TrustManagerKeyStore] = None,
    ) extends Data { self =>
      /** Returns this value; it ends a chain of builder calls. */
      def build(): FromJavaxNetSsl                             = this
      /** Returns a copy with the key store type `tpe`, for example `PKCS12`. */
      def keyManagerKeyStoreType(tpe: String): FromJavaxNetSsl = self.copy(keyManagerKeyStoreType = tpe)

      /**
       * Returns a copy that loads the key store from the file `file`. A value that already loads from a classpath resource is returned unchanged.
       */
      def keyManagerFile(file: String): FromJavaxNetSsl         =
        keyManagerSource match {
          case FromJavaxNetSsl.Resource(_) => this
          case _                           => self.copy(keyManagerSource = FromJavaxNetSsl.File(file))
        }
      /** Returns a copy that loads the key store from the classpath resource `path`. */
      def keyManagerResource(path: String): FromJavaxNetSsl     =
        self.copy(keyManagerSource = FromJavaxNetSsl.Resource(path))
      /** Returns a copy with the given key store password. */
      def keyManagerPassword(password: Secret): FromJavaxNetSsl = self.copy(keyManagerPassword = Some(password))
      /** Returns a copy with the given key store password. */
      def keyManagerPassword(password: String): FromJavaxNetSsl = keyManagerPassword(Secret(password))
      /** Returns a copy with the given trust key store. */
      def trustManagerKeyStore(trustManagerKeyStore: TrustManagerKeyStore): FromJavaxNetSsl =
        self.copy(trustManagerKeyStore = Some(trustManagerKeyStore))

    }

    /** The sources a `javax.net.ssl` key store can be loaded from. */
    object FromJavaxNetSsl {

      /** Where a key store is loaded from: `File` or `Resource`. */
      sealed trait Source                         extends Product with Serializable
      /** A key store in the file `file`. */
      final case class File(file: String)         extends Source
      /** A key store in the classpath resource `resource`. */
      final case class Resource(resource: String) extends Source
    }

    /**
     * Reads a `Data` from one of three shapes, tried in this order:
     *
     *   - the string `generate` gives `Generate`;
     *   - the keys `certPath` and `keyPath`, and optionally `trust-cert-collection-path`, give `FromFile`;
     *   - the keys `cert-resource` and `key-resource`, and optionally `trust-cert-collection-resource`, give `FromResource`.
     *
     * `FromJavaxNetSsl` is not read from config.
     */
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

  /**
   * The TLS implementation of a server, passed to Netty as `SslProvider.JDK` or `SslProvider.OPENSSL`.
   */
  sealed trait Provider
  /** The TLS implementations, and their `Config` reader. */
  object Provider {
    /** The TLS implementation of the JDK. */
    case object JDK     extends Provider
    /** The OpenSSL based TLS implementation of Netty. */
    case object OpenSSL extends Provider

    /**
     * Reads one of the strings `jdk` or `openssl`. Any other value fails with a message that starts with `Invalid Provider:`.
     */
    val config: Config[Provider] =
      Config.string.mapOrFail {
        case "jdk"     => Right(JDK)
        case "openssl" => Right(OpenSSL)
        case other     => Left(Config.Error.InvalidData(message = s"Invalid Provider: $other"))
      }
  }
}
