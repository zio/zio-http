package zio.http

import zio._
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

/**
 * Typed thick protocol-engine registration and one shared dispatcher.
 *
 * Engines are keyed on the Blocks HTTP [[Version]] sum type with a compile-time
 * max-one-per-version bound (`LoomServer.withEngine` requires
 * `Tuple.Contains[Ps, P] =:= false` on Scala 3). There is no runtime duplicate
 * path: registering two engines for one version does not compile (proven by
 * `TypedEngineNegationSpec` on Scala 3; the Scala 2 fallback documents the same
 * bound without enforcing it). The only runtime registration failure left is a
 * coverage gap: once any engine is declared, every served connector version
 * must have a registered engine, else `serve` fails before bind with
 * [[EngineRegistrationError.MissingEngine]].
 */

object ProtocolEngineSpec extends ZIOSpecDefault {

  private final class StubEngine[V <: Version](
    val protocol: V,
    val transportKind: TransportKind = TransportKind.Tcp,
  ) extends ProtocolEngine[V] {
    def drain(): Unit = ()
    def close(): Unit = ()
  }

  private val h1: ProtocolEngine[Version.`HTTP/1.1`.type] =
    new StubEngine(Version.`HTTP/1.1`)

  private val h2: ProtocolEngine[Version.`HTTP/2.0`.type] =
    new StubEngine(Version.`HTTP/2.0`)

  private val routes: Routes[Any] =
    Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))

  /**
   * Serves one ephemeral H2C connector, adding the H1 engine only when `flag`
   * holds.
   */
  private def serveWithFlag(flag: Boolean) = {
    val base    = LoomServer(Connector(bind = BindAddress.localhost(0))).withEngine(h2)
    val server  = if (flag) base.withEngine(h1) else base
    val context = Context.empty.add(server)
    ZIO.attemptBlocking {
      val handle = Server.serve(routes, context)
      try assertTrue(handle.bindings.length == 1)
      finally handle.shutdownAndWait()
    }
  }

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

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("ProtocolEngineSpec")(
      test("single-version registration serves normally") {
        val server  = LoomServer(Connector(bind = BindAddress.localhost(0))).withEngine(h2)
        val context = Context.empty.add(server)
        ZIO.attemptBlocking {
          val handle = Server.serve(routes, context)
          try assertTrue(handle.bindings.length == 1)
          finally handle.shutdownAndWait()
        }
      },
      test("missing engine fails serve before bind with a typed error") {
        val port   = freePort()
        val server = LoomServer(Connector(bind = BindAddress.localhost(port))).withEngine(h1)
        ZIO.attemptBlocking {
          val result =
            try {
              val handle = Server.serve(routes, context = Context.empty.add(server))
              try Left("bound")
              finally handle.shutdownAndWait()
            } catch {
              case error: EngineRegistrationError.MissingEngine => Right(error)
            }
          assertTrue(
            result == Right(EngineRegistrationError.MissingEngine(Version.`HTTP/2.0`)),
            portIsFree(port),
          )
        }
      },
      test("extra engine no connector needs is allowed") {
        val server  = LoomServer(Connector(bind = BindAddress.localhost(0))).withEngine(h2).withEngine(h1)
        val context = Context.empty.add(server)
        ZIO.attemptBlocking {
          val handle = Server.serve(routes, context)
          try assertTrue(handle.bindings.length == 1)
          finally handle.shutdownAndWait()
        }
      },
      test("H2C and H2 connectors share one HTTP/2.0 engine") {
        val first   = Connector(bind = BindAddress.localhost(0))
        val second  = Connector(bind = BindAddress.localhost(0))
        val server  = LoomServer(first).addConnector(second).withEngine(h2)
        val context = Context.empty.add(server)
        ZIO.attemptBlocking {
          val handle = Server.serve(routes, context)
          try assertTrue(handle.bindings.length == 2)
          finally handle.shutdownAndWait()
        }
      },
      test("config-flag conditional registration serves with the flag off") {
        serveWithFlag(false)
      },
      test("config-flag conditional registration serves with the flag on") {
        serveWithFlag(true)
      },
      test("connector versions map to wire versions explicitly") {
        assertTrue(
          EngineCoverage.protocolVersion(Protocol.H2C()) == Version.`HTTP/2.0`,
          EngineCoverage.protocolVersion(Protocol.H2(tlsForMapping)) == Version.`HTTP/2.0`,
          EngineCoverage.protocolVersion(Protocol.H3(tlsForMapping)) == Version.`HTTP/3.0`,
        )
      },
      test("shared dispatcher serves a matching route with 200") {
        val routes     = Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))
        val dispatcher = new EngineDispatcher(routes, Context.empty, DefectHandler.default)
        ZIO.attemptBlocking {
          val response = dispatcher.dispatch(Request.get(URL.root))
          assertTrue(response.status == Status.Ok)
        }
      },
      test("shared dispatcher returns 404 for an unknown path") {
        val routes     = Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))
        val dispatcher = new EngineDispatcher(routes, Context.empty, DefectHandler.default)
        ZIO.attemptBlocking {
          val response = dispatcher.dispatch(Request.get(URL.root / "missing"))
          assertTrue(response.status == Status.NotFound)
        }
      },
      test("shared dispatcher maps handler defects to 500") {
        val routes     = Routes(
          Route(
            RoutePattern.GET,
            Handler.fromRequest(_ => throw new RuntimeException("boom")),
          ),
        )
        val dispatcher = new EngineDispatcher(routes, Context.empty, DefectHandler.default)
        ZIO.attemptBlocking {
          val response = dispatcher.dispatch(Request.get(URL.root))
          assertTrue(response.status == Status.InternalServerError)
        }
      },
    ) @@ sequential

  private def tlsForMapping: TlsConfig =
    TlsConfig(
      certChain = TlsSource.PemString(zio.blocks.config.Secret("CERT")),
      privateKey = TlsSource.PemString(zio.blocks.config.Secret("KEY")),
    )
}
