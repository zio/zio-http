# Endpoint-Blocks Integration Plan

## Objective
Migrate `zio-http-endpoint` off its orphaned legacy `zio.http.codec`/hand-rolled `Endpoint`/`AuthType`/`RoutePattern` API (which no longer compiles — `zio.http.codec` was already deleted from `zio-http-core`) onto `zio.blocks.endpoint.*` (a resolved dependency via Sonatype snapshots), then add a thin `.implement`/`.call` extension layer with full Scala 2.13/3.8.3 parity, union-type return values, and testkit migration.

Full requirements/constraints/rejected-approaches: see `.omo/notepads/endpoint-blocks/decisions.md` — READ FIRST before any task below.

**Workspace**: `/Users/nabil_abdel-hafeez/zio-repos/zio-http-v4-engine-pr4188` — this is the CORRECT, canonical local workspace for PR #4188 (confirmed by user 2026-07-07). Do NOT create parallel/duplicate workspaces for this work again.

## Root Cause (confirmed via direct compile, 2026-07-07, in this exact workspace)
`zio-http-core/shared/src/main/scala/zio/http/package.scala` no longer exports `codec`; `zio.http.codec` package is deleted from `zio-http-core` (only a JS-platform stub file survives). Every file in `zio-http-endpoint` importing `zio.http.codec.{HttpCodec, HttpCodecType, ...}` is broken: `./mill endpoint.jvm[3.8.3].compile` → **134 errors found**, while `./mill core.jvm[3.8.3].compile` → **SUCCESS**. No file in `zio-http-endpoint` yet imports `zio.blocks.endpoint.*` — migration has not started in source.

## Out of Scope — Do Not Touch (already complete, unrelated feature, compiles clean)
`zio-http-core/shared/src/main/scala-{2,3}/zio/http/{RouteBinding,PathVarHandler,PathVarHandlerMacros,RouteBindingMacros}.scala` — a typed path-variable-to-handler-binding system for zio-http's own native routing (`.unused`/`PathVar.Ignored` warning semantics, zero-allocation handler binding by name+type). This is real, already-merged-quality work from prior commits on this same branch (`feat(http): support PathVar.Ignored (.unused) in Scala 3/2.13 -> macro`, etc.) and is orthogonal to the `zio.blocks.endpoint` integration. Do not modify, do not delete, do not treat as part of this task's deliverable.

