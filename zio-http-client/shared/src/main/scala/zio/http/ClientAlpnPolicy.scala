package zio.http

import zio.blocks.schema.Schema

/**
 * Client-side TLS ALPN policy.
 *
 * This governs the *client* end of the handshake (which protocols the client
 * offers via ALPN and whether it tolerates negotiating anything other than
 * `h2`). It is independent from the server-side [[AlpnPolicy]], which governs
 * TLS rejection vs. acceptance on the server: the two types must stay separate.
 *
 * Driver contract (for T14's LoomH2 driver and [[JavaH2Client]]): read
 * [[ClientAlpnPolicy.alpnProtocols]] to build the client's ALPN offer list. TLS
 * connections that negotiate a protocol outside the policy must fail fast;
 * cleartext (`h2c`) handling per policy is documented on each case.
 */
sealed trait ClientAlpnPolicy {

  /**
   * ALPN protocol IDs the client offers on TLS connections, in preference
   * order.
   */
  def alpnProtocols: List[String]
}

object ClientAlpnPolicy {

  /**
   * Offer `h2` first with `http/1.1` fallback. TLS connections use whichever
   * protocol the server negotiates; cleartext connections may use `h2c` upgrade
   * or plain HTTP/1.1. This is the default.
   */
  case object H2PreferredWithH11Fallback extends ClientAlpnPolicy {
    val alpnProtocols: List[String] = List("h2", "http/1.1")
  }

  /**
   * Offer `h2` only and fail fast when the server negotiates anything else
   * (mirrors the server-side `StrictH2` rejection semantics, client-side).
   * Cleartext connections must use `h2c` prior-knowledge only.
   */
  case object StrictH2 extends ClientAlpnPolicy {
    val alpnProtocols: List[String] = List("h2")
  }

  /**
   * H2-only transport: TLS connections negotiate `h2` or fail; cleartext
   * connections use `h2c` prior-knowledge only (no HTTP/1.1 upgrade dance, no
   * HTTP/1.1 fallback). For drivers without prior-knowledge support this
   * behaves like [[StrictH2]] on TLS and rejects cleartext URLs.
   */
  case object H2OnlyH2C extends ClientAlpnPolicy {
    val alpnProtocols: List[String] = List("h2")
  }

  /**
   * Parses a policy by case-object name. Unknown names are rejected with
   * [[IllegalArgumentException]] - never defaulted silently.
   */
  def fromString(name: String): ClientAlpnPolicy =
    name match {
      case "H2PreferredWithH11Fallback" => H2PreferredWithH11Fallback
      case "StrictH2"                   => StrictH2
      case "H2OnlyH2C"                  => H2OnlyH2C
      case other                        =>
        throw new IllegalArgumentException(
          s"Unknown ClientAlpnPolicy: '$other'. Expected one of: H2PreferredWithH11Fallback, StrictH2, H2OnlyH2C",
        )
    }

  implicit val h2PreferredWithH11FallbackSchema: Schema[H2PreferredWithH11Fallback.type] =
    Schema.derived[H2PreferredWithH11Fallback.type]
  implicit val strictH2Schema: Schema[StrictH2.type]                                     = Schema.derived[StrictH2.type]
  implicit val h2OnlyH2CSchema: Schema[H2OnlyH2C.type] = Schema.derived[H2OnlyH2C.type]
  implicit val schema: Schema[ClientAlpnPolicy]        = Schema.derived[ClientAlpnPolicy]
}
