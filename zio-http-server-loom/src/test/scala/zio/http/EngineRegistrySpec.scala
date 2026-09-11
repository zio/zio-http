package zio.http

import scala.annotation.experimental

import zio._
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

/**
 * Todo 1 contract: explicit thick protocol-engine registration and one shared
 * dispatcher.
 *
 * RED fixture: H1-only, H2-only and H1+H2 registries build; duplicate protocol
 * IDs, duplicate engine IDs and incompatible transports are deterministic typed
 * failures; the shared dispatcher serves routes without seeing frames.
 */
@experimental
object EngineRegistrySpec extends ZIOSpecDefault {

  private final class StubEngine(
    val id: EngineId,
    val transportKind: TransportKind,
    val supportedProtocols: Set[ProtocolId],
  ) extends ProtocolEngine {
    def drain(): Unit = ()
    def close(): Unit = ()
  }

  private val h1: ProtocolEngine =
    new StubEngine(EngineId("h1"), TransportKind.Tcp, Set(ProtocolId.Http1))

  private val h2c: ProtocolEngine =
    new StubEngine(EngineId("h2c"), TransportKind.Tcp, Set(ProtocolId.H2C))

  private val h2: ProtocolEngine =
    new StubEngine(EngineId("h2"), TransportKind.Tcp, Set(ProtocolId.H2))

  private val unixH1: ProtocolEngine =
    new StubEngine(EngineId("unix-h1"), TransportKind.Unix, Set(ProtocolId.Http1))

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("EngineRegistrySpec")(
      test("H1-only registry builds with exactly the H1 protocol") {
        val result = EngineRegistry.build(List(h1))
        assertTrue(result.map(_.protocols) == Right[EngineRegistrationError, Set[ProtocolId]](Set(ProtocolId.Http1)))
      },
      test("H2-only registry builds with exactly the H2 protocol") {
        val result = EngineRegistry.build(List(h2))
        assertTrue(result.map(_.protocols) == Right[EngineRegistrationError, Set[ProtocolId]](Set(ProtocolId.H2)))
      },
      test("H1+H2 registry builds with both protocols") {
        val result = EngineRegistry.build(List(h1, h2c, h2))
        assertTrue(
          result.map(_.protocols) == Right[EngineRegistrationError, Set[ProtocolId]](
            Set(ProtocolId.Http1, ProtocolId.H2C, ProtocolId.H2),
          ),
        )
      },
      test("duplicate protocol IDs are a deterministic typed failure") {
        val otherH1  = new StubEngine(EngineId("h1-b"), TransportKind.Tcp, Set(ProtocolId.Http1))
        val first    = EngineRegistry.build(List(h1, otherH1))
        val second   = EngineRegistry.build(List(h1, otherH1))
        val expected =
          Left(EngineRegistrationError.DuplicateProtocol(ProtocolId.Http1, EngineId("h1"), EngineId("h1-b")))
        assertTrue(first == expected, second == expected)
      },
      test("duplicate engine IDs are a deterministic typed failure") {
        val otherH1 =
          new StubEngine(EngineId("h1"), TransportKind.Tcp, Set(ProtocolId.H2C))
        val result  = EngineRegistry.build(List(h1, otherH1))
        assertTrue(result == Left(EngineRegistrationError.DuplicateEngineId(EngineId("h1"))))
      },
      test("incompatible transport registration is a deterministic typed failure") {
        val result = EngineRegistry.build(List(h1, unixH1))
        assertTrue(
          result == Left(
            EngineRegistrationError.IncompatibleTransport(EngineId("unix-h1"), TransportKind.Tcp, TransportKind.Unix),
          ),
        )
      },
      test("empty registry is a deterministic typed failure") {
        val result = EngineRegistry.build(Nil)
        assertTrue(result == Left(EngineRegistrationError.EmptyRegistry))
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
}
