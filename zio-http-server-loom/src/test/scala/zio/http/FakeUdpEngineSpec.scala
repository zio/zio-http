package zio.http

import scala.annotation.experimental

import java.util.concurrent.atomic.AtomicBoolean

import zio._
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

/**
 * Todo 14: future H3/QUIC transport seam — fake UDP engine.
 *
 * A test-only UDP engine exercises registration and per-engine lifecycle hooks
 * for a non-TCP transport: a UDP-only registry builds, TCP and UDP engines
 * coexist in one registry (independent port namespaces, so they may share a
 * numeric port), and duplicate protocols stay rejected across transports while
 * Unix domain sockets stay exclusive. The fake claims `Http1` purely as a
 * registration placeholder — no UDP wire behavior exists or is implied, and no
 * QUIC library is involved.
 *
 * Baseline pins: the Todo 1 TCP contract (H1-only builds, Tcp+Unix stays
 * incompatible) is unchanged.
 */
@experimental
object FakeUdpEngineSpec extends ZIOSpecDefault {

  /**
   * Test-only UDP engine. `drained`/`closed` record the per-engine lifecycle
   * hooks ([[ProtocolEngine.drain]]/[[ProtocolEngine.close]]) so the suite
   * proves they fire through a built registry.
   */
  private final class FakeUdpEngine(val id: EngineId) extends ProtocolEngine {
    val transportKind: TransportKind        = TransportKind.Udp
    val supportedProtocols: Set[ProtocolId] = Set[ProtocolId](ProtocolId.Http1)
    val drained: AtomicBoolean              = new AtomicBoolean(false)
    val closed: AtomicBoolean               = new AtomicBoolean(false)
    def drain(): Unit                       = {
      drained.set(true)
      ()
    }
    def close(): Unit                       = {
      closed.set(true)
      ()
    }
  }

  private final class StubTcpEngine(
    val id: EngineId,
    val supportedProtocols: Set[ProtocolId],
  ) extends ProtocolEngine {
    val transportKind: TransportKind = TransportKind.Tcp
    def drain(): Unit                = ()
    def close(): Unit                = ()
  }

  private final class StubUnixEngine(
    val id: EngineId,
    val supportedProtocols: Set[ProtocolId],
  ) extends ProtocolEngine {
    val transportKind: TransportKind = TransportKind.Unix
    def drain(): Unit                = ()
    def close(): Unit                = ()
  }

  private val tcpH1: ProtocolEngine =
    new StubTcpEngine(EngineId("tcp-h1"), Set[ProtocolId](ProtocolId.Http1))

  private val tcpH2: ProtocolEngine =
    new StubTcpEngine(EngineId("tcp-h2"), Set[ProtocolId](ProtocolId.H2))

