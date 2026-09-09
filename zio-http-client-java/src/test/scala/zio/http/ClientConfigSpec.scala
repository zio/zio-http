package zio.http

import java.time.Duration

import zio.test._

/**
 * Todo 13: explicit client config surfaces (`ClientTlsConfig` /
 * `ClientAlpnPolicy` / `PoolConfig` / `DeadlineConfig` on `ClientConfig`).
 *
 * Proves, without touching `Client.send` (signature unchanged) or any
 * server/endpoint/loom surface: config defaults, Schema round-trip of a
 * fully-populated `ClientConfig` (via `toDynamicValue`/`fromDynamicValue`),
 * fail-fast validation of every invalid value, and the ALPN policy values
 * (including rejection - never silent defaulting - of unknown policy names).
 */
object ClientConfigSpec extends ZIOSpecDefault {

  private val full: ClientConfig =
    ClientConfig(
      connectTimeout = Duration.ofSeconds(5),
      requestTimeout = Duration.ofSeconds(15),
      followRedirects = false,
      maxRedirects = 3,
      tls = Some(
        ClientTlsConfig(
          trust = ClientTrustSource.TrustStore("/etc/ssl/trust.p12", Some("s3cret")),
          key = Some(ClientKeySource.PemKey("CERT-CHAIN", "PRIVATE-KEY")),
          tlsVersions = List("TLSv1.3"),
        ),
      ),
      alpn = ClientAlpnPolicy.StrictH2,
      pool = PoolConfig(
        maxPerHost = 4,
        maxTotal = 40,
        idleTimeout = Duration.ofSeconds(30),
        queueSize = 100,
      ),
      deadline = DeadlineConfig(
        connectTimeout = Some(Duration.ofSeconds(7)),
        requestTimeout = None,
        streamTimeout = Some(Duration.ofSeconds(60)),
      ),
    )

  /** True only when `thunk` fails fast with IllegalArgumentException. */
  private def rejects(thunk: => Any): Boolean =
    try {
      thunk
      false
    } catch {
      case _: IllegalArgumentException => true
    }

