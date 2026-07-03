# route-pattern-typed-vars learnings (zio-http side)
<!-- This is the zio-http-repo copy. The orchestrator-side copy (Todo 1-3, zio-blocks findings)
     lives at /Users/nabil_abdel-hafeez/.omo/notepads/route-pattern-typed-vars/learnings.md.
     This file covers zio-http Todo 4 (Scala 3) and Todo 5 (Scala 2.13 parity), merged here via
     a jj rebase that unified the two previously-divergent branches (see "Todo 6" section at the
     end for the rebase/merge record) so a single Todo 6 workspace can build on both. -->

## Todo 5 findings (Scala 2.13 `handler(fn)` macro + `RoutePattern.->` derivation)

### Workspace / environment setup
- zio-http had NO `.jj` at all yet (plain git repo, dirty working tree per the plan's Risks section). Ran `jj git init --colocate` at the repo root (safe, non-destructive - imports the existing git state as `@`'s parent chain), then `jj workspace add ../zio-http-handler-macro-s2`. The NEW workspace's `@` parent turned out to be the last REAL commit (`test(v4): comprehensive test suite...`), NOT the dirty uncommitted state - `jj workspace add` gave a clean base for free, with zero risk of touching the pre-existing dirty files.
- `./mill` (the repo's own wrapper script) must be used, not a system-wide `mill` binary - the system one hit a broken/unrelated download URL. The repo's wrapper correctly finds the pre-cached native binary at `~/.cache/mill/download/1.1.7-native-linux-aarch64`. Always pass an explicit `workdir` to the repo root; running mill from an unrelated cwd tries to create `<cwd>/out` and can hit `AccessDeniedException` on read-only paths.
- **zio-blocks' `0.0.0-SNAPSHOT` publish from Todo 3 was INCOMPLETE for zio-http's actual needs**: `core.jvm`'s `mvnDeps` also pulls `blocksDep("config")`, `blocksDep("config-yaml")`, `blocksDep("telemetry")`, `blocksDep("html")`, `blocksDep("http-model-schema")` (all pre-existing, unrelated to PathVars) - none of these were published at the pinned `0.0.0-SNAPSHOT` (only Todo 3's own transitive closure was). Fixed by publishing the missing five (plus a further transitive one, `schema-yaml`, needed by `config-yaml`) from a zio-blocks workspace at the SAME pinned version: `sbt --client '++2.13.18; set ThisBuild / version := "0.0.0-SNAPSHOT"; configJVM/publishLocal; config-yamlJVM/publishLocal; telemetryJVM/publishLocal; htmlJVM/publishLocal; http-model-schemaJVM/publishLocal; schema-yamlJVM/publishLocal'`. This is a pure PUBLISH operation (no zio-blocks source edited, no zio-blocks commit made) and was necessary before `mill 'core.jvm[2.13.18].test'` could even resolve dependencies - baseline (pre-Todo-5) was otherwise broken for this exact reason, unrelated to any Todo 1-5 code.
- Baseline (before any Todo 5 code) with the above publishes in place: `mill 'core.jvm[2.13.18].test'` = 59/59 tests, 245/245 mill tasks green.

