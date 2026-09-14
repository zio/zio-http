package zio.http

import scala.compiletime.testing.{typeCheckErrors, typeChecks}

import zio.test._

/**
 * Scala 3 compile-time proof of connectors-first exact coverage.
 *
 * `LoomServer.connectors` accumulates the required version set at type level
 * and `serveWith` compiles only on an exact match (pairwise `=!=` plus
 * both-directions `<:<`). This spec pins the matrix: well-formed listings (and
 * the config-flag conditional pattern) typecheck, while missing, extra,
 * duplicate, ABA-repeat, zero-connector, and zero-engine shapes do not. The
 * `=!=` evidence is a small local definition (same syntax as `zio.=!=`, but
 * core server packages take on no new dependency for this), and
 * `compiletime.testing` exists only on Scala 3, so this spec lives in the Scala
 * 3 test sources.
 */

object TypedEngineNegationSpec extends ZIOSpecDefault {

  private final class StubEngine[V <: Version](val protocol: V) extends ProtocolEngine[V] {
    val transportKind: TransportKind = TransportKind.Tcp
    def drain(): Unit                = ()
    def close(): Unit                = ()
  }

  private val h1a: ProtocolEngine[Version.`HTTP/1.1`.type] =
    new StubEngine(Version.`HTTP/1.1`)

  private val h1b: ProtocolEngine[Version.`HTTP/1.1`.type] =
    new StubEngine(Version.`HTTP/1.1`)

  private val h2a: ProtocolEngine[Version.`HTTP/2.0`.type] =
    new StubEngine(Version.`HTTP/2.0`)

  private val h2b: ProtocolEngine[Version.`HTTP/2.0`.type] =
    new StubEngine(Version.`HTTP/2.0`)

  private val h3: ProtocolEngine[Version.`HTTP/3.0`.type] =
    new StubEngine(Version.`HTTP/3.0`)

  private def h2c(port: Int): H2CConnector =
    new H2CConnector(bind = BindAddress.localhost(port))

  private def h3c(port: Int, tls: TlsConfig): H3Connector =
    new H3Connector(bind = BindAddress.localhost(port), tls = tls)

  override def spec = suite("TypedEngineNegationSpec")(
    test("single connector and engine typecheck") {
      assertTrue(
        typeChecks("LoomServer.connectors(h2c(0)).serveWith(h2a)"),
      )
    },
    test("dual connectors sharing one engine typecheck") {
      assertTrue(
        typeChecks("LoomServer.connectors(h2c(0)).addConnector(h2c(1)).serveWith(h2a)"),
      )
    },
    test("two versions typecheck") {
      assertTrue(
        typeChecks("LoomServer.connectors(h2c(0)).addConnector(h3c(0, tlsHole)).serveWith(h2a, h3)"),
      )
    },
    test("TLS H2 connector maps to HTTP/2.0") {
      assertTrue(
        typeChecks("LoomServer.connectors(new H2Connector(tls = tlsHole)).serveWith(h2a)"),
      )
    },
    test("config-flag conditional listing typechecks") {
      assertTrue(
        typeChecks(
          """if (sys.env.contains("ZIO_HTTP_ENABLE_H2B")) LoomServer.connectors(h2c(0)).addConnector(h2c(1)).serveWith(h2a) else LoomServer.connectors(h2c(0)).serveWith(h2a)""",
        ),
      )
    },
    test("missing engine does not compile") {
      val errors =
        typeCheckErrors("LoomServer.connectors(h2c(0)).serveWith()")
      assertTrue(errors.nonEmpty)
    },
    test("extra engine does not compile") {
      val errors =
        typeCheckErrors("LoomServer.connectors(h2c(0)).serveWith(h2a, h1a)")
      assertTrue(errors.nonEmpty)
    },
    test("fewer engines than required versions does not compile") {
      val errors = typeCheckErrors(
        "LoomServer.connectors(h2c(0)).addConnector(h3c(0, tlsHole)).serveWith(h2a)",
      )
      assertTrue(errors.nonEmpty)
    },
    test("duplicate engines do not compile") {
      val errors =
        typeCheckErrors("LoomServer.connectors(h2c(0)).serveWith(h1a, h1b)")
      assertTrue(errors.nonEmpty)
    },
    test("repeat after an intervening version does not compile") {
      val errors = typeCheckErrors(
        "LoomServer.connectors(h2c(0)).addConnector(h3c(0, tlsHole)).serveWith(h2a, h3, h2b)",
      )
      assertTrue(errors.nonEmpty)
    },
    test("zero connectors do not compile") {
      val errors =
        typeCheckErrors("LoomServer.connectors()")
      assertTrue(errors.nonEmpty)
    },
    test("direct construction is private") {
      val errors =
        typeCheckErrors("new LoomServer(h2c(0))")
      assertTrue(errors.nonEmpty)
    },
  )

  private def tlsHole: TlsConfig =
    TlsConfig(
      certChain = TlsSource.PemString(zio.blocks.config.Secret("CERT")),
      privateKey = TlsSource.PemString(zio.blocks.config.Secret("KEY")),
    )
}
