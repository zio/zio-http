package zio.http

import zio._
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

/**
 * Migration to the typed protocol-engine contract.
 *
 * Intentional pre-release source breaks recorded here:
 *
 *   - `ProtocolEngine` is now keyed on the Blocks HTTP [[Version]] sum type
 *     (`ProtocolEngine[P <: Version]` with a single `def protocol: P`).
 *     `EngineId`, `ProtocolId`, `EngineRegistry`, and `withEngines(List(...))`
 *     are deleted, not deprecated.
 *   - `LoomServer` is built connectors-first: `connectors` lists bindings and
 *     accumulates the required version set at type level, then `serveWith`
 *     supplies engines once and compiles only on an exact version match
 *     (pairwise `=!=` evidence). Duplicates, extras, missing versions, and
 *     zero-engine servers are all compile-time rejections with no runtime
 *     representation.
 *   - `serve` model-validates every connector before bind; structurally invalid
 *     connectors fail with typed `InvalidConnector`.
 *   - `Server.serve(routes, context)` remains the single application definition
 *     shared by all engines; no call-shape change.
 */

object EngineMigrationSpec extends ZIOSpecDefault {

  private final class StubEngine[V <: Version](val protocol: V) extends ProtocolEngine[V] {
    val transportKind: TransportKind = TransportKind.Tcp
    def drain(): Unit                = ()
    def close(): Unit                = ()
  }

  private val routes: Routes[Any] =
    Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("EngineMigrationSpec")(
      test("valid typed engine listing serves normally") {
        val h2      = new StubEngine(Version.`HTTP/2.0`)
        val server  = LoomServer.connectors(new H2CConnector(bind = BindAddress.localhost(0))).serveWith(h2)
        val context = Context.empty.add(server)
        ZIO.attemptBlocking {
          val handle = Server.serve(routes, context)
          try assertTrue(handle.bindings.length == 1)
          finally handle.shutdownAndWait()
        }
      },
    ) @@ sequential
}
