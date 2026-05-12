# Blocks Stream Capabilities for zio-http Async Bodies

## TL;DR

> **Quick Summary**: Fix async/streaming HTTP body correctness by extending `zio-blocks` streams first, then refactoring `zio-http` netty/fetch integrations to use the new stream capabilities instead of side-channel hacks.
>
> **Deliverables**:
> - New push/async bridge capabilities in `zio-blocks` streams
> - `zio-http` netty/fetch body paths migrated off `WeakHashMap[Body, AsyncBodyState]`
> - Safe non-blocking request/response encoding semantics for streamed bodies
> - Explicit regression coverage for streaming, cancellation, empty bodies, and connection reuse
>
> **Estimated Effort**: Large
> **Parallel Execution**: YES - 4 waves
> **Critical Path**: Blocks stream bridge design → blocks implementation → netty/fetch migration → verification

---

## Context

### Original Request
Evaluate whether important areas are not in a clean state (DX, regression, performance), especially around async/streaming body handling after migrating zio-http to blocks. Treat the async/streaming issue as likely a shortcoming of blocks streams. Evaluate solutions that add capabilities to blocks streams, suggest a fix for `NettyRequestEncoder`, and produce a plan that explicitly includes changes that can/should happen in blocks.

### Interview Summary
**Key Discussions**:
- The critical async/streaming body issue should be treated as a **blocks-first** design problem, not merely a local zio-http patch.
- User maintains both repos and explicitly wants blocks changed where needed.
- The intended philosophy is: **stream is thin and easy enough that user-facing semantics should not distinguish stream from chunk/array/collection**.
- Therefore: **one body type only**, empty body = empty chunk, and async/streaming concerns should be solved in streams rather than body subclassing or side-channels.

**Research Findings**:
- `zio.http.Body` in blocks is a final wrapper over `zio.blocks.streams.Stream` with only `knownChunk` / `knownLength` metadata.
- `zio-http` netty currently uses `WeakHashMap[Body, AsyncBodyState]` to smuggle async producer semantics because blocks `Body` cannot represent them directly.
- `NettyBodyWriter` and `NettyRequestEncoder` contain unsafe eager materialization paths (`body.toArray`) that can break streaming semantics and block hot paths.
- External systems (http4s/fs2, Pekko/Akka HTTP, Netty-native flow control) consistently solve this with **push-to-pull bridging**, explicit cancellation/completion, and transport-native backpressure — not hidden body side-channels.

### Metis Review
**Identified Gaps** (addressed):
- Needed explicit decision on blocks scope → resolved: blocks changes are primary and in-scope now.
- Needed tighter scope boundaries to avoid full-body audit sprawl → plan limits body-related fixes to enumerated paths.
- Needed clearer acceptance criteria for both short-term zio-http behavior and medium-term blocks API shape → included below.
- Needed edge-case coverage (empty bodies, partial reads, cancellation, connection reuse, mid-stream failure, multipart interaction) → included in verification strategy and TODOs.

---

## Work Objectives

### Core Objective
Extend `zio-blocks` streams so that push-driven transport bodies (Netty callbacks, Fetch readable streams, similar producers) can be represented as normal blocks streams, then refactor `zio-http` to use those capabilities and eliminate hidden async-body side channels.

### Repository Boundary
- **zio-http repo (this workspace)**: `/Users/nabil_abdel-hafeez/zio-repos/zio-http`
- **zio-blocks repo (external but maintained by user)**: `/Users/nabil_abdel-hafeez/zio-repos/zio-blocks`

All tasks that reference `zio-blocks` files are intended to execute against the external repo path above, not inside this workspace.

### Concrete Deliverables
- New `zio-blocks` stream API for push/async producer bridging
- Stream-level lifecycle/finalizer support sufficient for transport resource cleanup
- Stream-level metadata support sufficient for known-length and safe materialization decisions
- `zio-http` netty response/request body paths migrated to the new stream capability
- `zio-http` fetch client body paths migrated or aligned with the new stream capability
- Defensive cleanup of eager body materialization hot paths in explicitly listed files
- Regression tests covering streaming correctness and transport behavior

### Proposed Stream Fix Design

> This plan does not merely say “streams need fixing”. The intended fix is:

