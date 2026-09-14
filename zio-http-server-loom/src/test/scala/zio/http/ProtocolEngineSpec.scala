package zio.http

import zio._
import zio.blocks.context.Context
import zio.blocks.endpoint.RoutePattern
import zio.test.TestAspect.sequential
import zio.test._

/**
 * Typed thick protocol-engine registration and one shared dispatcher.
 *
 * Connectors are listed first and engines second: `LoomServer.connectors`
 * accumulates the required version set at type level, and `serveWith` compiles
 * only when the supplied versions match it exactly (pairwise `=!=` evidence).
 * Duplicates, extras, and missing versions do not compile (proven by
 * `TypedEngineNegationSpec` on Scala 3; the shared trick works identically on
 * Scala 2.13), so there is no runtime registration path left at all: every
 * served connector version has a registered engine by construction.
 */

object ProtocolEngineSpec extends ZIOSpecDefault {

  private final class StubEngine[V <: Version](
    val protocol: V,
    val transportKind: TransportKind = TransportKind.Tcp,
  ) extends ProtocolEngine[V] {
    def drain(): Unit = ()
    def close(): Unit = ()
  }

  private val h2: ProtocolEngine[Version.`HTTP/2.0`.type] =
    new StubEngine(Version.`HTTP/2.0`)

  private val routes: Routes[Any] =
    Routes(Route(RoutePattern.GET, Handler.succeed(Response.ok)))

  /**
   * Serves H2C connectors; the flag-off branch lists one connector and one
   * engine, the flag-on branch two of each. Both branches are plain
   * `LoomServer`, so the flag only switches which exact set is built.
   */
  private def serveWithFlag(flag: Boolean) = {
    val first   = new H2CConnector(bind = BindAddress.localhost(0))
    val context =
      if (flag) {
        val server = LoomServer
          .connectors(first)
          .addConnector(new H2CConnector(bind = BindAddress.localhost(0)))
          .serveWith(h2)
        Context.empty.add(server)
      } else {
        val server = LoomServer.connectors(first).serveWith(h2)
        Context.empty.add(server)
      }
    ZIO.attemptBlocking {
      val handle = Server.serve(routes, context)
      try assertTrue(handle.bindings.length == (if (flag) 2 else 1))
      finally handle.shutdownAndWait()
    }
  }

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("ProtocolEngineSpec")(
      test("single-version listing serves normally") {
        val server  = LoomServer.connectors(new H2CConnector(bind = BindAddress.localhost(0))).serveWith(h2)
        val context = Context.empty.add(server)
        ZIO.attemptBlocking {
          val handle = Server.serve(routes, context)
          try assertTrue(handle.bindings.length == 1)
          finally handle.shutdownAndWait()
        }
      },
      test("H2C and H2 connectors share one HTTP/2.0 engine") {
        val first   = new H2CConnector(bind = BindAddress.localhost(0))
        val second  = new H2CConnector(bind = BindAddress.localhost(0))
        val server  = LoomServer
          .connectors(first)
          .addConnector(second)
          .serveWith(h2)
          .withDefectHandler(DefectHandler.default)
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