## Key API Facts (from zio-blocks 0.0.46, confirmed by reading source)
- `zio.blocks.endpoint.Endpoint[PathInput, Input, Err, Output, Auth <: AuthType]` — pure data, explicitly "no implement* methods, no Invocation". Same 5-type-param shape as the legacy `zio.http.endpoint.Endpoint`, which makes this a natural drop-in replacement.
- `HttpCodec[K <: CodecKind, A]` — Combine/Fallback/Query/Header/Body/StatusCodec atoms, `++`/`|` combinators. Already imports `zio.http.{Header, Status}` directly (blocks depends on zio-http-core's primitives, not vice versa).
- `Alternator[L,R] { type Out; combine(Either[L,R]): Out; separate(Out): Either[L,R] }` — uniform cross-version abstraction. `Alternator.fromEithers`/`fromUnions` bridge to the two backing typeclasses below.
- **Scala 3**: `zio.blocks.combinators.Unions.Unions.WithOut[L,R,L|R]` — a REAL macro-derived Scala 3 union type (`Out = L | R` literally). Scala 3 `.implement`/`.call` can expose genuine native unions with ZERO custom union-synthesis code — zio-blocks already provides it.
- **Scala 2**: `zio.blocks.combinators.Eithers.Eithers.WithOut[L,R,Out]` — whitebox macro; for two non-Either leaves, `Out = Either[L,R]` literally. This is WHY the project's own `type |[+A,+B] = Either[A,B]` alias (per decisions.md, defined locally in `zio.http.endpoint`, NOT re-exporting `ResultType`) is the correct design.
- `AuthType.ClientRequirement` — auth-derived data only. **No generic environment/context type param exists anywhere in the new Endpoint** — no "extra context extraction"/intersection-type macro work is needed against this API version.
- `RoutePattern[A]` (blocks, in `zio.blocks.endpoint`) — `case class RoutePattern[A](method: Method, pathCodec: PathCodec[A], doc: Doc)`, imports `zio.http.{Method, Path}` directly. Has `decode(Method, Path): Either[String,A]`, `encode`, `format`, `matches`. A separate, richer, PathVars-aware routing description layered on zio-http's own `Method`/`Path` — distinct from the zio-http-native `RouteBinding`/`PathVarHandler` system listed as out-of-scope above (do not conflate the two).
- `RouteTree[A]` (blocks) — a real routing trie (`get(Method, Path): Option[A]`) you plug handler values into. Does the matching algorithm but does NOT wire into zio-http's `Routes`/`Handler` pipeline — that bridging is exactly what our `.implement`/`.call` layer must build.
- `PathCodecRuntime.decodeCodec` — the real backtracking path decoder, `private[endpoint]`, operates on `zio.http.Path` segments.

## Legacy File Inventory (zio-http-endpoint, ~7,669 LOC across 17 files, confirmed present in THIS workspace)
| File | LOC | Fate |
|---|---|---|
| `Endpoint.scala` | ~1963 | DELETE — replace with `zio.blocks.endpoint.Endpoint` + new `.implement`/`.call` extensions. `implementHandler` (the real dispatch engine) is fully implemented already, not a stub — reuse its *logic* (content-negotiated encode/decode, auth codec merge) when writing the new bridge. |
| `AuthType.scala` | ~93 | DELETE — replaced by `zio.blocks.endpoint.AuthType`. |
| `EndpointExecutor.scala` | ~180 | DELETE — client dispatch rebuilt around `.call` + `Client`/`Wire` config per decisions.md. |
| `EndpointLocator.scala` | ~64 | DELETE — already `@deprecated` since 3.6.0 upstream. |
| `EndpointNotFound.scala` | ~21 | DELETE (or keep as a plain error type if the new `.call` layer still needs a "no route" exception — decide during implementation, default to delete). |
| `Invocation.scala` | ~31 | DELETE. |
| `grpc/GRPC.scala` | ~21 | DELETE — per decisions.md scope decision (gen/cli/grpc surface binned). |
| `http/HttpFile.scala`, `http/HttpGen.scala` | ~91+208 | DELETE — per decisions.md. |
| `internal/EndpointClient.scala` | ~89 | DELETE — replaced by new `.call` implementation. |
| `internal/MemoizedZIO.scala` | ~43 | KEEP IF the new client dispatch still wants endpoint->client memoization; otherwise delete. Decide during task 4. |
| `openapi/JsonSchema.scala`, `openapi/OpenAPI.scala`, `openapi/OpenAPIGen.scala`, `openapi/SwaggerUI.scala` | ~4674 total | DELETE — per decisions.md scope decision. |
| `scala-3/extensions.scala` | ~188 | DELETE — superseded by zio-blocks' own `Unions`/`Alternator`-based union support. |
| `jvm/EndpointPlatformSpecific.scala` (+ JS twin) | ~3 | DELETE if the new `Endpoint` usage is a type alias to blocks' `Endpoint` (no platform-specific trait needed); confirm during task 3. |
| Test: `jvm/src/test/scala-3/.../UnionRoundtripSpec.scala` + ~23 other legacy test specs | ? | DELETE — tests the legacy API; replaced by new `BlocksEndpointSpec`/`BlocksEndpointScala2Spec` (task 5). |

Net effect: delete essentially the entire existing `zio-http-endpoint` module content (~7,600 LOC of legacy/generator code) and rebuild a much smaller `.implement`/`.call` bridge layer on top of `zio.blocks.endpoint`.

## TODOs

1. [x] Add `blocksDep("endpoint")` directly to the `endpoint` module's `mvnDeps` in `build.mill` (currently only depends transitively via `core.jvm()`), then verify `endpoint.jvm[3.8.3].compile` can resolve `zio.blocks.endpoint.Endpoint`. DONE: Sonatype resolver + `Versions.ZioBlocks = "0.0.46+5-1f07ae2d-SNAPSHOT"` + direct `blocksDep("endpoint")` added; verified resolution clean, zero resolution errors.
2. [x] Delete the legacy files listed "DELETE" in the inventory table above (shared + jvm + js + their test counterparts) from `zio-http-endpoint/`; do NOT delete `internal/MemoizedZIO.scala` yet, defer that decision to task 4. Do NOT touch anything under `zio-http-core`. DONE: 17 main + 22 test files deleted; `endpoint.jvm[3.8.3].compile` verified SUCCESS (0 errors, was 134).
3. [x] Design and implement the Scala 3 `.implement`/`.call` extension layer. DONE with a documented gap: `.implement`/`.call` both work end-to-end with REAL native Scala 3 union types (root cause of the 8-round blocker was a cross-version `package.scala` shadowing Scala 3's native `|` — fixed by moving the alias to scala-2-only). `EndpointResultHandler` TC dispatch, zero ZIO imports, `private[endpoint]` internals, single-method constraint all verified. GAP (accepted, not silently dropped): partial application of input params and `.unused` 4-combination warning wiring are NOT implemented on Scala 3 — blocked by a Scala 3 inline-macro `Type[Input]` evidence-propagation issue from extension-method context into macro scope (documented in decisions.md, distinct from the union-shadowing bug).
4. [x] Design and implement the Scala 2.13 `.implement`/`.call` parity layer via blackbox macros. DONE with an IMPORTANT CORRECTION found during task 5's independent test-coverage work: `.implement`/`.call` both work with the local `type |[+A,+B]=Either[A,B]` alias (scala-2-only, not reexporting `ResultType`), TC dispatch, `Unused[A]` marker type, and the 4-combination warning macro logic all exist and compile — BUT for a **case-class `Input`**, there is NO handler shape that both satisfies Scala's own `Function1` type-check and the macro's field-matching (locked in via negative `zio.test.typeCheck` tests in `EndpointPartialApplicationSpec.scala`, confirmed by Atlas). Partial application and the warning logic are only real for non-case-class (primitive) `Input`, functionally equivalent to Scala 3's (also non-partial) behavior. Earlier notepad entries claiming "full parity, real partial application" were not exercised against a real case-class Input in a passing test and are corrected here. `internal/MemoizedZIO.scala` retained untouched (not needed by the new client dispatch).
5. [x] Write new test coverage proving `.implement`/`.call`/`|` work via `import zio.http.endpoint.*` alone on both Scala versions, covering zero-arg implement, query/path decode, union error/output round-trip, `.call` client-side round-trip, and an auth-required endpoint (supersedes deleted `UnionRoundtripSpec.scala`). DONE: Scala 3 6→13 tests, Scala 2.13 0→14 tests (was undiscoverable object+main before). Independently verified by Atlas: read `EndpointAuthSpec.scala`/`EndpointCallRoundtripSpec.scala` in full, confirmed real assertions (not placeholders), confirmed cached `./mill show endpoint.jvm[2.13.18].test` all-Success JSON. 5 real gaps found and documented (not fixed, out of scope): Auth unenforced at runtime both versions; `.call` hardcodes root path; Scala 2 case-class partial-apply is non-functional (locked in via negative `typeCheck` tests); Scala 2 `Eithers.WithOut` implicit resolution gap; Scala 2 bare-`Right` lambda inference issue.
6. [x] Verify `zio-http-testkit` compiles and its tests actually pass against the current sync `Client`/`Routes` API. DONE in sibling workspace `/Users/nabil_abdel-hafeez/zio-repos/zio-http-testkit-fix` (commit `22f2ec6d`), then RECONCILED into this workspace: `build.mill`'s `testkit` main+test `moduleDeps` had `endpoint.jvm()` removed (verified via `jj diff --git build.mill` by Atlas — exact 2-line diff matches the reconciliation plan), 5 testkit source files copied verbatim, 2 legacy files deleted. Independently verified by Atlas: `./mill show 'testkit.jvm[3.8.3].test'` cached JSON shows all-Success across TestClientSpec/RoutesPrecedentsSpec/TestServerSpec (14/14), and combined `endpoint.jvm[3.8.3].compile` unaffected (no regression).
7. [x] Get the full compile matrix green: `endpoint.jvm[3.8.3].test.compile`, `endpoint.jvm[2.13.18].test.compile`, `zio.jvm[3.8.3].compile`, `client.jvm[2.13.18].compile`, `testkit.jvm[3.8.3].test.compile`, plus `core.jvm`/`server.jvm` both versions. DONE: `./mill` run by Atlas directly (not delegated) — `302/302, SUCCESS` across all 9 targets, 0 errors.
8. [x] Run actual `.test` EXECUTION (not just `.test.compile`) green for endpoint, testkit, and zio modules. DONE: `./mill` run by Atlas directly — `353/353, SUCCESS` across `endpoint.jvm[3.8.3].test` (13 tests), `endpoint.jvm[2.13.18].test` (14 tests, cached-verified via `./mill show`), `testkit.jvm[3.8.3].test` (14 tests, cached-verified via `./mill show`), `zio.jvm[3.8.3].test`.
9. [ ] Commit the work via `jj describe` + bookmark in this workspace, being mindful of the existing `(divergent)` state on `zio-http-v4-engine` — coordinate/rebase rather than force-diverging further.

## Final Verification Wave
F1. [ ] Goal/constraint check — every hard constraint in decisions.md is met (no ZIO in endpoint module core, one `implement` method, union types not `Either` in user-facing surface, `import zio.http.endpoint.*` sufficiency on both Scala versions, zero tuple allocations, `private[endpoint]` internals, full 2.13 parity).
F2. [ ] Code quality review of all new/changed files.
F3. [ ] Security review (auth codec handling, no credential leakage in error responses given `AuthType.unauthorizedStatus` defaults to `NotFound` for information-hiding — verify this default is preserved).
F4. [ ] Hands-on QA — actually run a server with an implemented endpoint and a client `.call` against it (real HTTP round-trip, not just unit test mocks), covering at least one union-error case.

## Dependency Notes for Orchestration
- Task 1 and task 2 touch different files (build.mill vs source deletions) — parallel-safe, but task 3/4 depend on both.
- Task 3 and task 4 are parallel-safe (different `scala-2`/`scala-3` source trees) but both depend on tasks 1 and 2.
- Task 5 depends on tasks 3 and 4.
- Task 6 (testkit) is independent of tasks 1-5 — can run in parallel with the whole chain, but check for the `testkit-fix` workspace's existing commit first before redoing work.
- Tasks 7/8 depend on tasks 3, 4, 5, 6 all complete.
- Task 9 depends on tasks 7/8 passing.