1. **Add a first-class producer bridge to blocks streams** in `zio-blocks`, so a stream can be fed by an external push source without subclassing `Body` or storing hidden side-channel state.
2. **Use explicit producer callbacks/events** for:
   - emit chunk
   - fail stream
   - complete stream
3. **Attach lifecycle/finalizer semantics** so transport cleanup happens on:
   - normal completion
   - mid-stream failure
   - consumer cancellation / early abandonment
4. **Attach known-length metadata** for producer-backed streams when the transport already knows it (e.g. `Content-Length`).
5. **Use transport-native backpressure** in zio-http integrations:
   - Netty: `autoRead = false` + `ctx.read()` only when downstream is ready / bridge has capacity
   - Fetch: bridge readable-stream pulls/cancellation into the same producer-backed stream model
6. **Represent strict bodies as ordinary strict streams** and streamed bodies as ordinary producer-backed streams, preserving the “one body type” philosophy.

### Candidate API Shape (blocks)

> Exact naming can change, but the plan assumes this minimal capability set in `zio-blocks`:

```scala
trait ProducerSink[-E, -A] {
  def emit(chunk: zio.blocks.chunk.Chunk[A]): Unit
  def fail(error: E): Unit
  def end(): Unit
}

object zio.blocks.streams.Stream {
  def fromProducer[E, A](
    register: ProducerSink[E, A] => CancelToken,
    knownLength: Option[Long] = None,
  ): Stream[E, A]
}

trait CancelToken {
  def cancel(): Unit
}
```

**Behavior contract**:
- `fromProducer` must create a normal `Stream[E, A]`
- consumer cancellation must call `CancelToken.cancel()` exactly once
- `knownLength` must populate `stream.knownLength`
- strict/array/chunk streams remain unchanged
- no body/stream identity side-channel required in zio-http

### Candidate Internal Strategy (blocks)

The likely internal implementation strategy is a **bounded queue / channel rendezvous** inside `Stream.fromProducer`:
- producer callback offers chunks/errors/end markers into an internal bounded buffer
- stream consumer pulls from that buffer through the existing stream machinery
- cancellation closes the buffer and runs cancel token
- no producer call may block the transport thread indefinitely
- if true blocking is impossible, the bridge must define a bounded buffering + explicit demand/cancel policy

This follows the same proven shape seen in fs2/http4s and Pekko/Akka HTTP.

### Definition of Done
- [ ] `zio-blocks` exposes the new stream capability and it is used by `zio-http` instead of `WeakHashMap[Body, AsyncBodyState]`
- [ ] All JVM zio-http modules compile and streaming-related tests pass
- [ ] No netty/fetch async body path depends on body identity side channels
- [ ] `NettyRequestEncoder` does not eagerly materialize non-strict bodies on the hot path
- [ ] Streaming bodies preserve cancellation, completion, mid-stream failure, and connection reuse semantics

### Must Have
- Preserve single-body-type philosophy
- Solve async/streaming at the stream layer, not by reintroducing body subclasses
- Keep event-loop paths non-blocking
- Make failure modes explicit instead of silent degradation
- Minimize API surface added to blocks to the smallest capability set that fully solves transport integrations

### Must NOT Have (Guardrails)
- Do NOT implement a second long-lived push-stream abstraction inside zio-http separate from blocks
- Do NOT keep `WeakHashMap[Body, AsyncBodyState]` as the final architecture
- Do NOT redesign the entire blocks `Stream` API beyond what transport async bridging requires
- Do NOT silently materialize or buffer streaming bodies in hot paths just to preserve compile compatibility
- Do NOT conflate unrelated HTTP ergonomics issues with this body/stream architecture effort

---

## Verification Strategy (MANDATORY)

> **ZERO HUMAN INTERVENTION** - ALL verification is agent-executed. No exceptions.

### Test Decision
- **Infrastructure exists**: YES
- **Automated tests**: Tests-after
- **Framework**: Mill / existing Scala test suites

### QA Policy
Every task includes agent-executed QA scenarios. Evidence saved to `.sisyphus/evidence/task-{N}-{scenario-slug}.{ext}`.

