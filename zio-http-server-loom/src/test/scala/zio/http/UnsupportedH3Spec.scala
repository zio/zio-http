package zio.http

import scala.annotation.experimental

import zio._
import zio.blocks.config.Secret
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

/**
 * Production H3/QUIC is unsupported — fail before bind.
 *
 * No H3 engine is installed, so every production H3 shape must fail
 * `LoomServer.serve` validation with a deterministic [[InvalidConnector]]
 * before any socket is bound: a lone H3 connector, a UDP-transport connector,
 * an H3 connector smuggled in as an additional connector (nothing binds, not
 * even the valid first connector), H3 paired with a UDP transport or a shared
 * negotiation policy, and H3 alongside a registered TCP engine. H3 is never
 * advertised (no protocol-set mapping), never selected (no ALPN id), and never
 * run.
 */
@experimental
object UnsupportedH3Spec extends ZIOSpecDefault {

  private def tlsCfg: TlsConfig =
    TlsConfig(
      certChain = TlsSource.PemString(Secret("CERT")),
      privateKey = TlsSource.PemString(Secret("KEY")),
    )

  private val routes: Routes[Any] =
    Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))

  /** A TCP port that is free right now on loopback. */
  private def freePort(): Int = {
    val socket = new java.net.ServerSocket(0)
    try socket.getLocalPort
    finally socket.close()
  }

  /** True when nothing on loopback currently holds `port`. */
  private def portIsFree(port: Int): Boolean =
    try {
      val socket = new java.net.ServerSocket()
      try {
        socket.bind(new java.net.InetSocketAddress("127.0.0.1", port))
        true
      } finally socket.close()
    } catch {
      case _: java.io.IOException => false
    }

  private def serveFailsBeforeBind(server: Server): Boolean = {
    val outcome: Either[String, InvalidConnector] =
      try {
        val handle = server.serve(routes, Context.empty)
        try Left("bound")
        finally handle.shutdownAndWait()
      } catch {
        case error: InvalidConnector => Right(error)
      }
    outcome match {
      case Right(InvalidConnector(ConnectorFailure.H3NotAdvertised))   => true
      case Right(InvalidConnector(ConnectorFailure.TcpUdpMismatch(_))) => true
      case _                                                           => false
    }
  }

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("UnsupportedH3Spec")(
      test("H3 connector fails serve before bind with H3NotAdvertised") {
        val port   = freePort()
        val server = LoomServer(Connector(bind = BindAddress.localhost(port), protocol = Protocol.H3(tlsCfg)))
        ZIO.attemptBlocking {
          val refused: Either[String, InvalidConnector] =
            try {
              val handle = server.serve(routes, Context.empty)
              try Left("bound")
              finally handle.shutdownAndWait()
            } catch {
              case error: InvalidConnector => Right(error)
            }
          assertTrue(
            refused == Right(InvalidConnector(ConnectorFailure.H3NotAdvertised)),
            refused.fold(_ => "bound", _.getMessage).contains("H3/QUIC is not advertised"),
            portIsFree(port),
          )
        }
      },
      test("UDP-transport connector fails serve before bind with TcpUdpMismatch") {
        val port   = freePort()
        val server = LoomServer(
          Connector(bind = BindAddress.localhost(port), transport = TransportKind.Udp),
        )
        ZIO.attemptBlocking {
          val refused: Either[String, InvalidConnector] =
            try {
              val handle = server.serve(routes, Context.empty)
              try Left("bound")
              finally handle.shutdownAndWait()
            } catch {
              case error: InvalidConnector => Right(error)
            }
          assertTrue(
            refused match {
              case Right(InvalidConnector(ConnectorFailure.TcpUdpMismatch(_))) => true
              case _                                                           => false
            },
            portIsFree(port),
          )
        }
      },
      test("H3 as an additional connector fails before any connector binds") {
        val firstPort  = freePort()
        val secondPort = freePort()
        val server     = LoomServer(Connector(bind = BindAddress.localhost(firstPort)))
          .addConnector(Connector(bind = BindAddress.localhost(secondPort), protocol = Protocol.H3(tlsCfg)))
        ZIO.attemptBlocking {
          assertTrue(serveFailsBeforeBind(server), portIsFree(firstPort), portIsFree(secondPort))
        }
      },
      test("H3 with UDP transport still reports H3NotAdvertised first") {
        val connector = Connector(
          bind = BindAddress.localhost(0),
          protocol = Protocol.H3(tlsCfg),
          transport = TransportKind.Udp,
        )
        assertTrue(connector.validate == Left(ConnectorFailure.H3NotAdvertised))
      },
      test("H3 with a shared negotiation policy still reports H3NotAdvertised") {
        val alpn    = Connector(
          bind = BindAddress.localhost(0),
          protocol = Protocol.H3(tlsCfg),
          negotiation = NegotiationPolicy.TlsAlpn,
        )
        val preface = Connector(
          bind = BindAddress.localhost(0),
          protocol = Protocol.H3(tlsCfg),
          negotiation = NegotiationPolicy.CleartextPreface,
        )
        assertTrue(
          alpn.validate == Left(ConnectorFailure.H3NotAdvertised),
          preface.validate == Left(ConnectorFailure.H3NotAdvertised),
        )
      },
      test("H3 fails even with a valid TCP engine registered") {
        val port   = freePort()
        val engine = new ProtocolEngine[Version.`HTTP/2.0`.type] {
          val protocol: Version.`HTTP/2.0`.type = Version.`HTTP/2.0`
          val transportKind: TransportKind      = TransportKind.Tcp
          def drain(): Unit                     = ()
          def close(): Unit                     = ()
        }
        val server = LoomServer(Connector(bind = BindAddress.localhost(port), protocol = Protocol.H3(tlsCfg)))
          .withEngine(engine)
        ZIO.attemptBlocking {
          assertTrue(serveFailsBeforeBind(server), portIsFree(port))
        }
      },
      test("H3 is never advertised and never selected") {
        assertTrue(
          ProtocolSet.fromLegacy(Protocol.H3(tlsCfg)) == Left(ConnectorFailure.H3NotAdvertised),
          AppProtocol.fromAlpnId("h3").isEmpty,
          AppProtocol.fromAlpnId("h2").contains(AppProtocol.H2),
          AppProtocol.fromAlpnId("http/1.1").contains(AppProtocol.Http1),
        )
      },
      test("valid TCP connector control still validates") {
        val connector = Connector(bind = BindAddress.localhost(0))
        assertTrue(connector.validate == Right[ConnectorFailure, Unit](()))
      },
    ) @@ sequential
}
