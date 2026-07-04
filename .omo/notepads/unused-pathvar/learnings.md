# `.unused` PathVar support (Scala 3 `RouteBinding.scala`) - learnings

## Dependency resolution (zio-blocks `0.0.0-SNAPSHOT` republish)

- The task's premise held: `Versions.ZioBlocks = "0.0.0-SNAPSHOT"` in `build.mill` did NOT need to
  change. `./mill 'core.jvm[3.8.3].compileClasspath'` (via `mill show`, since `compileClasspath`
  prints unreadable `PathRef`/coursier hash prefixes on bare `mill` invocation) resolved straight to
  the freshly-republished jars under
  `/home/nabil_abdel-hafeez.guest/.ivy2/local/dev.zio/zio-blocks-next-endpoint_3/0.0.0-SNAPSHOT/`
  with no cache-clearing needed - a normal fresh `./mill` invocation picked it up automatically, as
  the task predicted.
- Multiple STALE, non-`0.0.0-SNAPSHOT`-versioned zio-blocks jars also exist side-by-side in
  `~/.ivy2/local` from other concurrent work - harmless, since the build only ever resolves the
  exact `0.0.0-SNAPSHOT` coordinate `build.mill` pins.

## `PathVar.Ignored` shape (zio-blocks side)

- `zio.blocks.endpoint.PathVar.Ignored[Name <: String, Type]` is a SIBLING `sealed trait` nested in
  the `PathVar` companion object - deliberately NOT a subtype of `PathVar[Name,Type]` itself.
- `Symbol.requiredClass("zio.blocks.endpoint.PathVar.Ignored")` (dot notation) resolves correctly
  for this nested trait in `quotes.reflect`.
- `SegmentCodec`'s `.unused` (on `IntSeg`/`LongSeg`/`StringSeg`/`BoolSeg`/`UUIDSeg`) returns a
  same-instance `asInstanceOf`-refined codec whose `PathVars` is relabeled from
  `OnePathVar[PathVar[N,T]]` to `OnePathVar[PathVar.Ignored[N,T]]` - zero runtime cost.
- **Real DSL gotcha**: `.unused` lives on `SegmentCodec`'s concrete case classes
  (`SegmentCodec.string("b").unused`), NOT on `PathCodec`'s smart constructors (`string("b")`
  brought in by `import zio.blocks.endpoint.PathCodec._`). Use the qualified `SegmentCodec.string`.

## Production change (`RouteBinding.scala`)

- `decomposePathVarTuple`'s return type changed from `List[(String, TypeRepr)]` to
  `List[(String, TypeRepr, Boolean)]` (third element = `isIgnored`).
- Extended the `head.dealias match` with a second case matching `pvIgnoredSym` alongside the
  existing `pvSym` case; catch-all `errorAndAbort` message updated to mention both shapes.
- `tryDecomposePathVarTuple` (handler-side decomposition) intentionally left untouched except for
  a doc-comment: a handler's own `RequiredVars` tuple is built exclusively from plain
  `PathVar[Name,Type]` by `handlerImpl`, so it can never contain an `Ignored` entry.
- `arrowImpl`'s matching loop needed only a destructuring update (`(pvName, pvType, _)`) - matching
  ignores `isIgnored` entirely, so `.unused` segments stay bindable if referenced.
- Merged the "unconsumed -> warn" loop and the new "ignored-but-consumed -> warn" loop into a
  SINGLE `pvEntries.zipWithIndex.foreach` over the four `(isIgnored, consumed)` combinations,
  reusing the existing `distinctPos` per-index-offset technique (dotc's `UniqueMessagePositions`
  reporter drops same-position diagnostics).
- New warning text: `"Variable $name:$type was marked .unused but is referenced by the handler"`.
- Zero runtime/allocation impact: `isIgnored` is resolved entirely at macro-expansion time; the
  generated runtime code for `positions`/`buildTuple`/`isIdentity` is byte-for-byte unchanged.

## Positional-correctness verification (real tests)

- Test 17: `int("a") / SegmentCodec.string("b").unused / int("c") -> handler((a, c) => ...)` -
  confirms `c`'s runtime position is correctly `2` (not `1`); `Ignored` entries are never filtered
  out of the indexing space. Passed `a=1 c=3` for `/1/ignored-b/3`.
- Test 18: same pattern with `b` also consumed - confirms correct binding (`a=1 b=hello c=3`) while
  the new warning fires (checked separately via compile-warning tests).

## Test technique (mirrored, not invented)

- Reused `FatalWarningsProof.compileScala3(code)` verbatim for tests 19/20/21 (mirrors the
  existing unused-var-warning capture technique). Test numbering continued from 1-16 to 17-21.
- Regression suite (this workspace): `core.jvm[3.8.3].test` = 99/99 passed (22/22 in
  `PathVarHandlerBindingSpec`). `core.jvm[2.13.18].test` = 93/93 passed, unchanged.
- Scope: exactly 2 files touched (`RouteBinding.scala` +92/-22,
  `PathVarHandlerBindingSpec.scala` scala-3 +78/-0). No zio-blocks/`Middleware`/`Routes` changes.

---

