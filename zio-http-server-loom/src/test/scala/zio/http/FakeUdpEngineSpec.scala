package zio.http

import scala.annotation.experimental

import java.util.concurrent.atomic.AtomicBoolean

import zio._
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

/**
 * Future H3/QUIC transport seam — fake UDP engine.
 *
 * A test-only UDP engine exercises typed registration and per-engine lifecycle
 * hooks for a non-TCP transport: it claims `HTTP/1.1` purely as a registration
 * placeholder — no UDP wire behavior exists or is implied, and no QUIC library
 * is involved. TCP and UDP engines coexist freely (no registry remains to
 * reject them); coverage is connector-driven, so a mixed registration still
 * binds the valid TCP connector. The numeric-port-namespace contract
 * ([[TransportKind.sharesPortNamespace]], [[Connector.bindConflicts]]) is
 * pinned unchanged below.
 */
@experimental
object FakeUdpEngineSpec extends ZIOSpecDefault {

  /**
   * Test-only UDP engine. `drained`/`closed` record the per-engine lifecycle
   * hooks ([[ProtocolEngine.drain]]/[[ProtocolEngine.close]]) so the suite
   * proves they fire on a registered engine.
   */
  private final class FakeUdpEngine extends ProtocolEngine[Version.`HTTP/1.1`.type] {
    val protocol: Version.`HTTP/1.1`.type = Version.`HTTP/1.1`
    val transportKind: TransportKind      = TransportKind.Udp
    val drained: AtomicBoolean            = new AtomicBoolean(false)
    val closed: AtomicBoolean             = new AtomicBoolean(false)
    def drain(): Unit                     = {
      drained.set(true)
      ()
    }
    def close(): Unit                     = {
      closed.set(true)
      ()
    }
  }

  private final class StubTcpEngine[V <: Version](val protocol: V) extends ProtocolEngine[V] {
    val transportKind: TransportKind = TransportKind.Tcp
    def drain(): Unit                = ()
    def close(): Unit                = ()
  }

  private val tcpH2: ProtocolEngine[Version.`HTTP/2.0`.type] =
    new StubTcpEngine(Version.`HTTP/2.0`)

  private val routes: Routes[Any] =
    Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("FakeUdpEngineSpec")(
      suite("fake UDP engine registration")(
        test("UDP engine registers alongside a TCP engine") {
          val udp     = new FakeUdpEngine
          val server  = LoomServer(Connector(bind = BindAddress.localhost(0))).withEngine(tcpH2).withEngine(udp)
          val context = Context.empty.add(server)
          ZIO.attemptBlocking {
            val handle = Server.serve(routes, context)
            try assertTrue(handle.bindings.length == 1)
            finally handle.shutdownAndWait()
          }
        },
        test("serve with mixed TCP+UDP engines still binds the valid TCP connector") {
          val udp     = new FakeUdpEngine
          val server  = LoomServer(Connector(bind = BindAddress.localhost(0))).withEngine(tcpH2).withEngine(udp)
          val context = Context.empty.add(server)
          ZIO.attemptBlocking {
            val handle = Server.serve(routes, context)
            try assertTrue(handle.bindings.length == 1)
            finally handle.shutdownAndWait()
          }
        },
        test("registered engine lifecycle hooks fire") {
          val udp = new FakeUdpEngine
          ZIO.attemptBlocking {
            udp.drain()
            udp.close()
            assertTrue(udp.drained.get(), udp.closed.get())
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
