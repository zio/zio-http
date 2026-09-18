package zio.http

import zio._
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

/**
 * Migration to the connectors-only server contract.
 *
 * Intentional pre-release source breaks recorded here:
 *
 *   - Users configure connectors only and never supply engines: `LoomServer`
 *     takes ≥1 connector, and the matching built-in engine object is selected
 *     internally at serve. `ProtocolEngine`, `EngineId`, `ProtocolId`,
 *     `EngineRegistry`, `withEngine(s)`, phased `connectors`/`serveWith`,
 *     `Requires`, and local `=:!=` are deleted, not deprecated.
 *   - `serve` model-validates every connector before bind; structurally invalid
 *     connectors fail with typed `InvalidConnector`, and versions without an
 *     engine fail before bind.
 *   - `Server.serve(routes, context)` remains the single application definition
 *     shared by all bindings; no call-shape change.
 */

object EngineMigrationSpec extends ZIOSpecDefault {

  private val routes: Routes[Any] =
    Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("EngineMigrationSpec")(
      test("connector-only server serves normally") {
        val server  = LoomServer(Connector(bind = BindAddress.localhost(0)))
        val context = Context.empty.add(server)
        ZIO.attemptBlocking {
          val handle = Server.serve(routes, context)
          try assertTrue(handle.bindings.length == 1)
          finally handle.shutdownAndWait()
        }
      },
    ) @@ sequential
}