- **Library/Module**: Use Mill compile/test commands and targeted JVM/JS module tests
- **Netty/HTTP behavior**: Use existing streaming/client/server specs where possible; add focused regression tests where absent
- **CLI/build verification**: Use bash with Mill commands and captured outputs

---

## Execution Strategy

### Parallel Execution Waves

Wave 1 (Start Immediately - research codification + API design):
├── Task 1: Define blocks stream bridge requirements and invariants [deep]
├── Task 2: Audit all current zio-http async body integration points [quick]
├── Task 3: Identify minimal blocks metadata/finalizer needs [deep]
├── Task 4: Define strict-vs-stream materialization rules for zio-http [quick]
└── Task 5: Define verification matrix for edge cases [writing]

Wave 2 (After Wave 1 - blocks implementation, MAX PARALLEL):
├── Task 6: Implement `Stream.fromProducer` push/async bridge in blocks streams [deep]
├── Task 7: Implement stream metadata/finalizer support needed by transport integrations [deep]
├── Task 8: Add blocks tests for async push, completion, failure, cancellation [unspecified-high]
├── Task 9: Validate thin-stream semantics for chunk/array/stream equivalence [unspecified-high]
└── Task 10: Document new blocks stream capability contract in draft/notes [writing]

Wave 3 (After Wave 2 - zio-http migration):
├── Task 11: Refactor netty response async body path off WeakHashMap [deep]
├── Task 12: Refactor netty request body encoding to avoid unsafe materialization [quick]
├── Task 13: Refactor fetch client body bridging onto blocks stream capability [deep]
├── Task 14: Remove or isolate side-channel fallback logic and loud-fail unsupported paths [unspecified-high]
├── Task 15: Fix explicitly-listed eager body inspection hot paths [quick]
└── Task 16: Restore/optimize header/status regression points if still needed [quick]

Wave 4 (After Wave 3 - tests and regression coverage):
├── Task 17: Add netty streaming regression tests [unspecified-high]
├── Task 18: Add request streaming/hot-path materialization regression tests [unspecified-high]
├── Task 19: Add fetch client streaming regression tests [unspecified-high]
├── Task 20: Add cancellation / partial-read / connection-reuse tests [deep]
└── Task 21: Run full module compile/test matrix and fix fallout [deep]

Wave FINAL (After ALL tasks):
├── Task F1: Plan compliance audit (oracle)
├── Task F2: Code quality and build review (unspecified-high)
├── Task F3: Real QA of streaming scenarios (unspecified-high)
└── Task F4: Scope fidelity check (deep)

Critical Path: 1 → 6 → 11 → 17 → 21 → F1-F4
Parallel Speedup: ~60% faster than sequential
Max Concurrent: 5

### Dependency Matrix

- **1**: - - 6, 7, 11, 13, 2
- **2**: - - 11, 12, 13, 14, 15, 1
- **3**: - - 7, 11, 13, 16, 1
- **4**: - - 12, 14, 15, 1
- **5**: - - 17, 18, 19, 20, 1
- **6**: 1 - 8, 9, 11, 12, 13, 14, 15, 17, 18, 19, 20, 2
- **7**: 1, 3 - 8, 11, 13, 20, 2
- **8**: 6, 7 - 21, 2
- **9**: 6 - 21, 2
- **10**: 1, 6, 7 - 21, 2
- **11**: 1, 2, 3, 6, 7 - 17, 20, 21, 3
- **12**: 2, 4, 6 - 18, 21, 3
- **13**: 1, 2, 3, 6, 7 - 19, 21, 3
- **14**: 2, 4, 6 - 21, 3
- **15**: 2, 4, 6 - 21, 3
- **16**: 3 - 21, 3
- **17**: 5, 6, 11 - 21, 4
- **18**: 5, 6, 12 - 21, 4
- **19**: 5, 6, 13 - 21, 4
- **20**: 5, 7, 11 - 21, 4
- **21**: 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20 - F1, F2, F3, F4, 4

### Agent Dispatch Summary