### CRITICAL, independently-confirmed, PRE-EXISTING zio-blocks BUG (out of Todo 5's scope - zio-blocks untouched)
- **`zio.blocks.endpoint.PathVarTuples` is declared `private[endpoint]`.** Its macro-materialized implicit (`PathVarTuples.Combine.concat`, an `implicit def ... = macro ...` inside a `private[endpoint] object`) is therefore INACCESSIBLE to Scala's implicit search from ANY call site outside the `zio.blocks.endpoint` package. This breaks `PathCodecOps.++`/`/` and `RoutePatternOps./` (both PUBLIC, meant to be used externally, e.g. by zio-http) for EVERY multi-segment `PathVars` composition (2+ captured/literal segments combined via `/`), for EVERY external consumer, not just this todo's macros - a genuine, severe, previously-unknown regression from Todo 2/3's implementation.
- **Reproduced and root-caused in complete isolation**, with zero of this todo's new files present (confirmed on a totally clean `mill clean` + fresh compile, ruling out incremental-compiler staleness):
  - `package zio.http; ...; Method.GET / SegmentCodec.int("a") / SegmentCodec.string("b")` → `could not find implicit value for parameter _pathVarsCombiner: zio.blocks.endpoint.PathVarTuples.Combine.WithOut[this.PathVars,this.PathVars,PVC]` (fails even for the most minimal 2-operand chain, regardless of `PathCodec.int` vs `SegmentCodec.int`, vals vs fully inline, bare string vs `literal(...)`).
  - The IDENTICAL code, declared `package zio.blocks.endpoint` instead (i.e. inside the defining package), compiles cleanly - conclusive proof the failure is `private[endpoint]`-caused, not a real usage bug.
  - Single-segment patterns (`Method.GET / int("id")`, no further `/` composition) are UNAFFECTED, since `RoutePattern.MethodSyntax./` needs no `PathVarTuples` combiner at all (direct pass-through).
  - My FIRST exploratory scratch-test run appeared to "pass" for a multi-segment chain - this was a stale/incremental-compilation false positive (Zinc reusing a cached result); every subsequent clean-build run reproduces the failure deterministically. Lesson: always `mill clean` before trusting a "it compiled" result when whitebox-macro-backed implicits are involved.
