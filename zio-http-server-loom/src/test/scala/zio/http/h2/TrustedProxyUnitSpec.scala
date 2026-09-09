package zio.http.h2

import scala.annotation.experimental

import zio.test._

import zio.http.TrustedProxyConfig

/**
 * Unit tests for the fail-closed numeric trust model: allowlist entries that
 * are not literal IPs or strict CIDRs (hostnames, malformed CIDRs) must never
 * match, and unparseable peer IPs must never be trusted — with zero DNS
 * resolution at request time.
 */
@experimental
object TrustedProxyUnitSpec extends ZIOSpecDefault {

  override def spec: Spec[Any, Any] =
    suite("TrustedProxyUnitSpec")(
      test("hostname allowlist entry never matches (no DNS trust)") {
        val config = TrustedProxyConfig(trustedCidrs = Set("localhost", "example.com"))
        assertTrue(
          !config.isTrusted("127.0.0.1", hasPeerCert = false),
          !config.isTrusted("10.0.0.1", hasPeerCert = false),
        )
      },
      test("malformed CIDR entries never match") {
        val config = TrustedProxyConfig(
          trustedCidrs = Set("10.0.0.0/33", "10.0.0.0/-1", "10.0.0.0/abc", "10.0.0.0/", "/24", "999.1.1.1/24"),
        )
        assertTrue(
          !config.isTrusted("10.0.0.1", hasPeerCert = false),
          !config.isTrusted("127.0.0.1", hasPeerCert = false),
        )
      },
      test("valid CIDR and literal entries still match") {
        val config = TrustedProxyConfig(trustedCidrs = Set("10.0.0.0/8", "127.0.0.1", "::1/128"))
        assertTrue(
          config.isTrusted("10.1.2.3", hasPeerCert = false),
          config.isTrusted("127.0.0.1", hasPeerCert = false),
          config.isTrusted("::1", hasPeerCert = false),
          !config.isTrusted("11.0.0.1", hasPeerCert = false),
          !config.isTrusted("::2", hasPeerCert = false),
        )
      },
      test("garbage peerIp is never trusted") {
        val config = TrustedProxyConfig(trustedCidrs = Set("10.0.0.0/8", "0.0.0.0/0"))
        assertTrue(
          !config.isTrusted("not-an-ip", hasPeerCert = false),
          !config.isTrusted("", hasPeerCert = false),
          !config.isTrusted("evil.com", hasPeerCert = false),
        )
      },
    )
}