- **1**: **5** - T1 → `deep`, T2 → `quick`, T3 → `deep`, T4 → `quick`, T5 → `writing`
- **2**: **5** - T6 → `deep`, T7 → `deep`, T8 → `unspecified-high`, T9 → `unspecified-high`, T10 → `writing`
- **3**: **6** - T11 → `deep`, T12 → `quick`, T13 → `deep`, T14 → `unspecified-high`, T15 → `quick`, T16 → `quick`
- **4**: **5** - T17-T19 → `unspecified-high`, T20 → `deep`, T21 → `deep`
- **FINAL**: **4** - F1 → `oracle`, F2 → `unspecified-high`, F3 → `unspecified-high`, F4 → `deep`

---

## TODOs

- [ ] 1. Define blocks stream bridge requirements and invariants

  **What to do**:
  - Write the exact invariants the new blocks stream capability must satisfy: non-blocking producer interaction, single-body-type semantics, explicit completion/failure, cancellation, optional known-length, and no identity side-channel.
  - Define the minimum API surface needed in blocks (constructor/bridge + metadata/finalizer semantics), not a broad stream redesign.
  - Decide whether stream kind introspection is needed or whether metadata + bridge semantics are sufficient.

  **Must NOT do**:
  - Do not redesign unrelated stream combinators.
  - Do not reintroduce body subclasses.

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: architectural problem framing with invariants and API boundary choices.
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: 6, 7, 11, 13
  - **Blocked By**: None

  **References**:
  - `.sisyphus/drafts/blocks-stream-netty-analysis.md` - Consolidated research findings and user direction.
  - `/Users/nabil_abdel-hafeez/zio-repos/zio-blocks/http-model/shared/src/main/scala/zio/http/Body.scala` - Current body model and metadata surface.
  - `/Users/nabil_abdel-hafeez/zio-repos/zio-blocks/streams/` - Current stream model to extend.

  **Acceptance Criteria**:
  - [ ] A written invariant set exists and names the minimum required blocks capabilities.
  - [ ] The chosen API direction preserves “one body type only” semantics.

  **QA Scenarios**:
  ```
  Scenario: Requirements document is concrete enough to implement
    Tool: Bash
    Preconditions: Draft and plan files exist
    Steps:
      1. Read the generated requirements notes
      2. Verify they explicitly define producer bridge, completion, failure, cancellation, known-length, and non-blocking invariants
      3. Confirm no body subclassing requirement appears
    Expected Result: All invariants are explicitly listed and unambiguous
    Evidence: .sisyphus/evidence/task-1-requirements.txt

  Scenario: Scope guard against stream redesign
    Tool: Bash
    Preconditions: Requirements document exists
    Steps:
      1. Search requirements text for unrelated stream redesign proposals
      2. Confirm only minimal bridge/metadata/finalizer capabilities are in scope
    Expected Result: Scope remains tightly focused on transport async bridging
    Evidence: .sisyphus/evidence/task-1-scope.txt
  ```

- [ ] 2. Audit all current zio-http async body integration points

  **What to do**:
  - Enumerate all files and functions in zio-http that currently rely on async body side channels, `knownChunk` heuristics, or eager materialization assumptions for HTTP bodies.
  - Produce a bounded list of in-scope call sites for this effort.

  **Must NOT do**:
  - Do not expand into a whole-repo unrelated body audit.

  **Recommended Agent Profile**:
  - **Category**: `quick`
    - Reason: targeted code inventory.
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: 11, 12, 13, 14, 15
  - **Blocked By**: None

  **References**:
  - `zio-http-netty-core/src/main/scala/zio/http/netty/NettyBody.scala`
  - `zio-http-netty-core/src/main/scala/zio/http/netty/NettyBodyWriter.scala`
  - `zio-http-netty-client/src/main/scala/zio/http/netty/client/NettyRequestEncoder.scala`
  - `zio-http-fetch-client/js/src/main/scala/zio/http/internal/FetchBody*.scala`
  - `zio-http-client/shared/src/main/scala/zio/http/ZClientAspect.scala`
  - `zio-http-core/shared/src/main/scala/zio/http/HandlerAspect.scala`

  **Acceptance Criteria**:
  - [ ] In-scope file list is explicit and finite.
  - [ ] Each call site is classified: migrate / guard / exclude.

  **QA Scenarios**:
  ```
  Scenario: Audit list covers all known critical paths
    Tool: Bash
    Preconditions: Inventory document exists
    Steps:
      1. Compare audit list against known critical files from draft
      2. Verify netty request, netty response, fetch, and eager inspection paths are listed
    Expected Result: No critical path is missing
    Evidence: .sisyphus/evidence/task-2-audit.txt

  Scenario: Audit scope is bounded
    Tool: Bash
    Preconditions: Inventory document exists
    Steps:
      1. Check that each listed file has a stated reason
      2. Confirm unrelated files are excluded
    Expected Result: Clear, bounded migration scope
    Evidence: .sisyphus/evidence/task-2-boundary.txt
  ```

