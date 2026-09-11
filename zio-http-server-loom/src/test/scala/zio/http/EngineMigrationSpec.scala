package zio.http

import scala.annotation.experimental

import zio._
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

/**
 * Migration fixture for Todo 1 (protocol-engine contract).
 *
 * Intentional pre-release source breaks recorded here:
 *
 *   - `LoomServer` gains explicit thick-engine registration (`withEngine` /
 *     `withEngines`). Pre-Todo-1 construction `LoomServer(connector)` without
 *     engines keeps serving exactly as before (source-compatible: the new
 *     `engines` parameter defaults to `Nil`).
 *   - `serve` now validates registered engines BEFORE any socket is bound and
 *     throws a deterministic [[EngineRegistrationError]] (duplicate id,
 *     duplicate protocol, incompatible transport) instead of binding.
 *   - `Server.serve(routes, context)` remains the single application definition
 *     shared by all engines; no call-shape change.
 */
@experimental
object EngineMigrationSpec extends ZIOSpecDefault {

  private final class StubEngine(
    val id: EngineId,
    val transportKind: TransportKind,
    val supportedProtocols: Set[ProtocolId],
  ) extends ProtocolEngine {
    def drain(): Unit = ()
    def close(): Unit = ()
  }

  private val routes: Routes[Any] =
    Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("EngineMigrationSpec")(
      test("pre-Todo-1 LoomServer(connector) without engines still serves") {
        val server  = LoomServer(Connector(bind = BindAddress.localhost(0)))
        val context = Context.empty.add(server)
        ZIO.attemptBlocking {
          val handle = Server.serve(routes, context)
          try assertTrue(handle.bindings.length == 1)
          finally handle.shutdownAndWait()
        }
      },
      test("valid engine registration serves normally") {
        val h1      = new StubEngine(EngineId("h1"), TransportKind.Tcp, Set(ProtocolId.Http1))
        val server  = LoomServer(Connector(bind = BindAddress.localhost(0))).withEngine(h1)
        val context = Context.empty.add(server)
        ZIO.attemptBlocking {
          val handle = Server.serve(routes, context)
          try assertTrue(handle.bindings.length == 1)
          finally handle.shutdownAndWait()
        }
      },
      test("duplicate protocol registration fails serve fast with a typed error") {
        val h1a     = new StubEngine(EngineId("h1-a"), TransportKind.Tcp, Set(ProtocolId.Http1))
        val h1b     = new StubEngine(EngineId("h1-b"), TransportKind.Tcp, Set(ProtocolId.Http1))
        val server  = LoomServer(Connector(bind = BindAddress.localhost(0))).withEngines(List(h1a, h1b))
        val context = Context.empty.add(server)
        ZIO.attemptBlocking {
          val result =
            try {
              val handle = Server.serve(routes, context)
              try Left("bound")
              finally handle.shutdownAndWait()
            } catch {
              case error: EngineRegistrationError.DuplicateProtocol => Right(error)
            }
          assertTrue(
            result == Right(
              EngineRegistrationError.DuplicateProtocol(ProtocolId.Http1, EngineId("h1-a"), EngineId("h1-b")),
            ),
          )
        }
      },
      test("incompatible transport registration fails serve fast with a typed error") {
        val tcpH1   = new StubEngine(EngineId("tcp-h1"), TransportKind.Tcp, Set(ProtocolId.Http1))
        val unixH1  = new StubEngine(EngineId("unix-h1"), TransportKind.Unix, Set(ProtocolId.Http1))
        val server  =
          LoomServer(Connector(bind = BindAddress.localhost(0))).withEngines(List(tcpH1, unixH1))
        val context = Context.empty.add(server)
        ZIO.attemptBlocking {
          val result =
            try {
              val handle = Server.serve(routes, context)
              try Left("bound")
              finally handle.shutdownAndWait()
            } catch {
              case error: EngineRegistrationError.IncompatibleTransport => Right(error)
            }
          assertTrue(
            result == Right(
              EngineRegistrationError.IncompatibleTransport(EngineId("unix-h1"), TransportKind.Tcp, TransportKind.Unix),
            ),
          )
        }
      },
    ) @@ sequential
}
