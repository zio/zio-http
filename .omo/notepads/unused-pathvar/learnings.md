# `.unused` PathVar support (Scala 3 `RouteBinding.scala`) - learnings

## Dependency resolution (zio-blocks `0.0.0-SNAPSHOT` republish)

- The task's premise held: `Versions.ZioBlocks = "0.0.0-SNAPSHOT"` in `build.mill` did NOT need to
  change. `./mill 'core.jvm[3.8.3].compileClasspath'` (via `mill show`, since `compileClasspath`
  prints unreadable `PathRef`/coursier hash prefixes on bare `mill` invocation) resolved straight to
  the freshly-republished jars under
  `/home/nabil_abdel-hafeez.guest/.ivy2/local/dev.zio/zio-blocks-next-endpoint_3/0.0.0-SNAPSHOT/`
  with no cache-clearing needed - a normal fresh `./mill` invocation picked it up automatically, as
  the task predicted. Confirmed the jar actually contains the new marker BEFORE writing any code
  against it, per the task's explicit instruction: `unzip -l .../zio-blocks-next-endpoint_3.jar`
  showed `zio/blocks/endpoint/PathVar$Ignored.class` (Scala 3) and
  `zio/blocks/endpoint/PathVar$Ignored.class` (Scala 2.13 jar too, for the sibling s2 task).
- Multiple STALE, non-`0.0.0-SNAPSHOT`-versioned zio-blocks jars (timestamped/hash-suffixed
  versions like `0.0.43+16-3755c7c5+20260704-1709-SNAPSHOT`) also exist side-by-side in
  `~/.ivy2/local` from other concurrent work in this environment - harmless, since the build only
  ever resolves the exact `0.0.0-SNAPSHOT` coordinate `build.mill` pins, but worth knowing they're
  there so as not to be confused by `find`/`unzip -l` output showing several candidate jars.

## `PathVar.Ignored` shape (zio-blocks side, read directly from published sources jar)

- `zio.blocks.endpoint.PathVar.Ignored[Name <: String, Type]` is a SIBLING `sealed trait` nested in
  the `PathVar` companion object - deliberately NOT a subtype of `PathVar[Name,Type]` itself, per
  the plan. Compiles to `PathVar$Ignored.class`.
- `Symbol.requiredClass("zio.blocks.endpoint.PathVar.Ignored")` (dot notation, exactly like writing
  the type in source) resolves correctly for this nested trait in `quotes.reflect` - no need for the
  bytecode `$`-separated form (`PathVar$Ignored`). Confirmed by a clean compile; no trial-and-error
  needed since the dot form is the same convention already used for `pathVarSymbol`
  (`"zio.blocks.endpoint.PathVar"`).
- `SegmentCodec`'s `.unused` (on `IntSeg`/`LongSeg`/`StringSeg`/`BoolSeg`/`UUIDSeg`) returns a
  same-instance `asInstanceOf`-refined codec whose `PathVars` is relabeled from
  `OnePathVar[PathVar[N,T]]` to `OnePathVar[PathVar.Ignored[N,T]]` - zero runtime cost, matching the
  "runtime-identical to the non-`.unused` version" guarantee documented on the zio-blocks side.
- **Real DSL gotcha worth flagging**: `.unused` lives on `SegmentCodec`'s concrete case classes
  (`SegmentCodec.string("b").unused`), NOT on `PathCodec`'s smart constructors
  (`PathCodec.string("b")`, the ones `import zio.blocks.endpoint.PathCodec._` brings in as bare
  `string(...)` for the rest of the DSL). Writing `string("b").unused` with only `PathCodec._`
  imported does NOT compile (`PathCodec[String]` has no `.unused` member) - the correct usage is
  `SegmentCodec.string("b").unused` (qualified, to avoid a same-name-`string` ambiguity with
  `PathCodec._`'s wildcard import), which then implicitly converts back to a `PathCodec` via
  `PathCodec.segmentToPathCodec` when used with `/`. Confirmed empirically via a throwaway scratch
  file compiled with `mill core.jvm[3.8.3].test.compile` before committing to this shape in the real
  tests - worth flagging since the task's own "Inherited Wisdom" section's worked example
  (`string("b").unused`) is only valid if `string` there resolves to `SegmentCodec.string`, not
  `PathCodec.string` (both are wildcard-importable under the same bare name, which is exactly the
  trap to avoid in real usage).

## Production change (`RouteBinding.scala`)

- `decomposePathVarTuple`'s return type changed from `List[(String, TypeRepr)]` to
  `List[(String, TypeRepr, Boolean)]` (third element = `isIgnored`). Chose a plain tuple over a case
  class to keep the diff minimal and match the file's existing "plain tuple, no ceremony" style for
  this kind of small positional data.
- Extended the `head.dealias match` inside `decomposePathVarTuple`'s `loop` with a second case
  matching `pvIgnoredSym` (alongside the existing `pvSym` case), factoring the shared
  `literalName(nameTpe)` extraction into a tiny local helper to avoid duplicating the
  `ConstantType(StringConstant(s))` match/error-message twice. Both cases still fall through to the
  same catch-all `errorAndAbort` for anything else - so the hard-fail-on-`Ignored` bug the task
  described no longer triggers, and the catch-all's message was updated to mention both accepted
  shapes.
- `tryDecomposePathVarTuple` (handler-side decomposition) was **intentionally left completely
  untouched** except for one doc-comment addition explaining why: a `handler(fn)`'s own
  `RequiredVars` tuple is built exclusively from plain `PathVar[Name,Type]` entries by
  `handlerImpl`'s `pathVarEntryType` - there is no code path that could ever produce an `Ignored`
  entry on the handler side, so recognizing it there would be dead code, not a fix. No zio-blocks or
  `PathVarHandlerMacros.scala`/`PathVarHandler.scala` changes were needed for this - matches the
  task's own stated expectation exactly.
- `arrowImpl`'s matching loop (`positions`) needed only a destructuring update
  (`(pvName, pvType, _)` instead of `(pvName, pvType)`) - matching genuinely does not care about
  `isIgnored` at all, confirming the task's stated semantics (an `.unused` segment remains fully
  bindable).
