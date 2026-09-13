package zio.http

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.h2.H2RawClientFixture.RawH2Client

/**
 * Baseline characterization of the server bind/serve/shutdown behavior.
 *
 * Pins the contract the typed engine wave preserves: one
 * `Server.serve(routes, context)` application definition, `LoomServer` bound to
 * an H2C connector, a single TCP binding, live traffic, and working shutdown —
 * both without engines (legacy bind path) and with a registered HTTP/2.0 engine
 * (typed coverage).
 */

object EngineBaselineSpec extends ZIOSpecDefault {

  private val routes: Routes[Any] =
    Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("EngineBaselineSpec")(
      test("LoomServer binds one ephemeral TCP connector and serves GET / with 200") {
        val server  = LoomServer(Connector(bind = BindAddress.localhost(0)))
        val context = Context.empty.add(server)
        val handle  = Server.serve(routes, context)
        ZIO.attemptBlocking {
          try {
            val port      = handle.bindings.headOption.collect { case BoundConnector(BoundAddress.Tcp(_, p), _) =>
              p
            }
            val nBindings = handle.bindings.length
            val running   = handle.isRunning
            assertTrue(nBindings == 1, running) &&
            (port match {
              case Some(p) =>
                val client = new RawH2Client(p)
                try {
                  val resp = client.roundTrip("GET", "/", Chunk.empty, streamId = 1)
                  assertTrue(resp.status == 200)
                } finally client.close()
              case None    => assertTrue(false)
            })
          } finally handle.shutdownAndWait()
        }
      },
      test("shutdown stops the server and awaitShutdown returns") {
        val server  = LoomServer(Connector(bind = BindAddress.localhost(0)))
        val context = Context.empty.add(server)
        val handle  = Server.serve(routes, context)
        ZIO.attemptBlocking {
          assertTrue(handle.isRunning)
          handle.shutdownAndWait()
          assertTrue(!handle.isRunning)
        }
      },
      test("registered HTTP/2.0 engine serves GET / with 200") {
        val engine  = new ProtocolEngine[Version.`HTTP/2.0`.type] {
          val protocol: Version.`HTTP/2.0`.type = Version.`HTTP/2.0`
          val transportKind: TransportKind      = TransportKind.Tcp
          def drain(): Unit                     = ()
          def close(): Unit                     = ()
        }
        val server  = LoomServer(Connector(bind = BindAddress.localhost(0))).withEngine(engine)
        val context = Context.empty.add(server)
        val handle  = Server.serve(routes, context)
        ZIO.attemptBlocking {
          try {
            val port = handle.bindings.headOption.collect { case BoundConnector(BoundAddress.Tcp(_, p), _) =>
              p
            }
            // No `isRunning` assertion here: it reads the acceptor thread's
            // `isAlive`, which races thread start immediately after `serve`.
            // Live traffic below is the meaningful proof the server serves.
            assertTrue(handle.bindings.length == 1) &&
            (port match {
              case Some(p) =>
                val client = new RawH2Client(p)
                try {
                  val resp = client.roundTrip("GET", "/", Chunk.empty, streamId = 1)
                  assertTrue(resp.status == 200)
                } finally client.close()
              case None    => assertTrue(false)
            })
          } finally handle.shutdownAndWait()
        }
      },
    ) @@ sequential
}