- **Recommended zio-blocks fix (for whoever owns that repo/plan next - NOT done here, zio-blocks is out of this todo's scope):** widen `PathVarTuples`'s visibility from `private[endpoint]` to `private[blocks]` (or remove the modifier) so its implicit is resolvable from external, cross-module consumers like zio-http while still not being a public API surface.
- **Workaround used here (zio-http-side only, no zio-blocks changes):** build the equivalent `PathCodec`/`RoutePattern` value using ONLY genuinely public APIs - `PathCodec.Concat` (a public final case class) plus `zio.blocks.combinators.Tuples.Tuples` (the SEPARATE, PUBLIC, unrestricted combinator for the real runtime value type) - then ascribe the exact `PathVars` refined type by hand via `.asInstanceOf`, exactly mirroring what `PathCodecOps.++`/`RoutePatternOps./` already do internally (they too are just `.asInstanceOf` bridges over a value the compiler can't otherwise verify precisely):
  ```scala
  private def concat2[A, B, C](left: PathCodec[A], right: PathCodec[B])(implicit
    combiner: Tuples.Tuples.WithOut[A, B, C]
  ): PathCodec[C] = PathCodec.Concat(left, right, combiner)

  private def multiSegment[A, PV](method: Method, pathCodec: PathCodec[A]): RoutePattern[A] { type PathVars = PV } =
    RoutePattern(method, pathCodec).asInstanceOf[RoutePattern[A] { type PathVars = PV }]
  ```
  Used only in the new test file for Worked Examples needing 2+ path segments (2/3, 6/7, 10, 11); single-segment examples use the normal, unaffected `GET / int("id")` syntax. This is a test-only workaround; it does not touch or paper over anything in the shipped macro/production code.

### Two real `ClassCastException` bugs found and fixed during implementation (both same root cause)
- **Root cause**: a lambda's OWN DECLARED parameter type, if it names any CONCRETE (non-erased-to-`Object`) class - e.g. `Tuple1[X]`, `PathVar[N,T]` (a real, if uninstantiable, trait) - gets a REAL JVM bridge-method cast generated by scalac for that parameter (Scala/JVM generics erasure inserts a `CHECKCAST` in the synthetic bridge `apply(Object...): Object` that every `FunctionN` instance needs). If the ACTUAL runtime value passed in doesn't match that concrete class (e.g. a bare boxed `Integer` where `Tuple1`/`PathVar` was declared), this throws a genuine `ClassCastException` at invocation time - this is NOT specific to macros, it is fundamental JVM/Scala-generics behavior.
  - This also applies to plain, standalone `.asInstanceOf[ConcreteClass]` EXPRESSIONS on a value (not just lambda parameters) - `(someInt).asInstanceOf[PathVar[N,T]]` crashes immediately, the same as if it were a lambda parameter.
  - The FIX in both places: never cast a bare VALUE to the phantom type. Instead, cast the `Handler[Ctx, Vars]` (a generic TRAIT) itself via `.asInstanceOf[Handler[Ctx, OtherVars]]` - reparameterizing a generic trait via `asInstanceOf` is always a zero-cost, no-bytecode-check operation (unlike casting a value to a concrete class), because `Handler`'s own abstract `handle` method is generic/erased at the trait level.
  - Fix 1 (phase 1, `PathVarHandlerMacros.handlerImpl`): the lambda passed to `Handler.extracted[Ctx, Vars]` must declare its own `vars` parameter using the REAL runtime shape (`runtimeShapeOf`: `Unit`/bare-type/`TupleN`, matching what's ACTUALLY passed), never the phantom `RequiredVars` type directly; the phantom type is applied only as an OUTER `.asInstanceOf[Handler[Ctx, RequiredVars]]` on the fully-constructed `Handler` value.
  - Fix 2 (phase 2, `RouteBindingMacros.arrowImpl`): when calling `h.handle(request, context, reqRuntimeShapeValue, scope)`, don't write `reqRuntimeShapeValue.asInstanceOf[Req]` (a value-level cast to the phantom type - crashes for the same reason). Instead, cast `h` itself: `h.asInstanceOf[Handler[Ctx, reqRuntimeShapeType]].handle(request, context, reqRuntimeShapeValue, scope)`.
- `RequiredVars`' own phantom encoding for exactly ONE open param is deliberately BARE (`PathVar[N,T]`, not `Tuple1[PathVar[N,T]]` as zio-blocks' `OnePathVar`/`PathVarTuples` convention would use) - `PV`/`Req` parsing (`RouteBindingMacros.parseEntries`) already treats "not a `TupleN`, not `Unit`" as a single-entry arity-1 case, so this deviation from zio-blocks' own PathVars convention is safe and unobservable to callers - it only avoids the `Tuple1` bridge-cast crash described above. Zero/two-or-more-open-param arities are unaffected (`Unit`/real `TupleN` already match their own real runtime shape).

### D9 phase-1 Context-vs-PathVar classification policy (a real, unavoidable design decision, documented here for reconciliation with the Scala 3 sibling implementation)
- Per D9, phase 1 (`handler(fn)`) resolves Context/Request/Scope-typed params EAGERLY, WITHOUT any route pattern - but at that point there is no pattern to check names against, so phase 1 cannot literally know whether a given (name, type) pair WILL match a declared PathVar later. The policy adopted here: any parameter whose type is one of the five types `SegmentCodec` can ever capture (`Int`, `Long`, `String`, `Boolean`, `java.util.UUID`) is left OPEN as a `PathVar` candidate (deferred to phase 2); any OTHER (nominal/custom) type is resolved EAGERLY from `Context[Ctx]` (`Ctx` inferred as the intersection of all such contributing types, or `Any` if none). This is a closed-universe check (SegmentCodec truly supports only those five types), not an arbitrary heuristic, and it reproduces every Worked Example's documented behavior exactly (e.g. Worked Example 8: `customerId: UUID` stays open/PathVar, `basketId: BasketId` resolves via Context, since `BasketId` is not in the five-type set). **This exact policy should be cross-checked against whatever the Scala 3 (Todo 4) implementation does**, since D9's own wording is genuinely ambiguous on this point and no other interpretation was discoverable from the plan/draft alone. CONFIRMED IDENTICAL in Todo 4's Scala 3 findings below (same closed five-type-set policy, same reasoning, arrived at independently).

### Scala 2 macro mechanics notes
- `PathVarHandler.handler[H](fn: H): Handler[Nothing, Nothing] = macro ...` MUST declare `Handler[Nothing, Nothing]` (not `Handler[Any, Any]`) as its whitebox-refined bound: `Handler[-Ctx, -Vars]` is contravariant in BOTH parameters, so a computed `Handler[Ctx, ReqVars]` is a subtype of `Handler[Nothing, Nothing]` for any `Ctx`/`ReqVars` (trivially, since `Nothing <: X` always holds) but is NOT generally a subtype of `Handler[Any, Any]` (contravariance flips the check to `Any <: X`, generally false). Getting this backwards produces a real, if slightly confusing, "cascading" batch of type-mismatch/implicit-not-found errors elsewhere in the SAME file (see below).
- `Response | Halt` in this codebase is `zio.http.ResultType.|` (a plain `Either[A,B]` alias with two `implicit def` conversions, `responseAsResult`/`haltAsResult`) - NOT a Scala-3-style native union type. Macro-GENERATED code that needs a bare `Response`- or `Halt`-returning user function to widen to `Response | Halt` must explicitly `import _root_.zio.http.ResultType._` inside the generated tree - relying on the CALL SITE happening to have that import (as `Handler.scala`/`package.scala` do internally) is not a safe assumption for arbitrary external callers of a new macro entry point.
- A single early type error (e.g. the contravariance bug above) inside one `def handler[H](...)` call in a source file can produce a batch of unrelated-looking cascading errors LATER in the SAME file (including spurious `PathVarTuples.Combine` "not found" noise on completely unrelated lines) - always fix the FIRST reported error and recompile before trying to diagnose later ones in the same run.
- `RoutePattern.GET`/`RoutePattern.POST` (the pre-built `val`s) do NOT carry a refined `PathVars` type (declared as plain `RoutePattern[Unit]`, not `RoutePattern[Unit]{type PathVars=...}`) - building routes via `Method.GET`/`Method.POST` (the `zio.http.Method` case objects) through `RoutePattern.MethodSyntax./` gives the properly-refined type needed for the `->` derivation to see `PathVars` at all. This is a pre-existing zio-blocks characteristic (not a bug introduced here), just a real trap for anyone writing zio-http-side tests/examples.
- Scala 2.13's dependent-method-type inference for chained `/`/`++` calls is fragile without intermediate `val` bindings for each step (matches D10's guidance in the plan) - but this alone was NOT sufficient to fix the `PathVarTuples` `private[endpoint]` issue above; both fixes (intermediate vals AND the public-API workaround) were needed together for multi-segment patterns.

### Verification performed
- `mill 'core.jvm[2.13.18].test'`: 70/70 tests pass (245/245 mill tasks), on a `mill clean` fresh build, confirmed twice.
- All Worked Examples reproduced end-to-end (constructed the `Route`, invoked `route.handler.handle(...)` directly, asserted the exact `Response` body) - full-use, multi-var any-order, same-type-name-disambiguated collision, Context-fallback (Worked Example 8), Request/Scope combination (Worked Example 9), composed/prefixed patterns (Worked Example 10), pattern reuse with independent partial/full use (Worked Example 11), pre-built `Handler` `->` overload (no macro), zero-arg thunk shape, and the unchanged `Routes(...) @@ mw` idiom.
- The D4 unused-PathVar warning fires with the exact required wording, confirmed via real compiler output: `Variable postId:String was defined in the path but is never used` (Worked Example 11's partial-use handler).
- Negative case: a handler param whose name+type matches no declared `PathVar` (and isn't a Context/Request/Scope type either) fails to compile via `c.abort`, confirmed via `typeCheck(...)` returning `Left`.
- Full pre-existing suite (`HandlerV4Spec`, `RouteV4Spec`, `RoutesV4Spec`, `MiddlewareV4Spec`) passes unchanged - zero regressions.
- Scope: `jj diff --stat` shows exactly 5 new files, all under `zio-http-core/{shared,jvm}/src/{main,test}/scala-2/zio/http/` - no existing file touched (`ToHandler.scala`, `Route.scala`, `HandlerMacros.scala` (scala-2 content), `Middleware.scala`, `Routes.scala`, `package.scala`, `Handler.scala` all untouched).

### Update: test file rewritten to natural syntax after the upstream `PathVarTuples` fix landed
- The orchestrator fixed the `private[endpoint]` bug documented above: both `PathVarTuples` and `PathVarTuplesMacros` (in zio-blocks' `endpoint/shared/src/main/scala-2/zio/blocks/endpoint/PathVarTuples.scala`) were widened to fully PUBLIC - `private[blocks]` alone was proven NOT sufficient, since `zio.http` is a disjoint top-level package, not nested under `zio.blocks`. zio-blocks was re-`publishLocal`'d at the same `0.0.0-SNAPSHOT` coordinate; confirmed via `javap` that the republished jar's `PathVarTuples`/`PathVarTuplesMacros` classes are now `public`.
- Forcing fresh resolution in this workspace required no special cache-clearing: `0.0.0-SNAPSHOT` is resolved by mill directly from the local ivy2 repository (a local file path, not a remote/TTL-cached coursier download), so the updated jar was picked up immediately on the next `mill clean 'core.jvm[2.13.18]'` + compile - no stale-cache symptoms observed.
- Re-verified in isolation first (a throwaway scratch spec) that the natural `GET / int("userId") / string("postId")` syntax now compiles AND runs correctly with zero workaround, on a fresh clean build, before touching the real test file.
- `PathVarHandlerBindingSpec.scala` was then rewritten in full: removed the `concat2`/`multiSegment` helper functions and every call site that used them, replacing all of Worked Examples 2/3, 6/7, 10, 11 with plain natural `GET / int(...) / string(...)`-style chains (matching the syntax real users write, and Todo 4's Scala 3 test file's shape). Also reverted the bare-string literal segments (`"users"`, `"posts"`, `"logout"`) back from the earlier `literal(...)` workaround form - the "needs `literal(...)` / needs intermediate `val`s" symptoms observed earlier during Todo 5's original implementation turned out to be RED HERRINGS caused entirely by the `private[endpoint]` bug, not a separate dependent-type-inference limitation; natural, fully-inline bare-string chains compile fine now.
- Result: same test count (70 tests, no test added or dropped - only the CONSTRUCTION style of 4 tests changed), `mill 'core.jvm[2.13.18].test'` = 245/245 mill tasks / 70/70 tests green, confirmed on two independent fresh `mill clean` builds. The D4 unused-var warning (`Variable postId:String was defined in the path but is never used`) still fires with byte-identical wording. Squashed into the same Todo 5 jj commit (`wqqmppkmsxzn...`, same change-id, new commit-id `dfa830dd...`) rather than a separate follow-up commit, since it corrects the SAME commit's test file to its intended final form rather than introducing new unrelated work.
## Todo 4 findings (Scala 3 `handler()` macro + `RoutePattern.->` derivation)

### Environment / dependency-resolution gap (pre-existing, unrelated to my code)
- `core.jvm[3.8.3]` could NOT resolve 5 of its own direct `zio-blocks-next-*` deps at `0.0.0-SNAPSHOT`
  (`config`, `config-yaml`, `telemetry`, `html`, `http-model-schema`), plus a transitive
  `schema-yaml`, even on a completely fresh workspace with zero of my own code. Todo 3's
  `publishLocal` pass only covered `endpoint`'s OWN transitive closure (chunk, ringbuffer, typeid,
  maybe, context, scope, combinators, mediatype, markdown, streams, http-model, schema, endpoint)
  - it did not cover these OTHER zio-blocks modules that `zio-http-core` ALSO depends on directly
    (build.mill:86-95). Confirmed via `~/.ivy2/local/dev.zio/zio-blocks-next-<name>_3/0.0.0-SNAPSHOT/`
    being absent for exactly these 6 modules (all other zio-blocks-next-* modules for endpoint's
    closure were present).
- Fix: ran `sbt --client -Dsbt.color=false 'set ThisBuild / version := "0.0.0-SNAPSHOT"; ++3.8.3;
  configJVM/publishLocal; config-yamlJVM/publishLocal; telemetryJVM/publishLocal; htmlJVM/publishLocal;
  http-model-schemaJVM/publishLocal'` (then separately `schema-yamlJVM/publishLocal`) from the
  EXISTING zio-blocks Todo-3 workspace (`zio-blocks-pathvars-pathcodec`) - a pure build/packaging
  step, zero source/jj edits to zio-blocks. After this, `mill core.jvm[3.8.3].test` baseline (before
  any of my changes) was 245/245 tasks green, 59 tests passed - confirming the gap was purely a
  publishing omission, not a real incompatibility.
- The Scala 2.13 equivalents of all 6 modules were ALREADY published at `0.0.0-SNAPSHOT` - only the
  Scala 3 (`next-*`) side had the gap. Todo 5 (Scala 2.13 macro parity) should not hit this.

### The load-bearing macro-design problem: `transparent inline` composition breaks naive two-typed-overload dispatch
- The draft's mental model - `handler(fn): Handler[Ctx, RequiredVars]` (precisely typed) then `->`
  dispatching on THAT type via two overloads (`Handler[Ctx,Req]` macro-derived vs `Handler[Ctx,A]`
  pre-built) - does not work as literally described on Scala 3. `handler` is `transparent inline`;
  its PRECISE return type is only established by fully inlining it. But when `handler(fn)` appears
  as the ARGUMENT to `->`, Scala's overload/extension-method APPLICABILITY check runs BEFORE that
  inlining is forced (using `handler`'s plain declared type `Any` for the check) - so any `->`
  overload requiring `Handler[Ctx,Req]` never applies to a `handler(fn)` argument, and the call
  SILENTLY falls back to `scala.Predef.ArrowAssoc.->` (building a bare `(RoutePattern, Handler)`
  TUPLE instead of a `Route` - no compile error, wrong behavior at the type level). Reproduced and
  confirmed via `report.errorAndAbort` dumps of the inferred type at the call site.
- Fix implemented: `->` is a SINGLE macro (`transparent inline def ->(inline h: Any): Route[Nothing]`)
  over a fully unconstrained parameter, so it is ALWAYS the applicable extension (and, being an
  explicit-import extension method, outranks `Predef.ArrowAssoc` in scope priority regardless of
  `h`'s shape - no ambiguity). Dispatch between "macro-derived handler" and "pre-built handler"
  happens INSIDE the macro body by inspecting `h`'s RESULTING TYPE (`Handler[Ctx, Vars]`'s `Vars`
  argument): if `Vars` decomposes as an ordered `PathVar[N,T]*:...` tuple (only ever produced by
  `RouteBinding.handler`), take the "match against PV, rewire positionally" path; otherwise treat
  `h` as an already-resolved `Handler[Ctx, A]` and pass it straight to the existing `Route.apply`.
- IMPORTANT: originally tried to detect "is `h` syntactically a call to `RouteBinding.handler`" by
  walking `h`'s raw AST (`inline h` parameter). This does NOT work either, for the same underlying
  reason: since `handler` is ALSO `transparent inline`, by the time `->`'s macro body runs, `h`'s
  captured term is ALREADY the fully-expanded `Handler.extracted[Ctx,Vars](lambda)` call, not the
  original `handler(fn)` syntax - confirmed via `.show`/raw-AST dumps showing a
  `RouteBindingMacros.inline$extracted$i1[...]` synthetic call shape, not a `handler(...)` call at
  all. Type-based dispatch (on `h`'s resulting `Handler[Ctx,Vars]` type) is the only reliable
  signal; AST-shape detection of macro CALL SYNTAX is not reliable once nested `transparent inline`
  macros are involved.
- `Route[-Ctx]`/`Handler[-Ctx,-Vars]` are CONTRAVARIANT in Ctx/Vars: for a `transparent inline`
  method whose actual expansion type varies per call site, the widest DECLARABLE signature bound
  must be `Route[Nothing]` (not `Route[Any]`) - contravariant subtyping means `Route[ctxT] <:
  Route[Nothing]` (since `Nothing <: ctxT` always), the OPPOSITE of the covariant-intuition
  `Route[Any]`. Got this backwards on the first attempt; the symptom was a "Found Expr[Route[ctxT]],
  Required Expr[Route[Any]]" compile error inside the macro's own body.
- A SEPARATE, unrelated gotcha hit along the way: pushing an OUTER expected type (from a
  `transparent inline` method's declared, imprecise return type) DOWN into a `'{ ... }` quote block
  can constrain INNER macro-generated calls (e.g. `Handler.extracted[ctxT, A]`) to the WRONG,
  coarser type parameters. Fix: bind the quote to an explicit `val built: Expr[Route[ctxT]] = '{
  ... }` first (giving the block its OWN precise expected type), then return `built` - never let the
  method's own imprecise declared return type leak into quote elaboration.

### PathVars type reduction (macro-side gotcha, distinct from the above)
- `PV` (a route pattern's `PathVars` member) is frequently a deeply-nested, UN-REDUCED
  `PathVarTuples.Concat[L, R]` match-type alias chain (one layer per `/`/`~` step, often bottoming
  out in a path-dependent operand like `RoutePattern.GET.pathCodec.PathVars` when the LEFTMOST `/`
  starts from the `RoutePattern` object's own convenience `GET`/`POST` vals rather than
  `Method.GET`/`Method.POST`). A single `.dealias`/`.simplified` call does NOT fully reduce this -
  needed a `fullyReduce` fixpoint loop (`.dealias.simplified.dealias`, repeat until stable) before
  the `*:`/`EmptyTuple`/`PathVar[N,T]` structural pattern-match will ever see a concrete shape.
- Corollary gotcha for TEST CODE (not the macro): `Method.GET / int("id")` (starting from the raw
  `Method` value, going through `RoutePattern.MethodSyntax./`) gives a PROPERLY, immediately
  reduced `PathVars`. `RoutePattern.GET / int("id")` (starting from the pre-built convenience val,
  going through `RoutePatternOps./`) gives `PathVars` anchored on `RoutePattern.GET.pathCodec.PathVars`
  - an UNRESOLVABLE-without-full-value-widening path-dependent reference that even the fixpoint
  reducer above cannot see through (it needs VALUE-level, not just type-level, reduction, since
  `RoutePattern.GET`'s own DECLARED type is the unrefined `RoutePattern[Unit]`). Always build routes
  from `Method.GET`/`Method.POST` (not the `RoutePattern.GET`/`POST` vals) when the pattern needs a
  concrete `PathVars` a macro will decompose - importing `RoutePattern.{MethodSyntax,
  RoutePatternOps}` selectively (not `RoutePattern._`) avoids accidentally shadowing `Method.GET`
  with the ambiguous val.

### Two distinct "always a tuple" vs "unwraps for arity 1" invariants - do not conflate them
- zio-blocks' PHANTOM `PathVars` registry (and therefore this macro's own internal
  `RequiredVars`/`Vars` bucket built by `handler(fn)`) is ALWAYS a genuine tuple, even for exactly
  one entry (`PathVar[N,T] *: EmptyTuple`, never bare `PathVar[N,T]`) - confirmed in
  `PathVar.scala`'s own doc and Todo 1's acceptance criteria.
- zio-blocks' REAL runtime value type `A` (from `Tuples.Tuples.WithOut`) UNWRAPS for exactly one
  captured segment (`A = Int`, not `Tuple1[Int]`) but is a real `TupleN` for 2+.
- These are DIFFERENT types with DIFFERENT arity-1 conventions. A real bug (ClassCastException:
  `Integer cannot be cast to Tuple1` / `Tuple1 cannot be cast to Integer`, both directions) was
  caused by applying the SAME "if only one entry, access directly - else `productElement`" shortcut
  to BOTH types. The fix: `handler(fn)`'s OWN internal `vars` access is ALWAYS positional
  (`productElement`), no arity-1 shortcut, because its `Vars` bucket is always-a-tuple; only the
  `->`-side access into the PATTERN's real value `A` gets the arity-1 direct-access shortcut.

### Design decision: how `handler(fn)` decides PathVar-vs-Context without seeing the pattern (D7)
- zio-blocks' `SegmentCodec` can only ever capture a path variable as one of exactly five primitive
  types (`Int`, `Long`, `String`, `Boolean`, `java.util.UUID` - see `SegmentCodec.int/long/string/
  bool/uuid`). This closed set is what makes phase-1 classification sound WITHOUT the pattern: a
  parameter typed as one of those five is ALWAYS a path-var candidate (deferred); anything else is
  ALWAYS resolved eagerly from `Context` (never could have been a path var, since `PathVar`'s own
  `Type` parameter is restricted to that same five-type set by construction). This sidesteps the
  deeper concern (worked through but NOT needed in the end) that Ctx-widening via ordinary
  `ContextHas`-style implicit search on an unconstrained type parameter is unsound (an unconstrained
  contravariant type parameter defaults to `Nothing`, under which `ContextHas[Nothing, T]` trivially
  "succeeds" for ANY `T` with no real guarantee) - `Ctx` is instead computed DIRECTLY, inside
  `handler(fn)`'s own macro expansion, as the exact `AndType` intersection of every eagerly-resolved
  Context-tier parameter's type (or `Any` if none).
- One direct, load-bearing consequence: since ALL Context-tier resolution happens in `handler(fn)`
  BEFORE the pattern is known, a path-var-eligible-typed handler parameter that does not match the
  pattern by name has NO further Context fallback (there is no such thing as a "Context-provided
  Int/String/etc." in this design) - it is a hard compile error at the `->` call site. This is
  exactly the plan's required negative-test shape and needed no extra scaffolding to produce.

### Verification
- `mill core.jvm[3.8.3].test`: 245/245 tasks SUCCESS, 71 tests passed (0 failed) - the pre-existing
  59 (HandlerV4Spec/MiddlewareV4Spec/RouteV4Spec/RoutesV4Spec, UNCHANGED, zero diffs) plus 12 new in
  `PathVarHandlerBindingSpec.scala` covering every Worked Example (1, 2/3, 4, 5, 6/7, 8, 9, 10, 11,
  pre-built-`Handler` overload, `Routes(...) @@ mw`, and the negative `typeCheck`-based case).
  Verified fresh from `mill clean` (not just an incremental re-run).
- Unused-PathVar warnings fire correctly and with the exact required text ("Variable
  postId:String was defined in the path but is never used") for the partial-use worked examples (4
  and 11's partial handler). NOTE for Todo 6: the zero-use worked example (5) should warn on BOTH
  declared PathVars, but only ONE of the two `report.warning` calls (same macro expansion, same
  default call-site position, different message text) was visible in mill's compiler output - the
  macro genuinely calls `report.warning` once per unconsumed entry (verified by reading the code
  path executing unconditionally per entry), so this looks like a reporter/mill display dedup at
  the SAME source position rather than a logic bug, but Todo 6 should verify this with an explicit
  `-Xfatal-warnings`-style count assertion (as its own acceptance criteria already requires) since
  I could not fully rule out a subtler issue without that harness.
- No `scala.reflect.Selectable`/Java reflection anywhere in `RouteBinding.scala` (grepped, zero
  matches). No `Middleware.scala`/`Routes.scala`/`Route.scala`/`ToHandler.scala` diffs - `jj diff
  --stat` against the branch tip shows exactly 2 new files (`RouteBinding.scala`,
  `PathVarHandlerBindingSpec.scala`), nothing else touched.
- No mill scalafmt task or standalone `scalafmt` binary available in this environment for zio-http
  (checked `mill resolve mill.scalalib.scalafmt/*`, `which scalafmt` - neither present, and
  build.mill has no `ScalafmtModule` wiring despite a `.scalafmt.conf` existing). Formatting step
  skipped for this reason; code was hand-formatted to match the existing file style (2-space indent,
  same import/doc conventions as `Handler.scala`/`Route.scala`).
- Commits in this jj-based zio-http repo are NOT GPG-signed (`git log --format='%G?'` = `N`) even
  for the pre-existing branch-tip commit before any of my changes - this appears to be the
  established convention/config state of this particular sandbox (jj has no `signing.behavior`
  configured), not something introduced or fixable within this todo's scope.

## Todo 6 findings (unused-PathVar warning: fatal-warnings proof, multi-var separateness, Context exemption)

- **Divergent-branch merge performed first**: Todo 4 (`ttzrslxpuxlk`/`18320fb966d6`, Scala 3) and
  Todo 5 (`wqqmppkmsxzn`/`e03d7ecdd645`, Scala 2.13) were BOTH parented directly on
  `uwzzukwvkxxp`/`66b5e6f543f0` (the pre-plan branch tip) - two sibling branches, NOT a linear
  history, confirmed via `jj log`. Fixed via `jj rebase -s ttzrslxpuxlk -d wqqmppkmsxzn` (rebase
  Todo 4's branch, as a unit with its descendants, onto Todo 5's tip) run from the
  `zio-http-handler-macro-s2` workspace. File sets do not overlap (Todo 4 touches only
  `scala-3`/shared-test paths, Todo 5 only `scala-2` paths) so the rebase was safe for all SOURCE
  files; the only conflict was BOTH branches independently adding
  `.omo/notepads/route-pattern-typed-vars/learnings.md` as a new file with different content (a
  real two-sided "both sides add" conflict, not a code conflict) - resolved by hand-merging both
  texts into one file (this file) via `jj new` + edit + `jj squash`.
- A NEW workspace (`zio-http-handler-macro-s2-todo6`) was created off the merged tip so both
  Scala 3's `RouteBinding.scala` (scala-3 sources) and Scala 2.13's `RouteBinding*.scala`/
  `PathVarHandler*.scala` (scala-2 sources) are simultaneously present and buildable in one place.
