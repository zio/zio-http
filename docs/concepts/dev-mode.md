# Dev / Preprod / Prod Modes

ZIO HTTP provides a simple built-in notion of application "mode" so you can adapt behavior (e.g. enable extra diagnostics in development, stricter settings in production, other routes, different error handling) without wiring your own config keys everywhere.

The available modes are:

- `Mode.Dev` (default if nothing is configured)
- `Mode.Preprod` (a staging / pre‑production environment)
- `Mode.Prod` (production)

## Reading the Current Mode

Use any of the following helpers:

```scala
import zio.http.Mode

// Full value
def m: Mode = Mode.current

// Convenience booleans
val isDev     = Mode.isDev
val isPreprod = Mode.isPreprod
val isProd    = Mode.isProd
```

## Configuring the Mode

The mode is determined in this precedence order:

1. JVM System Property: `-Dzio.http.mode=<dev|preprod|prod>`
2. Environment Variable: `ZIO_HTTP_MODE=<dev|preprod|prod>`
3. Fallback: `dev`

Examples:

```bash
# Using a JVM system property
sbt "run -Dzio.http.mode=preprod"

# Using an environment variable (takes effect if the system property is NOT set)
ZIO_HTTP_MODE=prod sbt run
```

Unknown values cause a warning on stderr and the mode falls back to `dev`:

## Typical Use Cases

You can branch on the mode to enable / disable features:

```scala
import zio._
import zio.http._

val extraRoutes: Routes[Any, Nothing] =
  if (Mode.isDev) SwaggerUI.routes("docs", OpenAPIGen.empty)
  else Routes.empty

val baseRoutes: Routes[Any, Nothing] = Routes(
  Method.GET / "health" -> handler(Response.ok)
)

val appRoutes = baseRoutes ++ extraRoutes
```

Or adapt server config:

```scala
val serverConfig =
  if (Mode.isProd) Server.Config.default
    .leakDetection(false)
    .requestDecompression(true)
  else Server.Config.default
    .leakDetection(true)           // extra visibility in dev
    .maxThreads(4)                 // keep lighter in local dev
```

## Testing Modes

Inside tests you generally want to *temporarily* switch the mode to verify conditional behavior. The testkit provides aspects in `zio.http.HttpTestAspect`:

- `HttpTestAspect.devMode`
- `HttpTestAspect.preprodMode`
- `HttpTestAspect.prodMode`

Each aspect sets the mode for the duration of the test, restoring the previous mode afterward. This allows you to write tests that depend on specific modes without affecting other tests.

Example:

```scala
import zio.test._
import zio.http._

object ModeExamplesSpec extends ZIOSpecDefault {
  def spec = suite("ModeExamplesSpec")(
    test("enables preprod logic") {
      assertTrue(Mode.current == Mode.Preprod)
    } @@ HttpTestAspect.preprodMode,

    test("enables prod logic") {
      assertTrue(Mode.isProd)
    } @@ HttpTestAspect.prodMode,
  ) @@ TestAspect.sequential // IMPORTANT, see below
}
```

### Why `TestAspect.sequential`?

The mode is stored per JVM. When you apply different mode aspects to multiple tests in the **same suite**, running them in parallel could cause races (e.g. one test reads prod while another just switched to preprod). Adding `@@ TestAspect.sequential` ensures the suite’s tests execute one after another so each mode override is isolated.

If every test suite uses only one mode (or you wrap all tests in a single aspect at the suite level), sequential execution is not strictly necessary. It is required only when multiple tests in the same suite each apply different mode aspects.

## Quick Reference

| Task | How |
|------|-----|
| Read current mode | `Mode.current` |
| Check if dev | `Mode.isDev` |
| Run in preprod | `-Dzio.http.mode=preprod` or `ZIO_HTTP_MODE=preprod` |
| Override in a test | `test("...") { ... } @@ HttpTestAspect.prodMode` |
| Avoid race conditions | Apply `@@ TestAspect.sequential` to suite when multiple mode aspects are used |

## When *Not* to Use Mode

For complex environment-dependent configuration (database URLs, secrets, feature flags) prefer a dedicated configuration service (e.g. `zio-config`).