- [ ] 3. Identify minimal blocks metadata and finalizer needs

  **What to do**:
  - Specify how producer-backed streams attach known-length and cleanup/finalizer semantics.
  - Decide exact lifecycle events: completion, consumer cancellation, transport error, early abandonment.

  **Must NOT do**:
  - Do not invent general-purpose metadata unrelated to transport bodies.

  **Recommended Agent Profile**:
  - **Category**: `deep`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: 7, 11, 13, 16
  - **Blocked By**: None

  **References**:
  - `/Users/nabil_abdel-hafeez/zio-repos/zio-blocks/http-model/shared/src/main/scala/zio/http/Body.scala`
  - `/Users/nabil_abdel-hafeez/zio-repos/zio-blocks/scope/shared/src/main/scala/zio/blocks/scope/Resource.scala`
  - `/Users/nabil_abdel-hafeez/zio-repos/zio-blocks/scope/shared/src/main/scala/zio/blocks/scope/Scope.scala`
  - External queue/channel bridge patterns summarized in draft

  **Acceptance Criteria**:
  - [ ] Known-length semantics for producer-backed streams are defined.
  - [ ] Finalizer/cancellation contract is explicitly documented.

  **QA Scenarios**:
  ```
  Scenario: Metadata contract handles Content-Length and unknown length
    Tool: Bash
    Preconditions: Metadata notes exist
    Steps:
      1. Read the metadata contract
      2. Verify both known-length and unknown-length producer streams are covered
    Expected Result: Both cases have explicit rules
    Evidence: .sisyphus/evidence/task-3-length.txt

  Scenario: Finalizer contract handles cancellation and completion
    Tool: Bash
    Preconditions: Lifecycle notes exist
    Steps:
      1. Read lifecycle contract
      2. Verify completion, cancellation, and failure each have explicit cleanup semantics
    Expected Result: No lifecycle state is unspecified
    Evidence: .sisyphus/evidence/task-3-lifecycle.txt
  ```

- [ ] 4. Define strict-versus-stream materialization rules for zio-http

  **What to do**:
  - Establish the exact rule for when zio-http may eagerly materialize a body (`knownChunk`/strict body only) versus when it must preserve streaming.
  - Apply the rule consistently to request encoding, response writing, logging/aspects, and helper conversions.

  **Must NOT do**:
  - Do not rely on `length <= smallThreshold` alone as a proxy for safe strictness.

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: 12, 14, 15
  - **Blocked By**: None

  **References**:
  - `zio-http-netty-client/.../NettyRequestEncoder.scala`
  - `zio-http-netty-core/.../NettyBodyWriter.scala`
  - `zio-http-netty-server/.../NettyResponseEncoder.scala`
  - `zio-http-client/.../ZClientAspect.scala`
  - `zio-http-core/.../HandlerAspect.scala`

  **Acceptance Criteria**:
  - [ ] A single strictness rule exists and is reused across all in-scope call sites.
  - [ ] Unsafe eager materialization is explicitly disallowed for streaming bodies.

  **QA Scenarios**:
  ```
  Scenario: Strictness rule rejects non-strict known-length streams
    Tool: Bash
    Preconditions: Strictness rule document exists
    Steps:
      1. Read the rule
      2. Verify a body with known length but no known chunk is treated as streaming
    Expected Result: The rule prevents unsafe `toArray` on known-length streams
    Evidence: .sisyphus/evidence/task-4-strictness.txt

  Scenario: Rule covers logging/aspect paths too
    Tool: Bash
    Preconditions: Strictness rule document exists
    Steps:
      1. Check referenced call sites
      2. Verify aspects/helpers are covered, not just netty encoder paths
    Expected Result: No in-scope eager materialization path is omitted
    Evidence: .sisyphus/evidence/task-4-coverage.txt
  ```

