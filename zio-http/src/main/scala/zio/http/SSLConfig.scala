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