  def spec = suite("ClientConfig explicit surfaces")(
    suite("defaults")(
      test("top-level legacy fields keep their historical defaults") {
        val cfg = ClientConfig()
        assertTrue(
          cfg.connectTimeout == Duration.ofSeconds(10),
          cfg.requestTimeout == Duration.ofSeconds(30),
          cfg.followRedirects,
          cfg.maxRedirects == 5,
        )
      },
      test("tls defaults to None (plaintext unless the URL scheme says otherwise)") {
        assertTrue(ClientConfig().tls.isEmpty)
      },
      test("alpn defaults to H2PreferredWithH11Fallback") {
        assertTrue(ClientConfig().alpn == ClientAlpnPolicy.H2PreferredWithH11Fallback)
      },
      test("pool defaults to maxPerHost=10, maxTotal=100, idleTimeout=60s, queueSize=1000") {
        val pool = ClientConfig().pool
        assertTrue(
          pool.maxPerHost == 10,
          pool.maxTotal == 100,
          pool.idleTimeout == Duration.ofSeconds(60),
          pool.queueSize == 1000,
        )
      },
      test("deadline defaults to all-None (inherit top-level, stream disabled)") {
        val deadline = ClientConfig().deadline
        assertTrue(
          deadline.connectTimeout.isEmpty,
          deadline.requestTimeout.isEmpty,
          deadline.streamTimeout.isEmpty,
        )
      },
    ),
    suite("Schema round-trip")(
      test("fully-populated ClientConfig survives toDynamicValue/fromDynamicValue") {
        val back = ClientConfig.schema.fromDynamicValue(ClientConfig.schema.toDynamicValue(full))
        assertTrue(back == Right(full))
      },
      test("every ClientAlpnPolicy value round-trips through its Schema") {
        val policies: List[ClientAlpnPolicy] = List(
          ClientAlpnPolicy.H2PreferredWithH11Fallback,
          ClientAlpnPolicy.StrictH2,
          ClientAlpnPolicy.H2OnlyH2C,
        )
        assertTrue(
          policies.forall { policy =>
            val back = ClientAlpnPolicy.schema.fromDynamicValue(ClientAlpnPolicy.schema.toDynamicValue(policy))
            back == Right(policy)
          },
        )
      },
      test("JSON dump of a fully-populated config shows every surface") {
        val dump = ClientConfig.schema.toDynamicValue(full).toJson.toString
        println("CONFIG-DUMP-BEGIN")
        println(dump)
        println("CONFIG-DUMP-END")
        assertTrue(
          dump.contains("connectTimeout"),
          dump.contains("requestTimeout"),
          dump.contains("followRedirects"),
          dump.contains("maxRedirects"),
          dump.contains("tls"),
          dump.contains("alpn"),
          dump.contains("pool"),
          dump.contains("deadline"),
          dump.contains("trust"),
          dump.contains("tlsVersions"),
          dump.contains("StrictH2"),
          dump.contains("TLSv1.3"),
          dump.contains("maxPerHost"),
          dump.contains("maxTotal"),
          dump.contains("idleTimeout"),
          dump.contains("queueSize"),
          dump.contains("streamTimeout"),
        )
      },
    ),
    suite("validation rejects invalid values")(
      test("PoolConfig rejects maxPerHost=0") {
        assertTrue(rejects(PoolConfig(maxPerHost = 0)))
      },
      test("PoolConfig rejects negative maxPerHost") {
        assertTrue(rejects(PoolConfig(maxPerHost = -2)))
      },
      test("PoolConfig rejects maxTotal=0") {
        assertTrue(rejects(PoolConfig(maxTotal = 0)))
      },
      test("PoolConfig rejects maxTotal below maxPerHost") {
        assertTrue(rejects(PoolConfig(maxPerHost = 10, maxTotal = 9)))
      },
      test("PoolConfig rejects negative queueSize") {
        assertTrue(rejects(PoolConfig(queueSize = -1)))
      },
      test("PoolConfig rejects zero and negative idleTimeout") {
        assertTrue(
          rejects(PoolConfig(idleTimeout = Duration.ZERO)) && rejects(PoolConfig(idleTimeout = Duration.ofSeconds(-1))),
        )
      },
      test("ClientTlsConfig rejects empty tlsVersions") {
        assertTrue(rejects(ClientTlsConfig(tlsVersions = Nil)))
      },
      test("DeadlineConfig rejects non-positive overrides") {
        assertTrue(
          rejects(DeadlineConfig(connectTimeout = Some(Duration.ZERO))),
          rejects(DeadlineConfig(requestTimeout = Some(Duration.ofSeconds(-5)))),
          rejects(DeadlineConfig(streamTimeout = Some(Duration.ZERO))),
        )
      },
      test("ClientConfig rejects negative maxRedirects") {
        assertTrue(rejects(ClientConfig(maxRedirects = -1)))
      },
    ),
    suite("ClientAlpnPolicy values")(
      test("H2PreferredWithH11Fallback offers h2 first with http/1.1 fallback") {
        assertTrue(ClientAlpnPolicy.H2PreferredWithH11Fallback.alpnProtocols == List("h2", "http/1.1"))
      },
      test("StrictH2 offers h2 only") {
        assertTrue(ClientAlpnPolicy.StrictH2.alpnProtocols == List("h2"))
      },
      test("H2OnlyH2C offers h2 only") {
        assertTrue(ClientAlpnPolicy.H2OnlyH2C.alpnProtocols == List("h2"))
      },
      test("fromString resolves every policy by name") {
        assertTrue(
          ClientAlpnPolicy.fromString("H2PreferredWithH11Fallback") == ClientAlpnPolicy.H2PreferredWithH11Fallback,
          ClientAlpnPolicy.fromString("StrictH2") == ClientAlpnPolicy.StrictH2,
          ClientAlpnPolicy.fromString("H2OnlyH2C") == ClientAlpnPolicy.H2OnlyH2C,
        )
      },
      test("fromString rejects unknown names instead of defaulting silently") {
        assertTrue(
          rejects(ClientAlpnPolicy.fromString("h3")),
          rejects(ClientAlpnPolicy.fromString("H2")),
          rejects(ClientAlpnPolicy.fromString("")),
          rejects(ClientAlpnPolicy.fromString("http/1.1")),
        )
      },
    ),
    suite("deadline precedence reuses top-level fields")(
      test("deadline override wins over the top-level connectTimeout") {
        assertTrue(full.effectiveConnectTimeout == Duration.ofSeconds(7))
      },
      test("absent override inherits the top-level requestTimeout") {
        assertTrue(full.effectiveRequestTimeout == Duration.ofSeconds(15))
      },
      test("streamTimeout surfaces the deadline override (None means disabled)") {
        assertTrue(
          full.effectiveStreamTimeout == Some(Duration.ofSeconds(60)),
          ClientConfig().effectiveStreamTimeout.isEmpty,
        )
      },
      test("defaults inherit the legacy top-level timeouts") {
        val cfg = ClientConfig()
        assertTrue(
          cfg.effectiveConnectTimeout == cfg.connectTimeout,
          cfg.effectiveRequestTimeout == cfg.requestTimeout,
        )
      },
    ),
    suite("driver wiring")(
      test("JavaH2Client builds from default and TLS-free configs") {
        val fromDefault = JavaH2Client.default
        val fromPlain   = JavaH2Client(full.copy(tls = None))
        assertTrue(fromDefault != null, fromPlain != null)
      },
      test("JavaH2Client with unloadable TLS material fails fast at construction, not first use") {
        // MINOR-4 timing shift: the `full` fixture points at a bogus trust
        // path and bogus PEM, so construction itself throws naming the file -
        // pinned in detail by ClientTlsFailFastSpec.
        assertTrue(rejects(JavaH2Client(full)))
      },
    ),
  )
}
