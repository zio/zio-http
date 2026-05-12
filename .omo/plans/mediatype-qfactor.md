# Replace MediaTypeWithQFactor with Accept.MediaRange

## TL;DR

> **Quick Summary**: Replace all references to the deleted `Header.Accept.MediaTypeWithQFactor` with `Accept.MediaRange` from zio-blocks-http-model, and fix Handler.scala's Accept/mimeTypes usage to use the blocks API.
> 
> **Deliverables**:
> - All `MediaTypeWithQFactor` references replaced with `Accept.MediaRange`
> - Handler.scala Accept usage fixed (`.mimeTypes` → `.mediaRanges`)
> - Compile succeeds for affected modules
> 
> **Estimated Effort**: Short
> **Parallel Execution**: YES - 2 waves
> **Critical Path**: Task 1 → Task 3

---

## Context

### Original Request
User wants to use blocks' mediatype micro-lib and replace the old `MediaTypeWithQFactor` class (which was deleted when headers moved to blocks) with the blocks equivalent.

### Interview Summary
**Key Discussions**:
- Old `MediaTypeWithQFactor(mediaType: MediaType, qFactor: Option[Double])` was inside `Header.Accept` companion
- Blocks has `Accept.MediaRange(mediaType: zio.blocks.mediatype.MediaType, quality: Double)` — equivalent
- User chose to use `Accept.MediaRange` directly instead of creating a wrapper class
- User chose to use `zio.blocks.mediatype.MediaType` (blocks) not local `zio.http.MediaType`
- Mediatype dep is transitively available via `zio-blocks-http-model`

### Metis Review
**Identified Gaps** (addressed):
- `Option[Double]` vs `Double` semantic difference: HTTP spec says absent q-factor = 1.0, so `None` → `1.0` is correct. Codec code that checks `.qFactor.getOrElse(1.0)` becomes just `.quality`.
- Package location: No new class needed — using `Accept.MediaRange` directly
- Ordering: Must provide `implicit val ordering: Ordering[Accept.MediaRange]` somewhere, since old code had one on `MediaTypeWithQFactor`. Define it locally where needed or as an extension.

---

## Work Objectives

### Core Objective
Replace all `Header.Accept.MediaTypeWithQFactor` references with `Accept.MediaRange` from blocks, and fix Accept usage in Handler.scala.

### Concrete Deliverables
- Updated imports in 5 codec files + Handler.scala
- `Accept.MediaRange` ordering provided where sorting is needed
- Handler.scala Accept/mimeTypes code uses blocks API

### Definition of Done
- [ ] `./mill "core.jvm[2.13.18].compile"` has no `MediaTypeWithQFactor` errors
- [ ] `grep -r "MediaTypeWithQFactor" zio-http-core/` returns 0 results
- [ ] `grep -r "\.mimeTypes" zio-http-core/shared/src/main/` returns 0 results

### Must Have
- All `MediaTypeWithQFactor` replaced with `Accept.MediaRange`
- Ordering by quality preserved (descending, highest quality first)
- `qFactor: Option[Double]` → `quality: Double` semantic equivalence (None → 1.0)

### Must NOT Have (Guardrails)
- Do NOT create a new `MediaTypeWithQFactor` class — use `Accept.MediaRange` directly
- Do NOT add explicit mediatype dependency to build.mill — it's transitive via http-model
- Do NOT touch local `zio.http.MediaType` usages outside the named files
- Do NOT refactor codec logic beyond what's needed for the type change
- Do NOT change `import zio._` to `import zio.*` — Scala 2.13

---

## Verification Strategy

> **ZERO HUMAN INTERVENTION** — ALL verification is agent-executed.

### Test Decision
- **Infrastructure exists**: YES (mill test)
- **Automated tests**: Tests-after (verify compile, run existing tests)
- **Framework**: mill test

### QA Policy
Every task includes compile verification.

---

## Execution Strategy

### Parallel Execution Waves

```
Wave 1 (Start Immediately — independent file fixes):
├── Task 1: Fix Handler.scala Accept usage [quick]
├── Task 2: Fix codec files — replace MediaTypeWithQFactor [quick]

Wave 2 (After Wave 1 — also fix Middleware.scala CORS errors):
├── Task 3: Fix Middleware.scala CORS header API [quick]

Wave FINAL (After ALL — compile verification):
├── Task F1: Full compile check [quick]
```

### Dependency Matrix
- **1**: None → F1
- **2**: None → F1
- **3**: None → F1
- **F1**: 1, 2, 3

### Agent Dispatch Summary
- **1**: **3** — T1-T3 → `quick`
- **FINAL**: **1** — F1 → `quick`

---

## TODOs

