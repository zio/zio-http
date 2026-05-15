---
id: http-test-aspect
title: "HttpTestAspect"
---

`HttpTestAspect` is a ZIO Test aspect utility that temporarily configures HTTP application modes for testing. It provides composable test aspects to run specific tests under different deployment modes (Dev, Preprod, Prod), allowing verification of mode-dependent handler behavior without affecting other tests. Mode settings automatically restore after each test.

The public API provides three mode aspects:

```scala
object HttpTestAspect {
  val devMode: TestAspectAtLeastR[Scope]
  val preprodMode: TestAspectAtLeastR[Scope]
  val prodMode: TestAspectAtLeastR[Scope]
}
```

Key properties:
- **Test Composition** — Acts as a ZIO Test aspect applicable with `@@` operator
- **Automatic Restoration** — Previous mode automatically restores after test completes
- **Scoped Mode Change** — Uses system property to set `Mode.current` for test duration
- **No Test Mutation** — Does not affect other tests in the same suite

### Role in Module

`HttpTestAspect` is the **test utility for mode-dependent behavior verification** in zio-http-testkit. It provides a way to test handlers that behave differently across deployment modes without requiring actual mode changes to the application.

**Use with:** TestServer (mode-dependent routes), TestClient (mode-dependent external calls), Mode (checks current mode in handlers)

**Complementary types:**
- TestServer — For integration testing mode-dependent routes
- TestClient — For testing mode-dependent client calls
- Mode — For reading current mode in handler logic

## Motivation

HTTP applications often have mode-dependent behavior: different error handling in development vs. production, extra diagnostics in staging, stricter validation in production. Testing this behavior requires temporarily switching the application mode for specific tests.

`HttpTestAspect` solves this by providing composable aspects that set the mode for the duration of a test and restore the previous mode afterward:

```scala
test("handles errors in dev mode") {
  // Test logic here
  assertTrue(Mode.isDev)
} @@ HttpTestAspect.devMode
```

Use `HttpTestAspect` when:
- Your handler behavior differs by deployment mode
- You want to test mode-specific error handling
- You need to verify feature flags that depend on the mode
- You want to ensure tests don't interfere with each other's mode settings

## Mode Types

Three modes are available:

- **`Mode.Dev`** — Development mode (default). Typically enables extra diagnostics, verbose error messages, and developer-friendly defaults.
- **`Mode.Preprod`** — Pre-production/staging mode. An intermediate mode between dev and production for testing production-like behavior in a safe environment.
- **`Mode.Prod`** — Production mode. Typically strict validation, minimal error details, optimized performance.

## Using HttpTestAspect

To test mode-dependent behavior, apply mode aspects to your tests.

### Basic Mode Override

Apply a mode aspect to a test to set the mode for that test only:

```scala mdoc:passthrough
import utils._
printSource("zio-http-example-testing/src/test/scala/example/testing/ModeBasicSetup.scala")
```

