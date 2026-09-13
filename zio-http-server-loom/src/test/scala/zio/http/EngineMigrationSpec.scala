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
 *   - `LoomServer` carries the registered versions as a tuple (`LoomServer[Ps
 *     <: Tuple]` on Scala 3) and `withEngine` enforces max-one-per-version at
 *     compile time. Duplicate registration has no runtime representation.
 *   - `serve` keeps the legacy bind path when no engine is registered, so
 *     `LoomServer(connector)` without engines serves exactly as before.
 *     Declaring any engine opts into coverage: every served connector version
 *     must then have a registered engine, else `serve` fails before bind with
 *     [[EngineRegistrationError.MissingEngine]].
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
      test("LoomServer(connector) without engines still serves") {
        val server  = LoomServer(Connector(bind = BindAddress.localhost(0)))
        val context = Context.empty.add(server)
        ZIO.attemptBlocking {
          val handle = Server.serve(routes, context)
          try assertTrue(handle.bindings.length == 1)
          finally handle.shutdownAndWait()
        }
      },
      test("valid typed engine registration serves normally") {
        val h2      = new StubEngine(Version.`HTTP/2.0`)
        val server  = LoomServer(Connector(bind = BindAddress.localhost(0))).withEngine(h2)
        val context = Context.empty.add(server)
        ZIO.attemptBlocking {
          val handle = Server.serve(routes, context)
          try assertTrue(handle.bindings.length == 1)
          finally handle.shutdownAndWait()
        }
      },
      test("uncovered version fails serve fast with a typed missing-engine error") {
        val h1      = new StubEngine(Version.`HTTP/1.1`)
        val server  = LoomServer(Connector(bind = BindAddress.localhost(0))).withEngine(h1)
        val context = Context.empty.add(server)
        ZIO.attemptBlocking {
          val result =
            try {
              val handle = Server.serve(routes, context)
              try Left("bound")
              finally handle.shutdownAndWait()
            } catch {
              case error: EngineRegistrationError.MissingEngine => Right(error)
            }
          assertTrue(
            result == Right(EngineRegistrationError.MissingEngine(Version.`HTTP/2.0`)),
          )
        }
      },
    ) @@ sequential
}