- [ ] 5. Define verification matrix for edge cases

  **What to do**:
  - Create the test matrix covering empty body, strict body, streaming body, partial read, mid-stream failure, cancellation, keep-alive reuse, multipart interaction, and fetch-body behavior.

  **Must NOT do**:
  - Do not leave critical edge cases implicit.

  **Recommended Agent Profile**:
  - **Category**: `writing`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: 17, 18, 19, 20
  - **Blocked By**: None

  **References**:
  - Metis gap analysis edge cases
  - Existing streaming/client/server specs in zio-http tests

  **Acceptance Criteria**:
  - [ ] All identified edge cases are represented in a test matrix.
  - [ ] Each edge case maps to a concrete future regression test.

  **QA Scenarios**:
  ```
  Scenario: Matrix includes all critical edge cases
    Tool: Bash
    Preconditions: Matrix file exists
    Steps:
      1. Read the matrix
      2. Verify empty, strict, streaming, cancellation, partial read, failure, keep-alive, and multipart are all present
    Expected Result: No critical edge case is missing
    Evidence: .sisyphus/evidence/task-5-matrix.txt

  Scenario: Matrix is testable
    Tool: Bash
    Preconditions: Matrix file exists
    Steps:
      1. Check that each row maps to a specific module/spec area
      2. Confirm each row has expected observable behavior
    Expected Result: Matrix can be translated directly into tests
    Evidence: .sisyphus/evidence/task-5-testability.txt
  ```

- [ ] 6. Implement `Stream.fromProducer` push/async bridge in blocks streams

  **What to do**:
  - Add the new producer-backed stream constructor in the `zio-blocks` repo.
  - Implement the bridge so a producer can emit chunks, fail, and complete into a normal blocks stream.
  - Ensure the stream integrates with existing pull-based consumption rather than creating a second stream abstraction.
  - Use a bounded internal rendezvous structure so producer and consumer are decoupled without transport-thread blocking.

  **Must NOT do**:
  - Do not create a parallel “async stream” type.
  - Do not require `Body` changes beyond consuming the new stream capability.

  **Recommended Agent Profile**:
  - **Category**: `deep`
    - Reason: core cross-library architectural implementation.
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: 8, 9, 11, 12, 13, 14, 15, 17, 18, 19, 20
  - **Blocked By**: 1

  **References**:
  - `/Users/nabil_abdel-hafeez/zio-repos/zio-blocks/streams/` - target implementation area
  - External queue/channel/subsource bridge patterns summarized in draft
  - `zio-http-netty-core/src/main/scala/zio/http/netty/AsyncBodyReader.scala` - current producer semantics zio-http needs to preserve

  **Acceptance Criteria**:
  - [ ] `zio-blocks` exposes a concrete producer-backed stream constructor
  - [ ] Producer can emit multiple chunks, complete, and fail into the stream
  - [ ] Consumer cancellation triggers the registered cancel/finalizer logic
  - [ ] No identity side-channel is required to consume the stream

  **QA Scenarios**:
  ```
  Scenario: Producer emits two chunks then completes
    Tool: Bash
    Preconditions: blocks tests exist for new producer bridge
    Steps:
      1. Run the targeted blocks stream test for emit/emit/end
      2. Assert collected output equals concatenated chunks in order
    Expected Result: Stream consumer sees all chunks in order and then completion
    Evidence: .sisyphus/evidence/task-6-producer-complete.txt

  Scenario: Producer fails mid-stream
    Tool: Bash
    Preconditions: blocks tests exist for producer failure
    Steps:
      1. Run targeted test where producer emits one chunk then fails
      2. Assert consumer receives first chunk and then stream failure
    Expected Result: Failure propagates correctly without hanging
    Evidence: .sisyphus/evidence/task-6-producer-fail.txt
  ```

