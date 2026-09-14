package zio.http

import java.util.concurrent.atomic.AtomicBoolean

import zio._
import zio.test.TestAspect.sequential
import zio.test._

/**
 * Future H3/QUIC transport seam — fake UDP engine.
 *
 * A test-only UDP engine placeholder: it claims `HTTP/1.1` purely as a
 * registration placeholder — no UDP wire behavior exists or is implied, and no
 * QUIC library is involved. The numeric-port-namespace contract
 * ([[TransportKind.sharesPortNamespace]], [[Connector.bindConflicts]]) is
 * pinned unchanged below.
 */

object FakeUdpEngineSpec extends ZIOSpecDefault {

  /**
   * Test-only UDP engine. `drained`/`closed` record direct calls to the
   * per-engine hooks ([[ProtocolEngine.drain]]/[[ProtocolEngine.close]]); the
   * test below invokes them directly, so it proves the hook methods record
   * invocation — not server-lifecycle integration (nothing calls these hooks
   * during serve/shutdown yet).
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

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("FakeUdpEngineSpec")(
      suite("fake UDP engine registration")(
        test("engine drain/close hooks record direct calls") {
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