# `.unused` PathVar support (Scala 2.13 `RouteBindingMacros.scala`) - learnings

## Dependency resolution (zio-blocks `0.0.0-SNAPSHOT` republish)

- `Versions.ZioBlocks = "0.0.0-SNAPSHOT"` did NOT need to change. A fresh
  `./mill 'core.jvm[2.13.18].compile'` resolved straight to the freshly-republished jar with no
  cache clearing needed. Confirmed via `unzip -l zio-blocks-endpoint_2.13.jar` that
  `PathVar$Ignored.class` was present before writing any code against it.

## `PathVar.Ignored` shape (zio-blocks side)

- Same sibling `sealed trait` as the Scala 3 side, compiles to `PathVar$Ignored.class`
  (`javap -p` confirms a plain zero-footprint interface, identical shape to `PathVar` itself).
- `typeOf[zio.blocks.endpoint.PathVar.Ignored[_, _]].typeSymbol` (dot notation, wildcard type
  args) resolves correctly in a Scala 2.13 blackbox macro on the first attempt.
- `SegmentCodec`'s `.unused` relabels a leaf segment's `PathVars` entry from `PathVar[Name,Type]`
  to `PathVar.Ignored[Name,Type]`, zero runtime cost - same API shape as the Scala 3 sibling jar.
- Same DSL gotcha as the Scala 3 sibling: use qualified `SegmentCodec.string("b").unused`, not the
  bare `string(...)` from `PathCodec._`.

## Production change (`RouteBindingMacros.scala`)

- `parseEntries`'s return type changed from `List[(String, Type)]` to
  `List[(String, Type, Boolean)]` (third element = `isIgnored`), mirroring the Scala 3 sibling's
  tuple-widening choice.
- `isIgnored` computed via `dealiased.typeSymbol == pathVarIgnoredSym`, hoisted once outside
  `parseEntries`.
- `parseEntries` is shared by both `pvType` and `reqType`; `reqEntries`' `isIgnored` is always
  `false` in practice (a handler's own parameter is built exclusively from plain `PathVar` by
  `PathVarHandlerMacros.pathVarType` - confirmed by reading that file, zero changes needed there).
- `accessExprs`'s matching loop needed only a destructuring update (`case (rName, rType, _)`) -
  matching ignores `isIgnored` entirely.
- The "unconsumed -> warn" loop extended in place to an `if`/`else if` over the four
  `(isIgnored, consumed)` combinations, same four cases as the Scala 3 sibling.
- New warning text matches the Scala 3 sibling verbatim (confirmed by reading their diff before
  writing this file's tests): `"Variable $name:$tpe was marked .unused but is referenced by the
  handler"`.
- **Empirically confirmed Scala 2.13's blackbox macro reporter has NO `UniqueMessagePositions`-
  style same-position warning dedup** (unlike dotc): all warnings here fire at the same
  `c.enclosingPosition`, and the pre-existing test 14 already proves two distinct warnings can
  fire from that same position - no per-entry position offset needed on this platform.
- Zero runtime cost: `isIgnored` is resolved entirely at macro-expansion time; `accessAt`/
  `positions`/`isIdentity`/`reqRuntimeShape` are unchanged in shape.

## Positional-correctness verification (real tests)

- Test 17: `int("a") / SegmentCodec.string("b").unused / int("c") -> handler((a, c) => ...)` -
  confirms `c`'s runtime position is correctly `_3` (not `_2`). Passed `a=1 c=3` for the runtime
  tuple `(1, "ignored-b", 3)`.
- Test 18: same pattern with `b` also consumed - confirms correct binding (`a=1 b=hello c=3`).

## Test technique (mirrored, not invented)

- Reused `FatalWarningsProof.compileScala2(code)` verbatim for tests 19/20/21. Test numbering
  continued from 1-16 to 17-21, matching the Scala 3 sibling's identical numbering for parity.
- Regression suite (this workspace): `core.jvm[2.13.18].test` = 98/98 passed (93 pre-existing + 5
  new). `core.jvm[3.8.3].test` = 94/94 passed, unchanged.
- Scope: exactly 2 files touched (`RouteBindingMacros.scala` +60/-11,
  `PathVarHandlerBindingSpec.scala` scala-2 +84/-0). No zio-blocks/`Middleware`/`Routes` changes.

## Cross-platform merge note (Atlas)

- Both platform changes were merged (`jj new <s3-commit> <s2-commit>`) into a single combined
  commit on top of `8629534f55da`. Only this notepad conflicted (both sides independently
  appended) - the production code files (`RouteBinding.scala`, `RouteBindingMacros.scala`, both
  `PathVarHandlerBindingSpec.scala` test files) merged with ZERO conflicts, confirming the two
  tasks were genuinely file-disjoint as designed.
- Atlas independently re-ran `./mill 'core.jvm[3.8.3].test'` and `./mill 'core.jvm[2.13.18].test'`
  in BOTH the `zio-http-unused-pathvar-s3` and `zio-http-unused-pathvar-s2` workspaces (4 runs
  total) before merging: 99/99, 93/93, 98/98, 94/94 - all match subagent claims exactly, zero
  discrepancies found.
