package zio.http

import scala.compiletime.testing.{typeCheckErrors, typeChecks}

import zio.test._

/**
 * Scala 3 compile-time proof of the max-one-engine-per-version bound.
 *
 * The fixed `LoomServer.apply` overloads require pairwise `=!=` evidence, so
 * listing two engines for one version is a type error, not a runtime failure.
 * This spec pins both sides: well-formed lists (and the config-flag conditional
 * pattern) typecheck, duplicates do not — including a repeat after an
 * intervening version and the four-arity overload — and a zero-engine server is
 * unrepresentable (no zero-engine `apply` overload exists and the constructor
 * is private). The `=!=` evidence is a small local definition (same syntax as
 * `zio.=!=`, but core server packages take on no new dependency for this), and
 * `compiletime.testing` exists only on Scala 3, so this spec lives in the Scala
 * 3 test sources.
 */

object TypedEngineNegationSpec extends ZIOSpecDefault {

  private final class StubEngine[V <: Version](val protocol: V) extends ProtocolEngine[V] {
    val transportKind: TransportKind = TransportKind.Tcp
    def drain(): Unit                = ()
    def close(): Unit                = ()
  }

  private val h10: ProtocolEngine[Version.`HTTP/1.0`.type] =
    new StubEngine(Version.`HTTP/1.0`)

  private val h1a: ProtocolEngine[Version.`HTTP/1.1`.type] =
    new StubEngine(Version.`HTTP/1.1`)

  private val h1b: ProtocolEngine[Version.`HTTP/1.1`.type] =
    new StubEngine(Version.`HTTP/1.1`)

  private val h2: ProtocolEngine[Version.`HTTP/2.0`.type] =
    new StubEngine(Version.`HTTP/2.0`)

  private val h3: ProtocolEngine[Version.`HTTP/3.0`.type] =
    new StubEngine(Version.`HTTP/3.0`)

  override def spec = suite("TypedEngineNegationSpec")(
    test("single engine typechecks") {
      assertTrue(
        typeChecks("LoomServer(Connector(bind = BindAddress.localhost(0)), h2)"),
      )
    },
    test("two engines typecheck") {
      assertTrue(
        typeChecks("LoomServer(Connector(bind = BindAddress.localhost(0)), h2, h1a)"),
      )
    },
    test("four engines typecheck") {
      assertTrue(
        typeChecks("LoomServer(Connector(bind = BindAddress.localhost(0)), h10, h1a, h2, h3)"),
      )
    },
    test("duplicate engines do not compile") {
      val errors =
        typeCheckErrors("LoomServer(Connector(bind = BindAddress.localhost(0)), h1a, h1b)")
      assertTrue(errors.nonEmpty)
    },
    test("repeat after an intervening version does not compile") {
      val errors = typeCheckErrors(
        "LoomServer(Connector(bind = BindAddress.localhost(0)), h2, h1a, h1b)",
      )
      assertTrue(errors.nonEmpty)
    },
    test("zero-engine apply does not exist") {
      val errors =
        typeCheckErrors("LoomServer(Connector(bind = BindAddress.localhost(0)))")
      assertTrue(errors.nonEmpty)
    },
    test("direct construction is private") {
      val errors =
        typeCheckErrors("new LoomServer(Connector(bind = BindAddress.localhost(0)))")
      assertTrue(errors.nonEmpty)
    },
    test("config-flag conditional listing typechecks") {
      assertTrue(
        typeChecks(
          """val c = Connector(bind = BindAddress.localhost(0)); if (sys.env.contains("ZIO_HTTP_ENABLE_H1")) LoomServer(c, h2, h1a) else LoomServer(c, h2)""",
        ),
      )
    },
  )
}