([source](https://github.com/zio/zio-http/blob/main/zio-http-example-testing/src/test/scala/example/testing/ModeBasicSetup.scala))

### Reading the Mode

Inside a test, read the current mode using:

```scala
import zio._
import zio.http._

// Full mode value
val mode: Mode = Mode.current

// Convenience checks
val isDev = Mode.isDev
val isPreprod = Mode.isPreprod
val isProd = Mode.isProd
```

## Core Operations

The following examples demonstrate testing different modes.

### Testing Dev Mode Behavior

Verify handlers that enable extra diagnostics in development:

```scala mdoc:passthrough
import utils._
printSource("zio-http-example-testing/src/test/scala/example/testing/TestAspectDevMode.scala")
```

([source](https://github.com/zio/zio-http/blob/main/zio-http-example-testing/src/test/scala/example/testing/TestAspectDevMode.scala))

### Testing Prod Mode Behavior

Verify handlers that enforce stricter validation in production:

```scala mdoc:passthrough
import utils._
printSource("zio-http-example-testing/src/test/scala/example/testing/TestAspectProdMode.scala")
```

([source](https://github.com/zio/zio-http/blob/main/zio-http-example-testing/src/test/scala/example/testing/TestAspectProdMode.scala))

### Testing Preprod Mode Behavior

Verify handlers that behave differently in staging environments:

```scala mdoc:passthrough
import utils._
printSource("zio-http-example-testing/src/test/scala/example/testing/TestAspectPreprodMode.scala")
```

([source](https://github.com/zio/zio-http/blob/main/zio-http-example-testing/src/test/scala/example/testing/TestAspectPreprodMode.scala))

### Multiple Mode Tests with Sequential Execution

When testing multiple modes in the same suite, use `TestAspect.sequential` to avoid race conditions (mode is a JVM-wide setting):

```scala mdoc:passthrough
import utils._
printSource("zio-http-example-testing/src/test/scala/example/testing/TestAspectMultiMode.scala")
```

([source](https://github.com/zio/zio-http/blob/main/zio-http-example-testing/src/test/scala/example/testing/TestAspectMultiMode.scala))

## Common Patterns

Here are practical patterns for using mode-dependent behavior.

### Mode-Conditional Routes

Define routes that only exist in certain modes:

```scala
import zio._
import zio.http._

val debugRoutes =
  if (Mode.isDev || Mode.isPreprod) Routes(
    Method.GET / "debug" / "health" -> handler(Response.text("OK"))
  )
  else Routes.empty

val appRoutes = Routes(
  Method.GET / "health" -> handler(Response.ok)
) ++ debugRoutes
```

### Mode-Conditional Error Handling

Customize error responses based on mode:

```scala
import zio._
import zio.http._

handler { (_: Request) =>
  ZIO.fail(new Exception("Something went wrong"))
    .catchAll { err =>
      if (Mode.isDev || Mode.isPreprod)
        ZIO.succeed(
          Response.status(Status.InternalServerError)
            .addHeader("X-Error", err.getMessage)
            .addHeader("X-Trace", err.getStackTrace.mkString("\n"))
        )
      else
        ZIO.succeed(Response.status(Status.InternalServerError))
    }
}
```

### Mode-Conditional Configuration

Adapt server configuration and resources based on mode:

```scala
import zio._
import zio.http._

// Use mode to determine resource allocation
val resourcePoolSize = if (Mode.isProd) 16 else 4
val enableVerboseLogging = Mode.isDev

// Configure handlers based on mode
val handlerConfig = if (Mode.isProd) {
  // Strict timeout in production
  ZIO.succeed(5000)
} else {
  // Lenient timeout in development
  ZIO.succeed(30000)
}
```

### Testing Feature Flags

Test handlers that use mode-based feature flags:

```scala mdoc:passthrough
import utils._
printSource("zio-http-example-testing/src/test/scala/example/testing/TestAspectFeatureFlag.scala")
```

([source](https://github.com/zio/zio-http/blob/main/zio-http-example-testing/src/test/scala/example/testing/TestAspectFeatureFlag.scala))

## Integration with Other Types

**`TestServer`** — Use mode aspects with `TestServer` integration tests to verify mode-dependent route behavior:

```scala mdoc:passthrough
import utils._
printSource("zio-http-example-testing/src/test/scala/example/testing/TestAspectTestServerMode.scala")
```

([source](https://github.com/zio/zio-http/blob/main/zio-http-example-testing/src/test/scala/example/testing/TestAspectTestServerMode.scala))

**`TestClient`** — Use mode aspects when testing handlers that call external services conditionally based on mode:

```scala mdoc:passthrough
import utils._
printSource("zio-http-example-testing/src/test/scala/example/testing/TestAspectTestClientMode.scala")
```

([source](https://github.com/zio/zio-http/blob/main/zio-http-example-testing/src/test/scala/example/testing/TestAspectTestClientMode.scala))

## Best Practices

1. **Use `TestAspect.sequential`** when multiple tests in the same suite use different mode aspects to prevent race conditions
2. **Document why modes differ** in your handler logic to help future maintainers understand the branching
3. **Test all modes** that your application supports to ensure consistent behavior
4. **Keep mode-dependent code simple** — complex branching can hide bugs; prefer simple guards over intricate logic
5. **Combine with config** — For complex environment-dependent behavior (secrets, database URLs), use a dedicated config service alongside modes

## API Reference

The following test aspects and mode query functions comprise the complete public API.

### Test Aspects

| Aspect | Type | Purpose |
|--------|------|---------|
| `HttpTestAspect.devMode` | `TestAspectAtLeastR[Scope]` | Run test under Dev mode |
| `HttpTestAspect.preprodMode` | `TestAspectAtLeastR[Scope]` | Run test under Preprod mode |
| `HttpTestAspect.prodMode` | `TestAspectAtLeastR[Scope]` | Run test under Prod mode |

### Usage Pattern

Apply all aspects using ZIO Test's `@@` operator:

```scala
test("my test") {
  // test logic
} @@ HttpTestAspect.devMode
```

### Mode Queries in Handlers

Use these functions to query the current mode in your handler code:

```scala
Mode.current      // Get current mode (Dev | Preprod | Prod)
Mode.isDev        // Boolean check for Dev mode
Mode.isPreprod    // Boolean check for Preprod mode
Mode.isProd       // Boolean check for Prod mode
```

## Implementation Details

- **Mechanism** — Uses system property `"zio.http.mode"` to set mode
- **Scope** — Mode change is scoped to test duration only
- **Restoration** — Previous mode is captured and restored after test
- **Thread-Safe** — Mode is JVM-wide; use `TestAspect.sequential` for multiple mode tests
- **No Manual Cleanup** — Automatic restoration means no try-finally needed

## See Also

- [Dev / Preprod / Prod Modes](../../concepts/dev-mode.md) — Comprehensive guide to application modes
- [TestServer](./test-server.md) — Integration testing with mode configuration
- [TestClient](./test-client.md) — Mocking external services for mode testing
- [Testing HTTP Applications](../../guides/testing-http-apps) — Comprehensive testing guide
