package zio.http

import zio.test._

/**
 * Built-in engine selection: H2C and H2 connectors resolve to the same
 * stateless `H2Engine` singleton (TLS vs cleartext is connector transport, not
 * version), while H3 has a connector type but no engine and is refused before
 * bind. No user-visible engine arguments are involved.
 */

object EngineSelectionSpec extends ZIOSpecDefault {

  private def tlsHole: TlsConfig =
    TlsConfig(
      certChain = TlsSource.PemString(zio.blocks.config.Secret("CERT")),
      privateKey = TlsSource.PemString(zio.blocks.config.Secret("KEY")),
    )

  override def spec = suite("EngineSelectionSpec")(
    test("H2C and H2 connectors select the same H2 singleton") {
      val h2c = Connector(bind = BindAddress.localhost(0))
      val h2  = Connector(bind = BindAddress.localhost(0), protocol = Protocol.H2(tlsHole))
      assertTrue(HttpEngine.engineFor(h2c) eq H2Engine, HttpEngine.engineFor(h2) eq H2Engine)
    },
    test("H3 connector has no engine") {
      val h3      = Connector(bind = BindAddress.localhost(0), protocol = Protocol.H3(tlsHole))
      val outcome =
        try {
          HttpEngine.engineFor(h3)
          "resolved"
        } catch {
          case InvalidConnector(ConnectorFailure.H3NotAdvertised) => "refused"
        }
      assertTrue(outcome == "refused")
    },
  )
}
