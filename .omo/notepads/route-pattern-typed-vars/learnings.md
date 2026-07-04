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
- A NEW workspace (`zio-http-todo6-warning-proof`) was created off the merged tip so both
  Scala 3's `RouteBinding.scala` (scala-3 sources) and Scala 2.13's `RouteBinding*.scala`/
  `PathVarHandler*.scala` (scala-2 sources) are simultaneously present and buildable in one place.
- **Real, merge-exposed bug (fixed as part of this todo)**: Todo 4's Scala 3 test file
  (`PathVarHandlerBindingSpec.scala`) was placed at `zio-http-core/jvm/src/test/scala/...` (the
  version-AGNOSTIC "scala" dir, compiled into BOTH Scala 2.13 and Scala 3 builds) instead of
  `.../test/scala-3/...` (mirroring Todo 5's correct `.../test/scala-2/...`). Invisible while Todo
  4/5 lived on separate branches; the moment both are present in one workspace,
  `mill 'core.jvm[2.13.18].test'` fails immediately with `PathVarHandlerBindingSpec is already
  defined as object PathVarHandlerBindingSpec` (duplicate object name, same package). Fixed via a
  `jj`-tracked file rename to `.../test/scala-3/...`. Confirmed before/after: Scala 3 baseline
  71/71, Scala 2.13 baseline 70/70, both unaffected by the move.
- **Real, PROVEN Scala-3-ONLY bug in the warning mechanism, found via a genuine `-Werror` (dotc's
  spelling of `-Xfatal-warnings`) out-of-process compile, fixed minimally in `RouteBinding.scala`
  (scala-3)**: `RouteBindingMacros.arrowImpl` called `report.warning(msg)` (no explicit position)
  once per unconsumed PathVar in a loop - every call shared the exact SAME default
  "macro expansion" position. Dotty's default reporter mixes in `UniqueMessagePositions`
  (`compiler/src/dotty/tools/dotc/reporting/UniqueMessagePositions.scala`), which SILENTLY DROPS
  any diagnostic whose `(SourceFile, pointOffset)` key was already seen - so only the FIRST of N
  unused-var warnings ever surfaced, regardless of differing message text. Confirmed empirically: a
  3-segment pattern consuming only 1 var showed only 1 of the 2 expected warnings in real
  `mill test` output (matches Todo 4's own recorded uncertainty). **Scala-3-SPECIFIC**: the parallel
  Scala 2.13 `c.warning(c.enclosingPosition, msg)` does NOT dedupe (scalac has no equivalent
  position-based warning suppression) - proven by an identical scratch snippet under real
  `scalac -Xfatal-warnings`, which showed both warnings distinctly, zero fix needed there. **Fix**
  (Scala 3 only): construct a distinct zero-width `Position(macroPos.sourceFile, offset, offset)`
  per unconsumed entry, `offset = macroPos.start + idx` (clamped to `macroPos.end`), instead of a
  position-less `report.warning(msg)` call. Re-verified via the same raw `-Werror` compile: all N
  warnings now surface distinctly, zero regression to the 71-test Scala 3 baseline.
- **Raw `-Werror`/`-Xfatal-warnings` build-level proof, embedded as real `mill test`-participating
  ZIO tests (not just manual evidence)**: a `FatalWarningsProof` helper object per spec file shells
  out (`scala.sys.process`) to a genuine separate `java -cp <classpath> dotty.tools.dotc.Main` /
  `scala.tools.nsc.Main` process. Classpath assembled from `sys.props("java.class.path")` (verified
  via a real diagnostic print to be the FULL ~7400-char mill-resolved test classpath inside the
  forked test JVM, NOT a manifest-jar/args-file wrapper - `testUseArgsFile.json` was `false` for
  this module) plus the Scala compiler's own jars, located by finding `scala-library`'s own jar on
  that classpath and deriving the coursier cache root 4 parent-dirs up from
  `.../org/scala-lang/scala-library/<version>/scala-library-<version>.jar`, then globbing sibling
  artifacts under that root. Non-obvious extra jars needed: Scala 3 needs `scala3-interfaces`,
  `tasty-core_3`, `scala-asm` (under `org/scala-lang/modules`), AND `org.scala-sbt:compiler-interface`
  (dotc's `CompilationUnit` statically references `xsbti.UseScope` even in plain standalone-driver
  mode - confirmed via a real `NoClassDefFoundError: xsbti/UseScope` without it). Scala 3 additionally
  needs `-usejavacp -experimental -Werror` (mirrors this module's own `scalacOptions`, since
  `RouteBinding`/`RouteBindingMacros` are `@experimental`); Scala 2.13 needs `-usejavacp
  -Xfatal-warnings`. dotc 3.8.3 reports `-Xfatal-warnings` as "a deprecated alias: use -Werror
  instead" but it still functions.
- **Final proof results** (both platforms, real compiler output, real exit codes): (1) a 3-segment
  pattern consuming only 1 var FAILS under fatal-warnings with exactly 2 distinct warning messages
  naming the other 2 vars; (2) the SAME pattern with full use compiles with exit code 0 and zero
  "was defined in the path" text; (3) a handler with an extra Context-resolved parameter whose
  VALUE is never referenced in the body (declared, since that's required for Context resolution to
  trigger - D7 tier 2) compiles with exit code 0 under fatal-warnings and zero "was defined in the
  path" warnings - the Context exemption is a real compiler-diagnostic-level guarantee.
- Added tests 12-16 to BOTH `PathVarHandlerBindingSpec.scala` files (12: multi-unused-var runtime
  correctness; 13: Context-declared-but-unused runtime correctness; 14/15/16: the three
  `-Werror`/`-Xfatal-warnings` build-level proofs above).
- Final verification (fresh `mill clean` + rebuild, both platforms): `mill 'core.jvm[3.8.3].test'`
  = 245/245 mill tasks, 76/76 tests (71 baseline + 5 new), 0 failures.
  `mill 'core.jvm[2.13.18].test'` = 245/245 mill tasks, 75/75 tests (70 baseline + 5 new), 0
  failures. Zero regressions; the full pre-existing suite
  (`HandlerV4Spec`/`RouteV4Spec`/`RoutesV4Spec`/`MiddlewareV4Spec`) passes unchanged on both.
- Scope: `jj diff --stat` for this todo shows exactly 4 changed files - the Todo 4 test-file rename
  (`.../test/scala/...` -> `.../test/scala-3/...`, with its 5 new tests + `FatalWarningsProof`
  helper added), the Scala 2.13 spec's 5 new tests + `FatalWarningsProof` helper, and the ONE
  minimal `RouteBinding.scala` (scala-3) fix (17 lines) for the position-dedup bug.
  `RouteBindingMacros.scala`/`PathVarHandler.scala`/`PathVarHandlerMacros.scala` (scala-2)

## Todo 7 findings (zio-http, `RoutePattern.->` overload-resolution safety + D12 middleware-boundary verification) - a genuine operator-precedence finding contradicting the plan's own stated expectation, plus a secondary import-collision usability gap, both REPORTED not fixed (no real bug in `RouteBinding.scala`/`RouteBindingMacros.scala`/`PathVarHandler*.scala` found; zero production files touched)

- **Workspace**: new dedicated jj workspace `zio-http-todo7-overload-safety` created off Todo 6's merged tip `oqnsomrxxxor`/`bb87480b89fc` (confirmed via `jj log` that the lineage includes both `ttzrslxpuxlk` (Todo 4, Scala 3) and `wqqmppkmsxzn` (Todo 5, Scala 2.13) as ancestors, plus Todo 6's own fixes, before starting work).
- **(a) overload-resolution safety, both Scala versions - CONFIRMED SAFE, but the two versions are NOT the same mechanism**: Scala 3's `->` (`RouteBinding.scala`) is a SINGLE `transparent inline` macro over an unconstrained `inline h: Any` parameter - there is only ONE declared extension method, so literal "ambiguous overload" errors are structurally impossible; dispatch between the macro-derived-`handler(fn)` path and the pre-built-`Handler[Ctx,V]` path happens INSIDE the macro body via `tryDecomposePathVarTuple` on `h`'s resulting type. Scala 2.13's `->` genuinely IS two declared overloads (`->[Ctx,Req](h: Handler[Ctx,Req])` macro-derived vs `->[Ctx](h: Handler[Ctx,A])` pre-built) on `RoutePatternArrowOps` - real Scala overload resolution (not macro-internal dispatch) picks between them. Both mechanisms were proven safe with REAL compiling+running tests (new `RouteArrowOverloadSafetySpec.scala`, both platforms, 8 tests each): both shapes compile and run correctly standalone, combine correctly in one `Routes(...)` with no cross-interference, and a pre-built `Handler` over a REAL (non-phantom) multi-segment value tuple is never misidentified as a macro-derived handler (this was the only theoretically-risky edge case, since both mechanisms distinguish the two shapes by inspecting `Vars`'/`Req`'s type shape - real value tuples can never accidentally look like an ordered `PathVar[...]` tuple, since `PathVar` is a sealed, never-instantiated phantom marker, so this is safe by construction, now also empirically confirmed).
- **(c) D12 boundary - Middleware.scala/Routes.scala ZERO diff, confirmed via `jj diff --stat --from uwzzukwvkxxp --to bb87480b89fc` (pre-plan tip to Todo 6 tip) AND again to this todo's own tip - both show `0 files changed, 0 insertions(+), 0 deletions(-)`** for exactly those two files across the ENTIRE plan (Todos 4-7 combined). `build.mill` is ALSO zero-diff across the whole plan.
- **REAL FINDING #1 (operator precedence): the plan's own stated expectation for the D12 negative case ("`@@` is not a member of Route") is WRONG for the literal bare/un-parenthesized form the plan specifies** - proven via REAL compilation (`core.jvm[X].test.compile` on an actual scratch `.scala` file, not just `typeCheck`, on BOTH Scala versions), not assumed. Scala's infix-operator precedence is determined by the operator's FIRST CHARACTER: `@` (as in `@@`) falls in the highest-precedence "all other special characters" bucket, while `-` (as in `->`) falls in the lower "+ -" bucket - so `pattern -> handler(fn) @@ mw` parses as `pattern -> (handler(fn) @@ mw)`, NOT `(pattern -> handler(fn)) @@ mw` as the plan's wording assumes. The REAL captured error (both Scala 3 and 2.13, byte-for-byte confirmed via actual `mill core.jvm[X].test.compile` runs, not typeCheck): `value @@ is not a member of zio.http.Handler[Any, zio.blocks.endpoint.PathVar[("id" : String), Int] *: EmptyTuple.type]` (Scala 3) / `value @@ is not a member of zio.http.Handler[Any,zio.blocks.endpoint.PathVar[String("id"),Int]]` (Scala 2.13) - it names `Handler`, not `Route`. The D12 safety net STILL HOLDS COMPLETELY (the compile still fails immediately and cleanly, with zero silent misparse or wrong-behavior compile - Scala never falls back to `Predef.ArrowAssoc` or any other silent interpretation), just with a different message than the plan's draft wording predicted. The EXPLICITLY PARENTHESIZED form `(pattern -> handler(fn)) @@ mw` DOES produce exactly the plan-predicted message: `value @@ is not a member of zio.http.Route[Any]` (confirmed identical on both Scala versions via real compilation). Both forms (bare and parenthesized) are now permanently locked in as regression tests in `RouteArrowOverloadSafetySpec.scala` (both platforms) via `typeCheck`, with the EXACT captured error text asserted via `Assertion.equalTo`/`Assertion.containsString`.
- **`typeCheck`-harness gotcha discovered while building the above** (relevant for anyone extending this test suite): assigning a `transparent inline`-derived expression to a top-level `val` INSIDE a `zio.test.typeCheck` string snippet (e.g. `val route = pattern -> handler(fn) @@ mw`) produces a spurious, UNRELATED "Recursive value route needs type" error - a `typeCheck`-macro-specific snippet-wrapping artifact, NOT a real user-facing issue (the identical code as a real, non-`typeCheck`, on-disk `.scala` file compiles/fails exactly as expected, no such error). Fix: write the risky expression as a bare STATEMENT (no `val` binding) inside the `typeCheck` string - this avoids the artifact entirely and surfaces the real, correct compiler diagnostic. Verified empirically (side-by-side, `val`-bound vs bare-statement, identical otherwise) before finalizing the committed tests.
- **REAL FINDING #2 (Scala-3-only import collision, reported not fixed - genuine external-consumer usability gap)**: Scala 3's `RouteBinding.handler` REUSES the exact same simple name (`handler`) as the PRE-EXISTING `zio.http.handler` (the old `ToHandler`-based package-object function, `HandlerMacros.scala`/`package.scala`, untouched by this plan). Scala 2.13 avoided this by naming its phase-1 macro `PathVarHandler.handler` (a DIFFERENT object) - no collision there. Empirically confirmed (via `typeCheck` snippets that are NOT already inside `package zio.http`, i.e. simulating a REAL external consumer's own application package): combining `import zio.http._` (near-mandatory for `Response`/`Method`/etc.) with `import zio.http.RouteBinding._` (mandatory for the new `->`/`handler`) makes `handler` an ambiguous reference on Scala 3, REGARDLESS of import order - dotc reports "Reference to handler is ambiguous" as a secondary note; in some cases (e.g. a genuinely-wrong `wrongName` PathVar mismatch) it still resolves the PRIMARY diagnostic correctly (`RouteBinding.handler` wins for elaboration purposes), but a plain, fully VALID `val route = pattern -> handler(fn)` (no `val`-avoidance possible in real code, unlike the test-harness workaround above) fails outright with "Recursive value route needs type" under this exact combination. This does NOT affect any file INSIDE package `zio.http` itself (explicit imports there shadow the package's own auto-visible `handler`, which is why the real `PathVarHandlerBindingSpec.scala`/`RouteArrowOverloadSafetySpec.scala` files - both literally `package zio.http` - never hit this), and does NOT affect any existing test in this plan's own suites (confirmed: 245/245 mill tasks / 84 tests on Scala 3, 245/245 / 83 tests on Scala 2.13, zero regressions). It WOULD affect a real external application (in package `myapp`, say) that writes the natural `import zio.http._` + `import zio.http.RouteBinding._` combo. NOT fixed in this todo (out of scope per the todo's own "should not need to modify RouteBinding.scala unless a real problem is found - if so, fix it minimally" - this is real but does not block any of Todo 7's own acceptance criteria, none of which requires an external-package consumer scenario; a rename of `RouteBinding.handler` to a distinct name, e.g. mirroring Scala 2.13's `PathVarHandler.handler`, is the likely minimal fix for a future todo, since `RouteBinding.scala`/`RouteBindingMacros.scala` are explicitly the ONLY files this plan permits touching for exactly this kind of issue - reported prominently per this todo's instructions, deliberately NOT fixed here to keep this todo's diff at zero production-file changes).
- **(b) exhaustive `Route(`/`Route.apply(`/`->`-on-`RoutePattern` call-site grep, across the WHOLE repo (not guessed - `grep -rn` across every module directory)**:
  - `zio-http-core` (main + test, both the module this plan's acceptance criteria mandates): `Route.scala` (the `Route.apply` definition + its own `toString`), `RouteBinding.scala` (scala-3 and scala-2, the new `->`/`handler` call sites this plan adds), `RouteV4Spec.scala` (13 sites), `RoutesV4Spec.scala` (1 site), `MiddlewareV4Spec.scala` (3 sites) - ALL confirmed compiling AND passing via a fresh `mill clean` + `mill 'core.jvm[3.8.3].test'` (245/245 tasks, 84/84 tests) and `mill 'core.jvm[2.13.18].test'` (245/245 tasks, 83/83 tests), zero failures, zero regressions vs. Todo 6's 76/75 baselines (the +8/+8 delta is exactly this todo's own new `RouteArrowOverloadSafetySpec.scala` tests).
  - `zio-http-example`: **is NOT wired into `build.mill` at all** (confirmed via `mill resolve "__"` showing zero modules matching "example", and `build.mill` itself having ZERO diff across the whole plan) - the 45 `Route(...)`-shaped call sites found there (`Method.GET / "hello" -> handler(...)`, etc., across 25 files) belong to a LEGACY/orphaned module that mill's build graph does not currently build at all, in either direction (before or after this plan). Out of scope, pre-existing, unrelated - this plan changed nothing that could affect a module mill never compiles.
  - `zio-http-testkit`: wired into `build.mill`, but transitively depends on `endpoint.jvm()` (the LEGACY `zio-http-endpoint` module, e.g. `AuthType.scala`, `Endpoint.scala` etc. - distinct from zio-blocks' `endpoint` module) which currently FAILS with 100 pre-existing compile errors (`object codec is not a member of package zio.http`, `not found: type HttpCodec`, etc.) - confirmed via a real `mill "testkit.jvm[2.13.18].compile"` run. Root cause: the plan's own documented Risk section (several DELETED files under `zio-http-endpoint/...`, pre-existing dirty-worktree state). Confirmed unrelated to this plan: `zio-http-endpoint/` and `zio-http-testkit/` both show ZERO diff (`jj diff --stat`) from the pre-plan tip (`uwzzukwvkxxp`) to this todo's tip - the identical 100 errors exist byte-for-byte before Todo 4 ever started. `TestServer.scala`/`TestClient.scala` (inside testkit) DO use a pre-existing `RoutePattern(...) -> handler {...}` call site, but it is unreachable/moot since the module housing it cannot currently compile for unrelated reasons - not this plan's concern.
  - `zio-http-server-loom`: wired into `build.mill`, but FAILS to even RESOLVE its own declared dependency (`dev.zio:zio-blocks-mux_2.13:0.0.0-SNAPSHOT` - not found in the local ivy2 cache or Maven Central) - confirmed via a real `mill "serverLoom.jvm[2.13.18].compile"` run. This is an incomplete-`publishLocal` environment gap (the `mux` zio-blocks module was never part of Todo 1-3's documented `publishLocal` chain), NOT a source-code issue - confirmed unrelated to this plan via `jj diff --stat` showing ZERO changes to `zio-http-server-loom/` across the whole plan. The 21 `Route(`-shaped call sites in `H2IntegrationSpec.scala`/`H2CSmokeTest.scala` are therefore also currently unreachable via mill, identically before and after this plan.
- Final verification (fresh `mill clean` + rebuild, both platforms): `mill 'core.jvm[3.8.3].test'` = 245/245 mill tasks, 84/84 tests (76 baseline + 8 new), 0 failures. `mill 'core.jvm[2.13.18].test'` = 245/245 mill tasks, 83/83 tests (75 baseline + 8 new), 0 failures.
- Scope: `jj diff --stat` for this todo shows exactly 2 new files (`RouteArrowOverloadSafetySpec.scala`, both `scala-2` and `scala-3` test dirs) - zero production files touched (`RouteBinding.scala`/`RouteBindingMacros.scala`/`PathVarHandler.scala`/`PathVarHandlerMacros.scala`/`Route.scala`/`Routes.scala`/`Middleware.scala`/`ToHandler.scala`/`HandlerMacros.scala` all untouched), since investigation found no bug requiring a production-code fix (only the two REPORTED findings above, neither of which blocks this todo's own acceptance criteria).
- Commit: `90403dad5e15` (jj change `vtruxwonstwt`), message `test(http): verify RoutePattern.-> overload-resolution safety and D12 middleware boundary`, parented directly on Todo 6's `oqnsomrxxxor`/`bb87480b89fc`.

## Todo 8 findings (D8 zero-allocation verification) - REAL nonzero allocation delta found via `javap`+JMH, root-caused, and fixed minimally in BOTH `RouteBinding.scala` (scala-3) and `RouteBindingMacros.scala` (scala-2)

- **Workspace**: new dedicated jj workspace `zio-http-todo8-alloc-verify` created off Todo 7's tip (`tzmzyprqwrtl`/`b46cb575b2ed`), confirmed via `jj log -r 'ancestors(tzmzyprqwrtl, 15)'` that the lineage includes Todo 4 (`ttzrslxpuxlk`), Todo 5 (`wqqmppkmsxzn`), Todo 6 (`oqnsomrxxxor`), Todo 7 (`vtruxwonstwt`) before starting.
- **No `zio-http-benchmarks` mill module existed** - there IS a pre-existing `zio-http-benchmarks/src/main/scala/{benchmark,zhttp.benchmarks,zio/http}/*.scala` directory (legacy pre-v4 zhttp/sbt-jmh sources, already tracked in git history, NOT touched by Todos 1-7, NOT wired into `build.mill` before this todo, and does NOT compile against the current API - same class of dormant/legacy code as `zio-http-example`/`zio-http-testkit` per Todo 7's own findings). Added a NEW `benchmarks` module to `build.mill` using `mill-contrib-jmh`'s `JmhModule` (`//| mvnDeps: ["com.lihaoyi::mill-contrib-jmh:$MILL_VERSION"]` header pragma + `trait JvmModule extends ZioHttpModule with JmhModule`, needed an explicit `override def mvnDeps = super[ZioHttpModule].mvnDeps() ++ super[JmhModule].mvnDeps()` to resolve a diamond-inheritance conflict on `mvnDeps`), cross-built for both Scala versions, with `sources` scoped ONLY to NEW `scala-2`/`scala-3` version-specific dirs (deliberately excluding the legacy version-agnostic `src/main/scala` dir, to avoid pulling in the broken pre-existing sources). `mill 'benchmarks.jvm[X].runJmh' '<regex>' -prof gc -f N -wi N -i N -w Nms -r Nms` is the correct invocation (a bare `-prof gc` with no regex is parsed as an EMPTY regex and reports "No matching benchmarks" - a JMH-runner quirk, not a mill one; `mill 'benchmarks.jvm[X].listJmhBenchmarks'` lists detected `@Benchmark` methods and is a fast pre-flight sanity check before a full `runJmh`).
- **Hit Todo 7's own documented Scala-3 `handler` import-collision finding FIRST-HAND**: `import zio.http._` combined with `import zio.http.RouteBinding._` in the new benchmark file (package `zio.http.benchmarks`, a sub-package, not `zio.http` itself) reproduced the exact "Reference to handler is ambiguous" error Todo 7 reported but did not fix. Worked around it in the benchmark files (not `RouteBinding.scala` itself - a fix there was correctly deferred as Todo 7 found, out of this todo's scope) by importing only the specific `zio.http` names needed (no wildcard).
- Scala 2.13's `Response | Halt` (`ResultType`'s custom `Either`-based encoding, not a native union type) needed an explicit `import zio.http.ResultType._` in the hand-written baseline's `Handler.extracted` lambda body, exactly matching Todo 5's own documented gotcha.
- **REAL, CONFIRMED, ROOT-CAUSED, FIXED allocation regression** (the actual finding this todo exists to catch - not hypothetical): for Worked Example 2's identity-order full-use shape (`GET / int("userId") / string("postId") -> handler((userId: Int, postId: String) => ...)` - handler consumes EVERY pattern var, in the pattern's OWN declared order), BOTH macros rebuilt a brand-new reshaping tuple from the pattern's already-correctly-shaped real value tuple `a`, then immediately re-decomposed it inside the phase-1 handler - pure waste, since `a` was already the exact `Product` shape needed. Confirmed via real `javap -c`:
  - Scala 3 (BEFORE fix): the macro-generated call site (`$init$$$anonfun$1`) did TWO `scala/runtime/Tuples$.cons` invocations (`productElement(0)`/`productElement(1)` extraction from `a`, then re-consing into a NEW `PathVar`-tagged tuple) before calling `Handler.handle` - the hand-written baseline's equivalent lambda did zero extra allocation (direct `_1`/`_2` field access only).
  - Scala 2.13 (BEFORE fix): the macro-generated call site (`$anonfun$macroRoute$1`) did a `new scala/Tuple2` (dup, extract `_1$mcI$sp`/`_2` off `a`, box, `invokespecial Tuple2.<init>`) - the hand-written baseline's equivalent had zero `new Tuple2`.
  - JMH `-prof gc` (BEFORE fix, `-f 1 -wi 3 -i 3`): Scala 3 `handWritten` = 608.048 B/op vs `macroGenerated` = 664.029 B/op (a real, reproducible +56 B/op delta matching the 2 extra cons allocations) vs `naiveMapAllocating` (deliberate self-test) = 768.025 B/op.
  - **Fix** (Scala 3, `RouteBinding.scala::arrowImpl`): detect `isIdentity = n >= 2 && positions == List.range(0, n)` (handler's requested positions are the exact 0..n-1 identity permutation over ALL pattern vars); when true, skip `buildTuple` entirely and use `a.asInstanceOf[varsT]` directly (a zero-cost reinterpret-cast, since `a`'s runtime `Product` shape is bit-for-bit identical to what the rebuilt tuple would have contained - `PathVar[Name,Type]` never exists as a runtime value, so the phantom-tagged tuple and the real value tuple are RUNTIME-IDENTICAL for the full-identity case). The `n == 1` case is UNCHANGED (still must wrap, since a single-capture pattern's real value `a` is bare/unwrapped, not a `Product`, while phase-1's `Vars` is ALWAYS a genuine 1-tuple by Todo 4's own documented invariant). Any non-identity case (partial use and/or reordered use) is UNCHANGED (a real reshape is unavoidable there - this is inherent, not a bug).
  - **Fix** (Scala 2.13, `RouteBindingMacros.scala::arrowImpl`): identical shape of fix - track `positions` during the `accessExprs` build, compute `isIdentity = pvArity >= 2 && positions.toList == List.range(0, pvArity)`, and when true use `$aVarsName.asInstanceOf[$aType]` directly instead of `q"(..$accessExprs)"` (which compiles to a real `new TupleN(...)`).
  - **Post-fix `javap` re-confirmation**: the macro-generated call site on BOTH platforms now does ZERO extra allocation opcodes - Scala 3's `$init$$$anonfun$1` collapsed to `aload_3; invokeinterface Handler.handle` (reusing `a` directly, no `productElement`/`cons` calls at all); Scala 2.13's `$anonfun$macroRoute$1` collapsed to `aload_2; invokeinterface Handler.handle` (no `new Tuple2`). Re-running the FULL diff of `javap -c` output before/after the fix on a `mill clean` rebuild is byte-for-byte reproducible (confirmed twice).
  - **Post-fix JMH re-confirmation (definitive, `-f 6 -wi 5 -i 5`, 30 measurements per benchmark)**: Scala 2.13 `handWritten` = 672.004 ± 0.001 B/op vs `macroGenerated` = 672.003 ± 0.001 B/op (a true ZERO delta - the 0.001 B/op gap is sub-byte floating-point noise, not a real difference); Scala 3 `handWritten` = 616.006 ± 12.155 B/op vs `macroGenerated` = 608.003 ± 7.688 B/op (overlapping error bars, DIRECTION FLIPS between repeated runs across this investigation - confirmed pure JIT/measurement noise, not a systematic delta, since a genuine regression would show a CONSISTENT sign, and this one did not). An intermediate run caught ONE outlier JMH fork (`handWritten` fork 4/4 read 696.001 B/op vs the other 3 forks' 672.001 B/op, on otherwise-identical code) - a real, observed example of single-fork JIT-inlining noise that a naive single-fork/low-iteration JMH run could easily misattribute to a macro-generated regression; multi-fork averaging (`-f >= 4`) is necessary to avoid this trap.
- **Harness self-test (D8's required "confirm the harness has teeth" check, both platforms)**: a THIRD deliberately-naive `naiveMapAllocating` benchmark (extracts `vars`, builds a `scala.collection.immutable.Map("userId" -> ..., "postId" -> ...)`, then reads back through the map) was added to the SAME harness. Confirmed via real `javap`: the `Map`-allocating opcodes (`Map$.apply`, `wrapRefArray`, `anewarray Tuple2`) appear ONLY in this method, nowhere in the macro-generated or hand-written call sites. Confirmed via real JMH `-prof gc`: `naiveMapAllocating` = 901.337 ± 17.939 B/op (Scala 2.13) / 796.009 ± 19.031 B/op (Scala 3) - clearly and CONSISTENTLY (across every run in this investigation, both platforms) far above both `macroGenerated` and `handWritten`'s ~608-672 B/op, by a margin (~130-230 B/op) an order of magnitude larger than the noise bands observed on the real comparison. This proves the harness correctly flags a genuine allocation regression when one exists, and correctly does NOT flag the real (fixed) Todo 4/5 implementation.
- **Zero `scala.reflect.Selectable`/Java-reflection/`MethodHandle` invocation opcodes** confirmed via `javap` on the macro-generated call site on both platforms (the only `invokedynamic` present is ordinary `LambdaMetafactory`-based Scala closure creation - identical mechanism used by the hand-written baseline's own lambda, not a per-call reflective dispatch, and not the kind of runtime reflection the plan's Scope guardrails prohibit).
- Regression suite (both platforms, fresh `mill clean` + rebuild, run twice for determinism): `mill 'core.jvm[3.8.3].test'` = 245/245 mill tasks, 84/84 tests, 0 failures (unchanged from Todo 7's baseline - this todo added zero new tests to `core.jvm`, all new verification lives in the separate `benchmarks` module). `mill 'core.jvm[2.13.18].test'` = 245/245 mill tasks, 83/83 tests, 0 failures (unchanged from Todo 7's baseline).
- Scope: `jj diff --stat` shows exactly 5 changed files - `build.mill` (new `benchmarks` module, +22 lines), 2 NEW benchmark files (`zio-http-benchmarks/src/main/scala-{2,3}/zio/http/benchmarks/HandlerBindingBenchmark.scala`), and the 2 MINIMAL production fixes (`RouteBinding.scala` scala-3, `RouteBindingMacros.scala` scala-2 - the ONLY production files touched, and only because this todo's own investigation surfaced a real, root-caused, fixable allocation regression, exactly per the todo's own "Must NOT do" exception clause). `PathVarHandler.scala`/`PathVarHandlerMacros.scala` (scala-2, phase 1) were read but NOT modified - phase 1's own extraction logic (native `._1`/`._2` field access, no `productElement`) was already allocation-free and needed no fix.
- Removed two stray `mill.<random>` zero-byte artifact files (same class of pre-existing mill-wrapper-download artifact the plan's own Risk section documents for the main workspace) before committing - not part of this todo's diff.
- Commit: `af759f14d4f0` (jj change `xrvotnrwkmvo`), message `test(http,benchmarks): verify zero-allocation profile of name+type handler binding` (the plan's exact Todo 8 commit message), parented directly on Todo 7's `tzmzyprqwrtl`/`b46cb575b2ed`.

## Todo 10 findings (zio-http, dedicated D7-tier-isolation + D6-property test suite - `PathVarD7TiersSpec.scala`, zero production files touched)

- **Workspace**: reused the ALREADY-EXISTING, clean `zio-http-todo7-overload-safety` workspace (its `@` was an empty change directly on top of Todo 7's own tip `tzmzyprqwrtl`/`b46cb575b2ed` - i.e. it was already exactly where this todo needed to start from; no new workspace was created, since one matching the task's exact required base already existed and had zero uncommitted changes). Confirmed via `jj log -r 'yosxowkp | yosxowkp-'` before touching anything.
- **Deliberate design choice**: `PathVarHandlerBindingSpec.scala` (Todos 4/5/6) and `RouteArrowOverloadSafetySpec.scala` (Todo 7) already exercise D7/D6 as part of reproducing Worked Examples and proving overload safety, but every case there MIXES 2+ tiers in one handler (e.g. Worked Example 9 = PathVar + Context + Request together). This todo's new file, `PathVarD7TiersSpec.scala` (both `scala-2`/`scala-3` test dirs), isolates each D7 tier into its OWN minimal test(s) where every OTHER tier/capability is verifiably ABSENT from both the pattern and the handler signature (not merely unused):
  - tier 1 suite: PathVar-only handlers (Context.empty, no Request/Scope params anywhere) - single var, out-of-order multi-var, same-type-by-name disambiguation.
  - tier 2 suite: patterns with **ZERO captured segments at all** (`GET / "widgets"`, `GET / "checkout"`) whose handlers consume ONLY Context-resolved params (one, then two distinct Context types together) - proves the fallback works with no PathVar in the pattern whatsoever, not just "an unmatched name."
  - tier 3 suite: patterns with zero PathVars, handlers consuming Request alone, Scope alone, and Request+Scope together.
  - tier 4 suite: one isolated `typeCheck`-based negative test - a PathVar-eligible-typed param (`Int`) whose name matches nothing in the pattern, with NO Context/Request/Scope param present anywhere in the same handler, so the failure cannot be attributed to any other concern.
  - D6 property suite: ONE pattern (`GET / int("a") / string("b") / bool("c")`), THREE handlers with three different parameter orderings (declared order, fully reversed, middle-first), asserting all three responses equal a canonical expected value AND equal each other for the SAME input - not just "it compiles," per the task's explicit requirement.
- New Context-only capability types (`D7TierCartId`, `D7TierUserRef`) were deliberately named distinctly from `PathVarHandlerBindingSpec`'s own `BasketId` to avoid any cross-file confusion, even though Scala-3's `BasketId` is nested inside that file's own object (not visible here) and Scala-2's `BasketId` is package-level (visible here, but intentionally not reused, to keep this file's fixtures self-contained and its own tier-2 tests independently readable without cross-referencing another spec file).
- Confirmed the "zero-PathVar pattern" runtime shape convention already established in Todo 5's own worked-example-5-equivalent (Scala 2.13's `POST / "logout" -> handler(() => ...)` test, `vars = ()`): a `RoutePattern` built purely from literal/method segments has value type `A = Unit`, and `PathVarHandlerMacros`'s `runtimeShapeOf`/`tupleTypeOf` both map "zero open PathVar params" to `Unit` as well - so `route.handler.handle(req, ctx, (), scope)` is the correct Scala 2.13 call shape for every zero-PathVar tier-2/tier-3 test in this new file; Scala 3's `run` helper handles this transparently via `pattern.decode(...)` on an empty path suffix.
- Top-of-file module doc in both platform files explicitly points to `PathVarHandlerBindingSpec.scala` (tests 14-16, `FatalWarningsProof`) for warning-content/`-Werror`/`-Xfatal-warnings` proofs and to `RouteArrowOverloadSafetySpec.scala` for overload-resolution-safety/D12-boundary proofs, per the todo's explicit "reference, do not duplicate" requirement - no assertions from either file were copied.
- **Verification (fresh `mill clean` + rebuild, both platforms, run TWICE to confirm determinism)**: `mill 'core.jvm[2.13.18].test'` = 245/245 mill tasks, 93/93 tests (83 baseline + 10 new), 0 failures, both runs identical. `mill 'core.jvm[3.8.3].test'` = 245/245 mill tasks, 94/94 tests (84 baseline + 10 new), 0 failures, both runs identical. Exactly +10 new test cases on each platform (3 tier-1 + 2 tier-2 + 3 tier-3 + 1 tier-4 + 1 D6-property = 10), matching 1:1 across both platform files by design.
- Scope: `jj diff --stat` shows exactly 2 new files (`PathVarD7TiersSpec.scala`, both `scala-2` and `scala-3` test dirs under `zio-http-core/jvm/src/test/`) - zero production files touched, zero existing test files modified.
- No mill scalafmt task/binary available in this environment (same finding as Todo 4/5's notepad entries - `build.mill` has no `ScalafmtModule` wiring despite `.scalafmt.conf` existing); code hand-formatted to match the existing file style (2-space indent, matching `PathVarHandlerBindingSpec.scala`/`RouteArrowOverloadSafetySpec.scala` conventions).
- Commit: `3e5dac503cb1` (jj change `yosxowkpryok`), message `test(http): add comprehensive PathVar handler-binding test suite` (the plan's exact Todo 10 commit message), parented directly on Todo 7's `tzmzyprqwrtl`/`b46cb575b2ed`.

## Todo 11 findings (docs update)

- The public docs should stay strictly user-facing. The `customerId` / `basketId` example is the right way to explain the 4-tier resolution chain without leaking the macro machinery.
- The exact warning text to show users is `warning: Variable postId:String was defined in the path but is never used`.
- Middleware remains a `Routes(...) @@ mw` concern, so the docs should say that plainly and avoid implying any new route-level middleware surface.