- [ ] 7. Implement stream metadata/finalizer support needed by transport integrations

  **What to do**:
  - Ensure producer-backed streams can carry `knownLength` when transport provides it.
  - Ensure cancellation/completion/failure all trigger cleanup exactly once.
  - If needed, add minimal stream-internal lifecycle hooks to support this contract.

  **Must NOT do**:
  - Do not add generic metadata machinery beyond what transport bodies need.

  **Recommended Agent Profile**:
  - **Category**: `deep`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: 8, 11, 13, 20
  - **Blocked By**: 1, 3

  **References**:
  - `/Users/nabil_abdel-hafeez/zio-repos/zio-blocks/http-model/shared/src/main/scala/zio/http/Body.scala`
  - `/Users/nabil_abdel-hafeez/zio-repos/zio-blocks/scope/`
  - Netty/fetch transport requirements from draft

  **Acceptance Criteria**:
  - [ ] `knownLength` works for producer-backed streams when provided
  - [ ] Cleanup runs on completion, failure, and cancellation
  - [ ] Cleanup is idempotent / exactly-once by contract

  **QA Scenarios**:
  ```
  Scenario: Known length is visible without consuming the stream
    Tool: Bash
    Preconditions: blocks metadata test exists
    Steps:
      1. Run targeted test creating producer-backed stream with knownLength=123
      2. Assert stream.knownLength == Some(123)
    Expected Result: known length metadata survives stream construction
    Evidence: .sisyphus/evidence/task-7-known-length.txt

  Scenario: Cancellation runs cleanup exactly once
    Tool: Bash
    Preconditions: blocks cancellation/finalizer test exists
    Steps:
      1. Start consuming producer-backed stream
      2. Cancel consumer before completion
      3. Assert cancel/finalizer callback fired once
    Expected Result: Cleanup fires once with no leak or double-run
    Evidence: .sisyphus/evidence/task-7-cancel.txt
  ```

- [ ] 8. Add blocks tests for async push, completion, failure, cancellation

  **What to do**:
  - Add tests in `zio-blocks` covering the new producer-backed stream constructor.
  - Cover empty stream, single chunk, multi-chunk, fail-before-any-data, fail-after-data, completion, and cancellation.

  **Must NOT do**:
  - Do not rely only on compile-time tests.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: 21
  - **Blocked By**: 6, 7

  **References**:
  - New producer bridge implementation in zio-blocks
  - Edge-case matrix from Task 5

  **Acceptance Criteria**:
  - [ ] Tests exist for push, complete, fail, cancel, and empty cases
  - [ ] Tests prove no hidden side-channel is needed

  **QA Scenarios**:
  ```
  Scenario: Full producer bridge test suite passes
    Tool: Bash
    Preconditions: blocks stream tests added
    Steps:
      1. Run the targeted blocks stream test suite
      2. Verify all producer-bridge scenarios pass
    Expected Result: Producer-backed stream behavior is fully covered and green
    Evidence: .sisyphus/evidence/task-8-blocks-tests.txt

  Scenario: Empty producer-backed stream behaves like empty chunk
    Tool: Bash
    Preconditions: empty-stream test exists
    Steps:
      1. Run empty producer-backed stream test
      2. Assert collected output is empty and length is zero/known when applicable
    Expected Result: Empty body semantics match empty chunk semantics
    Evidence: .sisyphus/evidence/task-8-empty.txt
  ```

- [ ] 9. Validate thin-stream semantics for chunk/array/stream equivalence

  **What to do**:
  - Prove the user-facing semantics remain consistent across `Stream.fromChunk`, `Stream.fromArray`, and the new producer-backed stream path.
  - Specifically validate empty-body equivalence and safe strictness detection behavior.

  **Must NOT do**:
  - Do not accept semantic divergence between strict and streamed bodies for the same bytes.

  **Recommended Agent Profile**:
  - **Category**: `unspecified-high`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: 21
  - **Blocked By**: 6

  **References**:
  - User philosophy captured in Context
  - `Body.scala` in zio-blocks

  **Acceptance Criteria**:
  - [ ] Empty producer-backed stream is equivalent to empty chunk-backed body
  - [ ] Strict-body detection remains correct and does not accidentally classify producer streams as strict

  **QA Scenarios**:
  ```
  Scenario: Empty semantics match exactly
    Tool: Bash
    Preconditions: equivalence tests exist
    Steps:
      1. Compare empty chunk-backed body with empty producer-backed body
      2. Assert same emptiness, same collected bytes, same externally observable behavior
    Expected Result: Empty is empty regardless of source construction path
    Evidence: .sisyphus/evidence/task-9-empty-equivalence.txt

  Scenario: Strict and streamed representations preserve byte equality
    Tool: Bash
    Preconditions: equivalence tests exist
    Steps:
      1. Build same payload via chunk/array and producer-backed stream
      2. Collect both and compare bytes
    Expected Result: Equivalent payloads produce identical output bytes
    Evidence: .sisyphus/evidence/task-9-byte-equivalence.txt
  ```

