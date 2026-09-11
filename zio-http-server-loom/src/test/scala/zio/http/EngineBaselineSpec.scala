package zio.http

import scala.annotation.experimental

import zio._
import zio.blocks.chunk.Chunk
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

import zio.http.h2.H2RawClientFixture.RawH2Client

/**
 * Baseline characterization of the pre-Todo-1 server behavior.
 *
 * Pins the contract Todo 1 must preserve: one `Server.serve(routes, context)`
 * application definition, `LoomServer` bound to an H2C connector, a single TCP
 * binding, live traffic, and working shutdown.
 */
@experimental
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
    ) @@ sequential
}
