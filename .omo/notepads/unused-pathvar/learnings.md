# `.unused` PathVar support (Scala 2.13 `RouteBindingMacros.scala`) - learnings

## Dependency resolution (zio-blocks `0.0.0-SNAPSHOT` republish)

- The task's premise held: `Versions.ZioBlocks = "0.0.0-SNAPSHOT"` in `build.mill` did NOT need to
  change. A fresh `./mill 'core.jvm[2.13.18].compile'` resolved straight to the freshly-republished
  jar at `~/.ivy2/local/dev.zio/zio-blocks-endpoint_2.13/0.0.0-SNAPSHOT/jars/` with no cache
  clearing needed.
- Confirmed the jar actually contains the new marker BEFORE writing any code against it, per the
  task's instruction: `unzip -l zio-blocks-endpoint_2.13.jar` showed `zio/blocks/endpoint/
  PathVar$Ignored.class` alongside the existing `PathVar.class`/`PathVar$.class`.
- Many STALE, non-`0.0.0-SNAPSHOT` zio-blocks jars (timestamped/hash-suffixed versions) also exist
  side-by-side in `~/.ivy2/local` from other concurrent work in this environment - harmless since
  the build only resolves the exact `0.0.0-SNAPSHOT` coordinate, but worth knowing so `find`/
  `unzip -l` output isn't confusing.

## `PathVar.Ignored` shape (zio-blocks side, read directly from published sources jar)

- `zio.blocks.endpoint.PathVar.Ignored[Name <: String, Type]` is a `sealed trait` nested in the
  `PathVar` companion object - a SIBLING of `PathVar[Name,Type]`, deliberately NOT a subtype.
  Compiles to `PathVar$Ignored.class` (`javap -p` confirms a plain interface, no runtime footprint,
  identical shape to `PathVar` itself: `public interface zio.blocks.endpoint.PathVar$Ignored<Name
  extends java.lang.String, Type> {}`).
- `typeOf[zio.blocks.endpoint.PathVar.Ignored[_, _]].typeSymbol` (dot notation, exactly like
  writing the type in source, with wildcard type args) resolves correctly for this nested trait in
  a Scala 2.13 blackbox macro - no need for the bytecode `$`-separated form. Compiled clean on the
  first attempt; no trial-and-error needed, matching the existing file's own convention of using
  `typeOf[X]` for well-known zio-blocks/zio-http types (e.g. `typeOf[Unit]`) rather than
  `c.mirror.staticClass(...)` (which `PathVarHandlerMacros.scala` uses instead, for a symbol it
  needs to `appliedType(...)` against dynamically-constructed type args - not needed here since we
  only need `.typeSymbol` for an equality check, never to construct a new applied type from it).
- `SegmentCodec`'s `.unused` (on the `IntSeg`/`LongSeg`/`StringSeg`/`BoolSeg`/`UUIDSeg` case
  classes, confirmed via the Scala 2.13 sources jar) relabels a leaf segment's `PathVars` registry
  entry from `PathVar[Name,Type]` to `PathVar.Ignored[Name,Type]`, zero runtime cost - exactly the
  same API shape as the Scala 3 sibling's zio-blocks-next-endpoint jar.
- Same DSL gotcha as the Scala 3 sibling: `.unused` lives on `SegmentCodec.string("b")` (import
  `zio.blocks.endpoint.SegmentCodec`, qualify the call), NOT on the smart-constructor `string(...)`
  brought in by `import zio.blocks.endpoint.PathCodec._` used everywhere else in the DSL/tests.

## Production change (`RouteBindingMacros.scala`)

- `parseEntries`'s return type changed from `List[(String, Type)]` to
  `List[(String, Type, Boolean)]` (third element = `isIgnored`), matching the existing file's
  "plain tuple, no ceremony" style (mirrors the Scala 3 sibling's identical tuple-widening choice
  in `decomposePathVarTuple`).
- `isIgnored` is computed via `dealiased.typeSymbol == pathVarIgnoredSym`, where
  `pathVarIgnoredSym = typeOf[zio.blocks.endpoint.PathVar.Ignored[_, _]].typeSymbol` is hoisted
  once, outside `parseEntries`, exactly like the existing file hoists `aType`/`pvType`/etc. once at
  the top of `arrowImpl` rather than recomputing per call.
- `parseEntries` is shared by BOTH `pvType` (pattern registry) and `reqType` (handler's phase-1
  requirements), per the existing file's own established sharing convention - `reqEntries`'
  `isIgnored` flag is always `false` in practice (a handler's own declared parameter is built
  exclusively from plain `PathVar[Name,Type]` by `PathVarHandlerMacros.pathVarType`, confirmed by
  reading that file - it has NO code path that could ever produce an `Ignored` entry), so
  `PathVarHandlerMacros.scala` needed ZERO changes, exactly as the task predicted. Verified this by
  reading the whole file rather than assuming.
- `accessExprs`'s matching loop needed only a destructuring update (`case (rName, rType, _)`
  instead of `case (rName, rType)`) - matching genuinely does not care about `isIgnored` at all,
  confirming the task's stated semantics (an `.unused` segment remains fully bindable if
  referenced).
- The "unconsumed -> warn" loop was extended in place (not split into two loops, matching the
  Scala 3 sibling's own choice to merge rather than duplicate iteration) to an `if`/`else if` over
  the four `(isIgnored, consumed)` combinations:
  - plain, unconsumed -> existing "was defined in the path but is never used" warning (unchanged).
  - plain, consumed -> no warning (unchanged).
  - ignored, unconsumed -> no warning (the whole point of `.unused`).
  - ignored, consumed -> NEW "was marked .unused but is referenced by the handler" warning.
- New warning text (matches the Scala 3 sibling verbatim, confirmed by reading their already-merged
  diff in the `zio-http-unused-pathvar-s3` workspace before writing this file's tests):
  `"Variable $name:$tpe was marked .unused but is referenced by the handler"` - same `$name:$tpe`
  formatting convention as the existing "was defined in the path" warning in THIS file (no
  `.show.split('.').last`-style trimming needed here, since Scala 2.13's `Type.toString` already
  prints a bare `String`/`Int`/etc. for these types, as the pre-existing test 14 already proves).
- **Empirically confirmed (not assumed) that Scala 2.13's blackbox macro reporter has NO
  `UniqueMessagePositions`-style same-position warning dedup**, unlike the Scala 3 sibling's real
  bug in dotc's default reporter: all warnings in this file are emitted at the SAME
  `c.enclosingPosition` (no per-entry offset, unlike the Scala 3 fix), and the PRE-EXISTING test 14
  (added before this task, unmodified) already asserts TWO DISTINCT warnings fire from two entries
  sharing that identical position - and it still passes unmodified after this change. No fix was
  needed on the 2.13 side; documented this finding in the file's own comments rather than
  discovering it silently.
- Zero runtime cost: `isIgnored` is a plain `Boolean` resolved entirely at macro-expansion
  (compile) time inside `parseEntries`/`arrowImpl`'s warning-emission logic - it never appears in
  any of the generated `q"..."` trees the macro splices into the call site. The generated runtime
  code (`accessAt`/`positions`/`isIdentity`/`reqRuntimeShape`) is unchanged in shape; only the
  purely-diagnostic `pvEntries.zipWithIndex.foreach` loop gained a new branch.

## Positional-correctness verification (real test, not just a claim)

- Test 17 (`int("a") / SegmentCodec.string("b").unused / int("c") -> handler((a: Int, c: Int) =>
  ...)`, per the task's required case (d)) - confirms `c`'s runtime position is correctly `_3` (not
  `_2`) even though the handler never references `b`, i.e. the `Ignored` entry still occupies its
  real slot in `pvEntries` and is never filtered out. Passed with `a=1 c=3` for the runtime tuple
  `(1, "ignored-b", 3)`.
- Test 18 - same pattern, handler now ALSO consumes `b` (`(a: Int, b: String, c: Int)`) - confirms
  `b`'s decoded value binds correctly (`a=1 b=hello c=3`) while ALSO emitting the new warning
  (verified separately via the compile-warning output in tests 20/21, not asserted in this runtime
  test - runtime correctness and compile-diagnostic content are deliberately split into separate
  tests, mirroring the existing file's own test 12/14 split and the Scala 3 sibling's identical
  choice).

## Test technique (mirrored, not invented)

- Reused the file's existing `FatalWarningsProof.compileScala2(code)` out-of-process `scalac
  -Xfatal-warnings` harness verbatim for tests 19/20/21 - zero changes to that object, per the
  task's instruction to mirror the existing "capture unused-var warning" technique.
- Test numbering continued from the existing file's own scheme (1-16 pre-existing, 17-21 new) -
  matches both this file's own incremental-numbering convention and the Scala 3 sibling's identical
  choice (also 17-21), confirmed by reading their diff for exact parity of warning text and test
  scope before writing these tests.
- Regression suite (fresh, this workspace): `core.jvm[2.13.18].test` = 98 tests passed, 0 failed
  (93 pre-existing + 5 new tests in `PathVarHandlerBindingSpec`). `core.jvm[3.8.3].test` = 94 tests
  passed, 0 failed - confirming this Scala-2.13-only production change has zero cross-platform side
  effects (this workspace's Scala 3 sources are the pre-existing baseline, untouched; the parallel
  Scala 3 task lives entirely in the separate `zio-http-unused-pathvar-s3` workspace, no file
  overlap).
- Scope confirmed via `jj diff --stat`: exactly 2 files touched -
  `zio-http-core/shared/src/main/scala-2/zio/http/RouteBindingMacros.scala` (+60/-11 lines) and
  `zio-http-core/jvm/src/test/scala-2/zio/http/PathVarHandlerBindingSpec.scala` (+84 new lines,
  0 deletions - purely additive). `PathVarHandlerMacros.scala`/`PathVarHandler.scala`,
  `Middleware.scala`, `Routes.scala`, and all zio-blocks files are untouched, exactly per the
  task's "MUST NOT DO" list.
