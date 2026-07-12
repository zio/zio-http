## [2026-07-12] FlashMap fix for S3 -Werror: changed to values.toMap (cross-compatible, no splice)

## [2026-07-12] Scala 2.13 test compile fix: given/using -> implicit val/implicit

**Files changed:**
- `CoreMiddlewareSpec.scala:70`: `given IsNominalType[...] = ...` → `implicit val isNominalTypeFlashMap: IsNominalType[...] = ...`
- `MiddlewareMacroSpec.scala:16-17`: `(using ev: ...)` → `(implicit ev: ...)`

**Verification (both passed):**
- `./mill core.jvm.2_13_18.compile` → SUCCESS (59/59)
- `./mill core.jvm.3_8_3.compile` → SUCCESS (59/59)

## [2026-07-12] Fix 9 failing tests: `isInstanceOf[Halt]` → `foldResult` on Scala 2 `Either`

**Problem:** AuthMiddlewareSpec and InterceptMiddlewareSpec run on both Scala 2 and 3 (shared `scala/` test dir). On Scala 2, `Response | Halt` is `Either[Response, Halt]` via `ResultType.|`. The `.isInstanceOf[Halt]` calls always return `false` on `Either`, causing 9 tests to fail.

**Fix:** Replaced all `.isInstanceOf[Halt]` with `foldResult(result)(_ => false, _ => true)` which pattern-matches correctly on both Scala 2 (Left/Right) and Scala 3 (union type match). `foldResult` is already imported via `import zio.http.ResultType._`.

**Files changed:**
- `AuthMiddlewareSpec.scala`: 4 occurrences (lines 33, 38, 52, 66)
- `InterceptMiddlewareSpec.scala`: 5 occurrences (lines 24, 50, 69, 88, 124)

**Verification:**
- `./mill core.jvm.2_13_18.test zio.http.AuthMiddlewareSpec` → SUCCESS
- `./mill core.jvm.2_13_18.test zio.http.InterceptMiddlewareSpec` → SUCCESS
- `./mill core.jvm.3_8_3.test zio.http.AuthMiddlewareSpec` → SUCCESS (all AuthMiddleware tests pass; pre-existing failure in CoreMiddlewareSpec only)
- `./mill core.jvm.3_8_3.test zio.http.InterceptMiddlewareSpec` → SUCCESS (all InterceptMiddleware tests pass; pre-existing failure in CoreMiddlewareSpec only)

**Note:** The `== responseAsResult(Response.text("ok"))` comparisons (used for "allows" assertions) were already correct on both Scala 2 (via implicit `Left` conversion) and Scala 3 (union identity), so those were left unchanged.
