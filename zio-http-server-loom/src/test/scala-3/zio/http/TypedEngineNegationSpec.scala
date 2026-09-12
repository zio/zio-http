package zio.http

import scala.annotation.experimental
import scala.compiletime.testing.{typeCheckErrors, typeChecks}

import zio.test._

/**
 * Scala 3 compile-time proof of the max-one-engine-per-version bound.
 *
 * `LoomServer.withEngine` requires `Tuple.Contains[Ps, P] =:= false`, so a
 * second registration for an already-registered version is a type error, not a
 * runtime failure. This spec pins both sides: single registration (and the
 * config-flag conditional pattern) typechecks, duplicate registration does not.
 * Scala 2 only documents the bound (see the `scala-2` `LoomServer` fallback),
 * so this spec lives in the Scala 3 test sources.
 */
@experimental
object TypedEngineNegationSpec extends ZIOSpecDefault {

  private final class StubEngine[V <: Version](val protocol: V) extends ProtocolEngine[V] {
    val transportKind: TransportKind = TransportKind.Tcp
    def drain(): Unit                = ()
    def close(): Unit                = ()
  }

  private val h1a: ProtocolEngine[Version.`HTTP/1.1`.type] =
    new StubEngine(Version.`HTTP/1.1`)

  private val h1b: ProtocolEngine[Version.`HTTP/1.1`.type] =
    new StubEngine(Version.`HTTP/1.1`)

  private val h2: ProtocolEngine[Version.`HTTP/2.0`.type] =
    new StubEngine(Version.`HTTP/2.0`)

  override def spec = suite("TypedEngineNegationSpec")(
    test("single registration typechecks") {
      assertTrue(
        typeChecks("LoomServer(Connector(bind = BindAddress.localhost(0))).withEngine(h2)"),
      )
    },
    test("two versions typecheck") {
      assertTrue(
        typeChecks("LoomServer(Connector(bind = BindAddress.localhost(0))).withEngine(h2).withEngine(h1a)"),
      )
    },
    test("duplicate registration does not compile") {
      val errors =
        typeCheckErrors("LoomServer(Connector(bind = BindAddress.localhost(0))).withEngine(h1a).withEngine(h1b)")
      assertTrue(errors.nonEmpty)
    },
    test("config-flag conditional registration typechecks") {
      assertTrue(
        typeChecks(
          """val base = LoomServer(Connector(bind = BindAddress.localhost(0))).withEngine(h2); if (sys.env.contains("ZIO_HTTP_ENABLE_H1")) base.withEngine(h1a) else base""",
        ),
      )
    },
  )
}