- Merged the "unconsumed -> warn" loop and the new "ignored-but-consumed -> warn" loop into a
  SINGLE `pvEntries.zipWithIndex.foreach`, using an `if`/`else if` over the four
  `(isIgnored, consumed)` combinations. This was a deliberate choice over two separate loops: since
  every `pvEntries` index is unique and each entry can trigger AT MOST one of the two warnings, a
  single loop keeps the existing `distinctPos` per-index-offset technique trivially safe (no risk of
  two DIFFERENT warnings ever computing the same offset for two DIFFERENT entries, since the offset
  is keyed off the entry's own unique index, and an entry only ever contributes zero or one
  diagnostic).
- New warning text: `"Variable $name:$type was marked .unused but is referenced by the handler"` -
  matches the OLD warning's exact formatting convention (`$name:$type`, `type.show.split('.').last`
  for a bare display name, e.g. `String` not `java.lang.String` / `scala.Predef.String`).
- Zero runtime/allocation impact: `isIgnored` is a plain `Boolean` resolved entirely inside
  `decomposePathVarTuple`/`arrowImpl`'s macro-expansion-time logic (used only to decide whether/which
  `report.warning` fires) - it never appears in any of the generated `Expr`/`Term` code the macro
  splices into the call site. The generated runtime code for `positions`/`buildTuple`/`isIdentity` is
  BYTE-FOR-BYTE the same shape as before this change (same functions, same inputs beyond the
  now-ignored third tuple element) - confirmed by inspection, no `javap` re-run was needed since no
  new runtime-level branch was introduced at all (this is a pure "extra compile-time boolean feeding
  two `if`/`else if` branches inside `report.warning` calls" change).

## Positional-correctness verification (real test, not just a claim)

- Test 17 (`int("a") / SegmentCodec.string("b").unused / int("c") -> handler((a: Int, c: Int) =>
  ...)`, per the task's required case (d)) - confirms `c`'s runtime position is correctly `2` (not
  `1`) even though the handler never references `b`, i.e. the `Ignored` entry still occupies its
  real slot in `pvEntries` (and therefore in `positions`' indexing space) and is never filtered out.
  Passed with `a=1 c=3` for input path `/1/ignored-b/3`.
- Test 18 - same pattern, handler now ALSO consumes `b` (`(a: Int, b: String, c: Int)`) - confirms
  `b`'s decoded value binds correctly (`a=1 b=hello c=3` for `/1/hello/3`) while ALSO emitting the
  new warning (verified separately via the compile-warning output, not asserted in this runtime
  test - runtime correctness and compile-diagnostic content are deliberately split into separate
  tests, mirroring the existing file's own test 4/14 split).

## Test technique (mirrored, not invented)

- Reused the file's existing `FatalWarningsProof.compileScala3(code)` out-of-process `dotc -Werror`
  harness verbatim (zero changes to that object) for tests 19/20/21 - exactly per the task's
  instruction to mirror the existing "capture unused-var warning" technique rather than invent a new
  one. Confirmed the harness's own doc comment already generalizes correctly to any warning this
  macro emits (it just execs `dotc` and captures stdout/stderr), so no harness changes were needed.
- Test numbering continued from the existing file's own scheme (1-16 pre-existing, 17-21 new) rather
  than renumbering/reorganizing anything - matches the file's own incremental-numbering convention
  build up across prior Todos (4/5/6/7/8/10/11) referenced in the sibling
  `route-pattern-typed-vars` notepad.
- Regression suite (fresh, both platforms, this workspace): `core.jvm[3.8.3].test` = 99 tests
  passed, 0 failed (16 pre-existing + 6 new tests in `PathVarHandlerBindingSpec`, confirmed via
  `testOnly zio.http.PathVarHandlerBindingSpec` = 22/22 passed). `mill 'core.jvm[2.13.18].test'` = 93
  tests passed, 0 failed - completely unchanged, confirming this Scala-3-only production change has
  zero cross-platform side effects (the parallel Scala 2.13 task in `zio-http-unused-pathvar-s2` is
  fully independent, no file overlap).
- Scope confirmed via `jj diff --stat`: exactly 2 files touched -
  `zio-http-core/shared/src/main/scala-3/zio/http/RouteBinding.scala` (+92/-22) and
  `zio-http-core/jvm/src/test/scala-3/zio/http/PathVarHandlerBindingSpec.scala` (+78 new lines,
  0 deletions - purely additive). `PathVarHandlerMacros.scala`/`PathVarHandler.scala`,
  `Middleware.scala`, `Routes.scala`, and all zio-blocks files are untouched, exactly per the task's
  "MUST NOT DO" list.