  private val routes: Routes[Any] =
    Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("FakeUdpEngineSpec")(
      suite("baseline TCP contract unchanged")(
        test("H1-only TCP registry still builds") {
          val result = EngineRegistry.build(List(tcpH1))
          assertTrue(result.map(_.protocols) == Right[EngineRegistrationError, Set[ProtocolId]](Set(ProtocolId.Http1)))
        },
        test("Tcp+Unix registration is still an incompatible-transport failure") {
          val unixH1 = new StubUnixEngine(EngineId("unix-h1"), Set[ProtocolId](ProtocolId.Http1))
          val result = EngineRegistry.build(List(tcpH1, unixH1))
          assertTrue(
            result == Left(
              EngineRegistrationError.IncompatibleTransport(EngineId("unix-h1"), TransportKind.Tcp, TransportKind.Unix),
            ),
          )
        },
      ),
      suite("fake UDP engine registration")(
        test("UDP-only registry builds with the fake engine protocol") {
          val udp    = new FakeUdpEngine(EngineId("udp-h1"))
          val result = EngineRegistry.build(List(udp))
          assertTrue(result.map(_.protocols) == Right[EngineRegistrationError, Set[ProtocolId]](Set(ProtocolId.Http1)))
        },
        test("TCP and UDP engines coexist in one registry on a shared numeric port") {
          val udp    = new FakeUdpEngine(EngineId("udp-h1"))
          val result = EngineRegistry.build(List(tcpH2, udp))
          assertTrue(
            result.map(_.protocols) == Right[EngineRegistrationError, Set[ProtocolId]](
              Set(ProtocolId.H2, ProtocolId.Http1),
            ),
          )
        },
        test("duplicate protocol across TCP and UDP engines is still rejected") {
          val udp      = new FakeUdpEngine(EngineId("udp-h1"))
          val first    = EngineRegistry.build(List(tcpH1, udp))
          val second   = EngineRegistry.build(List(tcpH1, udp))
          val expected =
            Left(EngineRegistrationError.DuplicateProtocol(ProtocolId.Http1, EngineId("tcp-h1"), EngineId("udp-h1")))
          assertTrue(first == expected, second == expected)
        },
        test("Unix stays exclusive: Udp+Unix registration fails typed") {
          val udp    = new FakeUdpEngine(EngineId("udp-h1"))
          val unixH1 = new StubUnixEngine(EngineId("unix-h1"), Set[ProtocolId](ProtocolId.Http1))
          val result = EngineRegistry.build(List(udp, unixH1))
          assertTrue(
            result == Left(
              EngineRegistrationError.IncompatibleTransport(EngineId("unix-h1"), TransportKind.Udp, TransportKind.Unix),
            ),
          )
        },
        test("registry exposes the UDP engine and its lifecycle hooks fire") {
          val udp   = new FakeUdpEngine(EngineId("udp-h1"))
          val built = EngineRegistry.build(List(tcpH2, udp))
          ZIO.attemptBlocking {
            val found = built.toOption.flatMap(_.engineFor(ProtocolId.Http1))
            found.foreach { engine =>
              engine.drain()
              engine.close()
            }
            assertTrue(built.isRight, found.contains(udp), udp.drained.get(), udp.closed.get())
          }
        },
        test("serve with mixed TCP+UDP engines still binds the valid TCP connector") {
          val udp     = new FakeUdpEngine(EngineId("udp-h1"))
          val server  = LoomServer(Connector(bind = BindAddress.localhost(0))).withEngines(List(tcpH2, udp))
          val context = Context.empty.add(server)
          ZIO.attemptBlocking {
            val handle = Server.serve(routes, context)
            try assertTrue(handle.bindings.length == 1)
            finally handle.shutdownAndWait()
          }
        },
      ),
      suite("numeric port sharing across transports")(
        test("TCP and UDP share one port namespace flag independently") {
          assertTrue(
            TransportKind.sharesPortNamespace(TransportKind.Tcp, TransportKind.Tcp),
            TransportKind.sharesPortNamespace(TransportKind.Udp, TransportKind.Udp),
            !TransportKind.sharesPortNamespace(TransportKind.Tcp, TransportKind.Udp),
            !TransportKind.sharesPortNamespace(TransportKind.Udp, TransportKind.Tcp),
            !TransportKind.sharesPortNamespace(TransportKind.Tcp, TransportKind.Unix),
            !TransportKind.sharesPortNamespace(TransportKind.Unix, TransportKind.Unix),
          )
        },
        test("same TCP address conflicts") {
          val first  = Connector(bind = BindAddress.Tcp("127.0.0.1", 8443))
          val second = Connector(bind = BindAddress.Tcp("127.0.0.1", 8443))
          assertTrue(Connector.bindConflicts(first, second))
        },
        test("TCP and UDP connectors may share a numeric port") {
          val tcp = Connector(bind = BindAddress.Tcp("127.0.0.1", 8443))
          val udp = Connector(bind = BindAddress.Tcp("127.0.0.1", 8443), transport = TransportKind.Udp)
          assertTrue(!Connector.bindConflicts(tcp, udp), !Connector.bindConflicts(udp, tcp))
        },
        test("different ports or hosts do not conflict") {
          val base      = Connector(bind = BindAddress.Tcp("127.0.0.1", 8443))
          val otherPort = Connector(bind = BindAddress.Tcp("127.0.0.1", 8444))
          val otherHost = Connector(bind = BindAddress.Tcp("127.0.0.2", 8443))
          assertTrue(!Connector.bindConflicts(base, otherPort), !Connector.bindConflicts(base, otherHost))
        },
        test("ephemeral ports never conflict statically") {
          val first  = Connector(bind = BindAddress.localhost(0))
          val second = Connector(bind = BindAddress.localhost(0))
          assertTrue(!Connector.bindConflicts(first, second))
        },
        test("same Unix path conflicts, Unix never conflicts with TCP") {
          val path   = java.nio.file.Paths.get("/tmp/zio-http-task14-seam.sock")
          val first  = Connector(bind = BindAddress.Unix(path))
          val second = Connector(bind = BindAddress.Unix(path))
          val tcp    = Connector(bind = BindAddress.localhost(8443))
          assertTrue(
            Connector.bindConflicts(first, second),
            !Connector.bindConflicts(first, tcp),
            !Connector.bindConflicts(tcp, first),
          )
        },
        test("TransportKind.fromString resolves Unix and still rejects unknown names") {
          val rejected = List("UDP", "udp", "tcp", "", "H3", "Unix ").forall { name =>
            try {
              TransportKind.fromString(name)
              false
            } catch {
              case _: IllegalArgumentException => true
            }
          }
          assertTrue(TransportKind.fromString("Unix") == TransportKind.Unix, rejected)
        },
        test("every TransportKind round-trips through its schema") {
          val all: List[TransportKind] = List(TransportKind.Tcp, TransportKind.Udp, TransportKind.Unix)
          assertTrue(
            all.forall(k => TransportKind.schema.fromDynamicValue(TransportKind.schema.toDynamicValue(k)) == Right(k)),
          )
        },
      ),
    ) @@ sequential
}
