# Draft: Blocks-only stream bridge handoff

## Requirements (confirmed)
- Create a handoff plan for an agent working only on `zio-blocks`.
- Isolate the issue to the blocks repo.
- Focus on the stream/body capability fix, not zio-http migration.

## Technical Decisions
- Use the previously approved cross-repo plan as source material.
- Extract only the blocks-side design and implementation work.
- Keep references explicit to the external repo path: `/Users/nabil_abdel-hafeez/zio-repos/zio-blocks`.

## Research Findings
- Current blocks `Body` is a final wrapper over `Stream`.
- Current blocks `Stream` lacks a first-class producer-backed / push-to-pull bridge.
- zio-http currently works around this with side-channel async state; the desired long-term fix is in blocks streams.
- User philosophy: one body type only; empty body == empty chunk; stream should hide source differences.

## Open Questions
- None critical for the handoff.

## Scope Boundaries
- INCLUDE: stream bridge API, lifecycle/finalizer behavior, known-length metadata, tests, contract notes.
- EXCLUDE: zio-http integration, netty/fetch migration, downstream regression cleanup.