- [x] 1. Fix Handler.scala Accept usage

  **What to do**:
  - In Handler.scala, the `error` method (around line 790) uses `_.mimeTypes.sorted.map(_.mediaType)` on `Header.Accept`. Replace with blocks API:
    ```scala
    // Old: accept.flatMap(_.mimeTypes.sorted.map(_.mediaType).collectFirst { ... })
    // New: accept.flatMap { a =>
    //   a.mediaRanges.toList.sortBy(-_.quality).map(_.mediaType.fullType).collectFirst {
    //     case ft if errorMediaTypes.exists(_.fullType == ft) =>
    //       errorMediaTypes.find(_.fullType == ft).get
    //   }
    // }
    ```
  - Note: `Accept.MediaRange.mediaType` returns `zio.blocks.mediatype.MediaType`, but `errorMediaTypes` is `List[zio.http.MediaType]`. Compare via `.fullType` (String).
  - Check if this was already partially fixed by previous subagent work — read current state first.

  **Must NOT do**:
  - Do not change imports from `_` to `*`
  - Do not touch other methods in Handler.scala

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Task 2)
  - **Blocks**: Task F1
  - **Blocked By**: None

  **References**:
  - `zio-http-core/shared/src/main/scala/zio/http/Handler.scala:~790` — the `error` method with Accept usage
  - `/Users/nabil_abdel-hafeez/zio-repos/zio-blocks/http-model/shared/src/main/scala/zio/http/headers/NegotiationHeaders.scala:25-69` — blocks `Accept` and `Accept.MediaRange` API

  **Acceptance Criteria**:
  - [ ] No `_.mimeTypes` references in Handler.scala
  - [ ] Compile: `rm -rf out/core/jvm/2.13.18/compile.dest && ./mill "core.jvm[2.13.18].compile"` — no Accept-related errors in Handler.scala

  **QA Scenarios**:
  ```
  Scenario: Handler.scala compiles without mimeTypes errors
    Tool: Bash
    Steps:
      1. grep -n "mimeTypes" zio-http-core/shared/src/main/scala/zio/http/Handler.scala
      2. Expect: no output (0 matches)
    Expected Result: No references to mimeTypes
    Evidence: .sisyphus/evidence/task-1-no-mimetypes.txt
  ```

  **Commit**: YES (groups with 2, 3)
  - Message: `refactor(http): replace MediaTypeWithQFactor with Accept.MediaRange`

- [x] 2. Fix codec files — replace MediaTypeWithQFactor with Accept.MediaRange

  **What to do**:
  - In these files, replace `import zio.http.Header.Accept.MediaTypeWithQFactor` with `import zio.http.Header.Accept.MediaRange`:
    - `zio-http-core/shared/src/main/scala/zio/http/codec/HttpContentCodec.scala`
    - `zio-http-core/shared/src/main/scala/zio/http/codec/internal/EncoderDecoder.scala`
    - `zio-http-core/shared/src/main/scala/zio/http/codec/internal/BodyCodec.scala`
    - `zio-http-core/shared/src/main/scala/zio/http/codec/HttpCodec.scala`
  - Replace all `MediaTypeWithQFactor` type references with `Accept.MediaRange`
  - Replace `.qFactor.getOrElse(1.0)` with `.quality`
  - Replace `.qFactor` with `.quality` (wrapping in `Some()` if callers expect `Option`)
  - Replace `.mediaType` usages — note blocks `MediaRange.mediaType` returns `zio.blocks.mediatype.MediaType`, NOT `zio.http.MediaType`. If codec code does `mt.mediaType.matches(...)`, check if blocks MediaType has `.matches()` (it does — see blocks MediaType.scala:30).
  - Where sorting by qFactor was done via `MediaTypeWithQFactor.ordering`, use `.sortBy(-_.quality)` inline or define:
    ```scala
    implicit val mediaRangeOrdering: Ordering[Accept.MediaRange] = 
      Ordering.by[Accept.MediaRange, Double](_.quality).reverse
    ```
  - Also check `zio-http-endpoint/shared/src/main/scala/zio/http/endpoint/Endpoint.scala` for references.

  **Must NOT do**:
  - Do not restructure codec logic
  - Do not change method signatures beyond the type parameter change
  - Do not change import syntax from `_` to `*`

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Task 1)
  - **Blocks**: Task F1
  - **Blocked By**: None

  **References**:
  - `zio-http-core/shared/src/main/scala/zio/http/codec/HttpContentCodec.scala:12,90,110` — import + usage
  - `zio-http-core/shared/src/main/scala/zio/http/codec/internal/EncoderDecoder.scala:24,33,81,135,187,386,400` — heavy usage
  - `/Users/nabil_abdel-hafeez/zio-repos/zio-blocks/http-model/shared/src/main/scala/zio/http/headers/NegotiationHeaders.scala:25-69` — `Accept.MediaRange` definition
  - `/Users/nabil_abdel-hafeez/zio-repos/zio-blocks/mediatype/shared/src/main/scala/zio/blocks/mediatype/MediaType.scala:30` — `.matches()` method on blocks MediaType

  **Acceptance Criteria**:
  - [ ] `grep -r "MediaTypeWithQFactor" zio-http-core/ zio-http-endpoint/` returns 0 results
  - [ ] Compile: no MediaTypeWithQFactor errors

  **QA Scenarios**:
  ```
  Scenario: No MediaTypeWithQFactor references remain
    Tool: Bash
    Steps:
      1. grep -r "MediaTypeWithQFactor" zio-http-core/ zio-http-endpoint/
      2. Expect: no output
    Expected Result: 0 matches
    Evidence: .sisyphus/evidence/task-2-no-mtwqf.txt
  ```

  **Commit**: YES (groups with 1, 3)