- [ ] 10. Document new blocks stream capability contract in notes

  **What to do**:
  - Write the intended contract for `fromProducer` (or final chosen name), including backpressure expectations, cancellation semantics, known-length behavior, and intended use by transport integrations.

  **Must NOT do**:
  - Do not leave the contract implicit in tests only.

  **Recommended Agent Profile**:
  - **Category**: `writing`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: 21
  - **Blocked By**: 6, 7

  **References**:
  - New blocks implementation and tests
  - External bridge patterns summarized in draft

  **Acceptance Criteria**:
  - [ ] Contract clearly states what producer threads may do and what consumers may assume
  - [ ] Contract covers completion, failure, cancellation, and known-length behavior

  **QA Scenarios**:
  ```
  Scenario: Contract is implementation-aligned
    Tool: Bash
    Preconditions: notes exist
    Steps:
      1. Read notes and compare against implementation/tests
      2. Verify no documented behavior contradicts tests
    Expected Result: Notes accurately describe the implemented bridge contract
    Evidence: .sisyphus/evidence/task-10-contract.txt

  Scenario: Contract is transport-usable
    Tool: Bash
    Preconditions: notes exist
    Steps:
      1. Check notes for explicit netty/fetch integration expectations
      2. Verify they are concrete enough for zio-http migration tasks
    Expected Result: zio-http tasks can implement against the documented contract
    Evidence: .sisyphus/evidence/task-10-transport.txt
  ```

---

## Final Verification Wave (MANDATORY — after ALL implementation tasks)

- [ ] F1. **Plan Compliance Audit** — `oracle`
  Verify blocks-first architecture was followed, no body side-channel remains on final async paths, and deliverables match plan.

- [ ] F2. **Code Quality Review** — `unspecified-high`
  Run compile/tests and review changed files for hidden fallback buffering, identity hacks, and transport-thread blocking behavior.

- [ ] F3. **Real Manual QA** — `unspecified-high`
  Execute streaming request/response scenarios, cancellation scenarios, and fetch client scenarios with evidence capture.

- [ ] F4. **Scope Fidelity Check** — `deep`
  Ensure work stayed focused on body/stream architecture and explicitly listed regression points.

---

## Commit Strategy

- **1**: `feat(streams): add async producer bridge for transport bodies`
- **2**: `refactor(netty): migrate async body handling to blocks streams`
- **3**: `fix(client): avoid eager materialization of streaming bodies`
- **4**: `test(streaming): add body regression coverage`

---

## Success Criteria

### Verification Commands
```bash
./mill "core.jvm[2.13.18].compile"               # Expected: SUCCESS
./mill "server.jvm[2.13.18].compile"             # Expected: SUCCESS
./mill "client.jvm[2.13.18].compile"             # Expected: SUCCESS
./mill "endpoint.jvm[2.13.18].compile"           # Expected: SUCCESS
./mill "nettyCore.jvm[2.13.18].compile"          # Expected: SUCCESS
./mill "nettyServer.jvm[2.13.18].compile"        # Expected: SUCCESS
./mill "nettyClient.jvm[2.13.18].compile"        # Expected: SUCCESS
./mill "testkit.jvm[2.13.18].compile"            # Expected: SUCCESS
./mill "fetchClient.js[2.13.18].compile"         # Expected: SUCCESS
```

### Final Checklist
- [ ] Async transport bodies are represented as normal blocks streams
- [ ] No final architecture depends on body-identity side channels
- [ ] Request hot paths avoid unsafe eager materialization
- [ ] Streaming cancellation/completion/failure semantics are preserved
- [ ] Empty body semantics remain equivalent to empty chunk
- [ ] Body remains a single type; stream solves the complexity
