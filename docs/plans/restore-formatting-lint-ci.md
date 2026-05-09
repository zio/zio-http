# Restore formatting and lint CI checks

## Objective

Restore the missing formatting/lint validation that was dropped during the sbt-to-mill migration, without reintroducing any other deferred build or CI surfaces.

## Why this plan exists

The old CI workflow had a dedicated `lint` job that ran formatting and scalafix validation through sbt:

- `sbt "++2.13.18; fmtCheck"`

That job disappeared in the mill-only workflow. The current CI in `.github/workflows/ci.yml` only runs:

- compile
- core JVM tests
- docs site build

So the repo currently has no CI guardrail for:

- Scala formatting drift
- import organization drift
- lint-style regressions that were previously caught before merge

## In scope

- Add a mill-era replacement for the old formatting/lint CI job
- Define the exact local command developers can run before pushing
- Update CI so formatting/lint failures fail the aggregate `ci` job
- Match the current mill-only build direction; do not reintroduce sbt

## Explicitly out of scope

These missing features are intentionally deferred for now:

1. benchmarks / performance regression automation
2. MIMA / binary compatibility checks
3. Scala.js CI test coverage
4. Scaladoc generation checks
5. release / publish automation
6. coverage reporting
7. shaded publishing

## Current gap

### Previously present

- `project/plugins.sbt` included:
  - `sbt-scalafmt`
  - `sbt-scalafix`
- `aliases.sbt` included:
  - `fmt`
  - `fmtCheck`
  - `sFix`
  - `sFixCheck`
- old `.github/workflows/ci.yml` had:
  - `lint` job

### Currently missing

- No formatting check job in `.github/workflows/ci.yml`
- No import-organization/lint validation in CI
- No documented mill replacement command for the old sbt aliases

## Proposed approach

1. Identify the correct mill-native way to run formatting and lint validation for this repository.
   - Prefer existing mill plugins/tasks if already available.
   - If not available, add the smallest mill-native solution that fits the current repo.
2. Add a dedicated CI job for formatting/lint validation.
   - Name should stay clear and boring, e.g. `lint` or `formatting`.
3. Wire that job into the aggregate `ci` job so failures block merges.
4. Document the local developer command in a stable place, likely `docs/installation.md` or contributor-facing docs.

## Candidate implementation details to verify during execution

- Whether mill formatting support should come from:
  - a mill scalafmt module/plugin,
  - a custom task in `build.mill`,
  - or a small wrapper script around mill-supported tooling.
- Whether scalafix/import organization is still required, or whether this repo should reduce scope to formatting-only for now.
- Whether all Scala sources are covered, including nested modules and generated-code exclusions.

## Success criteria

- A developer can run one documented command locally to validate formatting/lint state.
- CI has a dedicated formatting/lint job.
- The aggregate `ci` job fails when formatting/lint validation fails.
- The solution is mill-only and does not depend on sbt.
- No unrelated CI surfaces are restored as part of this work.

## Risks / watchouts

- Restoring scalafix behavior exactly may be harder than restoring scalafmt checks.
- Generated or tool-managed files may need exclusions to avoid noisy CI failures.
- The repo may need a pragmatic first step: formatting checks first, deeper lint checks second.

## Implementation checklist

- [ ] Confirm the desired mill-native formatting/lint command(s)
- [ ] Add build support for those command(s)
- [ ] Add CI job in `.github/workflows/ci.yml`
- [ ] Add the job to aggregate CI gating
- [ ] Document the local validation command
- [ ] Run the new formatting/lint validation locally
- [ ] Run the relevant CI-equivalent validation after changes

## Status

- Planned
- Not yet implemented