- [x] 3. Fix Middleware.scala CORS header API

  **What to do**:
  - Fix all 10 CORS-related compile errors in Middleware.scala:
    1. `AccessControlAllowOrigin.Specific(origin)` — `origin` is `Header.Origin`, needs `String` → use `origin.renderedValue`
    2. `AccessControlAllowMethods.All` doesn't exist → use `AccessControlAllowMethods(zio.blocks.chunk.Chunk(Method.GET, Method.POST, Method.PUT, Method.DELETE, Method.PATCH, Method.HEAD, Method.OPTIONS))`
    3. `AccessControlAllowHeaders.All` doesn't exist → use `AccessControlAllowHeaders(zio.blocks.chunk.Chunk("*"))`
    4. `AccessControlAllowCredentials.Allow` doesn't exist → use `AccessControlAllowCredentials(true)`
    5. `AccessControlExposeHeaders.All` doesn't exist → use `AccessControlExposeHeaders(zio.blocks.chunk.Chunk("*"))`
    6. `AccessControlAllowHeaders.Some` pattern match → use `case s: Header.AccessControlAllowHeaders if s.headers.toList != List("*") => s.headers.toList.toSet`
    7. `Headers(config.allowedMethods, ...)` — `Headers(...)` takes `(String, String)*` tuples → convert each header to `h.headerName -> h.renderedValue`

  **Must NOT do**:
  - Do not touch Handler.scala or HandlerAspect.scala
  - Do not change import syntax

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1 (with Tasks 1, 2)
  - **Blocks**: Task F1
  - **Blocked By**: None

  **References**:
  - `zio-http-core/shared/src/main/scala/zio/http/Middleware.scala:58-105` — CorsConfig and cors method
  - `/Users/nabil_abdel-hafeez/zio-repos/zio-blocks/http-model/shared/src/main/scala/zio/http/headers/CorsHeaders.scala` — blocks CORS header types
  - Blocks `AccessControlAllowMethods(methods: Chunk[Method])` — takes `Chunk[Method]`
  - Blocks `AccessControlAllowHeaders(headers: Chunk[String])` — takes `Chunk[String]`
  - Blocks `AccessControlAllowCredentials(allow: Boolean)` — takes Boolean
  - Blocks `AccessControlExposeHeaders(headers: Chunk[String])` — takes `Chunk[String]`

  **Acceptance Criteria**:
  - [ ] Compile: no Middleware.scala errors

  **QA Scenarios**:
  ```
  Scenario: Middleware.scala compiles clean
    Tool: Bash
    Steps:
      1. rm -rf out/core/jvm/2.13.18/compile.dest && ./mill "core.jvm[2.13.18].compile" 2>&1 | grep "error.*Middleware"
      2. Expect: no output
    Expected Result: 0 Middleware errors
    Evidence: .sisyphus/evidence/task-3-middleware-clean.txt
  ```

  **Commit**: YES (groups with 1, 2)

---

## Final Verification Wave

- [x] F1. **Compile Verification** — `quick`
  Run `rm -rf out/core/jvm/2.13.18/compile.dest && ./mill "core.jvm[2.13.18].compile"`. Verify fewer errors than before (may still have errors in other files from the broader migration, but NO errors related to MediaTypeWithQFactor, mimeTypes, or CORS headers).
  Output: `Errors [N] | MediaTypeWithQFactor [0] | mimeTypes [0] | CORS [0] | VERDICT`

---

## Commit Strategy

- **1+2+3**: `refactor(http): replace MediaTypeWithQFactor with Accept.MediaRange and fix CORS headers` — all changed files, `./mill "core.jvm[2.13.18].compile"`

---

## Success Criteria

### Verification Commands
```bash
grep -r "MediaTypeWithQFactor" zio-http-core/ zio-http-endpoint/  # Expected: 0 results
grep -n "mimeTypes" zio-http-core/shared/src/main/scala/zio/http/Handler.scala  # Expected: 0 results
rm -rf out/core/jvm/2.13.18/compile.dest && ./mill "core.jvm[2.13.18].compile"  # Expected: no MTWQF/mimeTypes/CORS errors
```

### Final Checklist
- [ ] All "Must Have" present
- [ ] All "Must NOT Have" absent
- [ ] MediaTypeWithQFactor fully replaced
- [ ] CORS headers fixed
