# Blocks-Only Stream Producer Bridge

## TL;DR

> **Quick Summary**: Add a producer-backed stream capability to `zio-blocks` so push-driven transports can be represented as normal streams without body subclassing or side-channel state.
>
> **Deliverables**:
> - `Stream.fromProducer` (or final chosen name) in `zio-blocks`
> - producer completion/failure/cancellation semantics
> - known-length support for producer-backed streams
> - blocks test coverage for async push behavior
> - contract notes for downstream integrations
>
> **Estimated Effort**: Medium
> **Parallel Execution**: YES - 3 waves
> **Critical Path**: API contract → implementation → tests → verification

---

## Context

### Original Request
Create a handoff plan for an agent working only on `zio-blocks` as an isolated issue.

### Interview Summary
**Key Discussions**:
- The core problem should be solved in `zio-blocks`, not by growing local workarounds in `zio-http`.
- The intended philosophy is: **stream is thin enough that chunk/array/collection/async source differences should not matter at user level**.
- Therefore there should remain **one body type only**, and empty body should behave the same as empty chunk.

**Research Findings**:
- `zio.http.Body` in blocks is a final wrapper over `Stream`.
- Current `Stream` lacks a first-class push/producer-backed construction path.
- Downstream HTTP integrations need explicit support for producer emission, failure, completion, cancellation, and known-length metadata.
- External ecosystems solve this with queue/channel/subsource bridges rather than body subclassing or hidden identity side channels.

---

## Work Objectives

### Core Objective
Implement a producer-backed stream construction capability in `zio-blocks` that preserves the existing single-stream/single-body philosophy while making push-driven transport integration possible.

### Repository Boundary
- **Target repo**: `/Users/nabil_abdel-hafeez/zio-repos/zio-blocks`

### Concrete Deliverables
- New producer-backed stream constructor in `zio-blocks`
- Explicit producer event contract: emit / fail / end
- Cancellation/finalizer contract
- Known-length metadata support for producer-backed streams
- Test suite for push-stream behavior
- Notes documenting the contract for downstream users (e.g. zio-http)

### Proposed Fix Design

The plan is to add a minimal capability like:

```scala
trait ProducerSink[-E, -A] {
  def emit(chunk: zio.blocks.chunk.Chunk[A]): Unit
  def fail(error: E): Unit
  def end(): Unit
}

trait CancelToken {
  def cancel(): Unit
}

object zio.blocks.streams.Stream {
  def fromProducer[E, A](
    register: ProducerSink[E, A] => CancelToken,
    knownLength: Option[Long] = None,
  ): Stream[E, A]
}
```

**Expected semantics**:
- yields a normal `Stream[E, A]`
- producer can emit many chunks over time
- producer can fail or complete exactly once
- consumer cancellation triggers `CancelToken.cancel()` exactly once
- `knownLength` populates `stream.knownLength`
- empty producer stream is semantically equivalent to empty chunk stream

**Likely implementation shape**:
- internal bounded rendezvous/queue/channel bridge
- producer offers events into bridge
- consumer pulls through existing stream mechanics
- no second async-stream type introduced

### Definition of Done
- [ ] Producer-backed streams can be constructed without subclassing or side-channel attachment
- [ ] Producer-backed streams support emit/fail/end semantics correctly
- [ ] Cancellation and cleanup behavior is explicit and tested
- [ ] Known-length metadata works for producer-backed streams
- [ ] Empty producer streams behave like empty chunk streams

### Must Have
- Preserve the current single-stream abstraction
- Keep the API minimal and transport-focused
- Avoid hidden identity-based or GC-sensitive contracts
- Preserve thin-stream semantics for users

### Must NOT Have (Guardrails)
- Do NOT introduce a second long-lived async stream abstraction
- Do NOT redesign unrelated stream combinators
- Do NOT require body-type changes to express async producers
- Do NOT rely on undocumented side effects for cancellation or cleanup

---

## Verification Strategy (MANDATORY)

> **ZERO HUMAN INTERVENTION** - ALL verification is agent-executed.

### Test Decision
- **Infrastructure exists**: YES
- **Automated tests**: Tests-after
- **Framework**: Existing zio-blocks Scala test suite

### QA Policy
- Use targeted compile/test commands in the `zio-blocks` repo
- Add focused tests for emit/complete/fail/cancel/empty/known-length behavior

---

## Execution Strategy

### Parallel Execution Waves

Wave 1 (Design and contract):
├── Task 1: Define exact API contract for producer-backed streams [deep]
├── Task 2: Define cancellation and known-length semantics [deep]
└── Task 3: Define test matrix for producer-backed streams [writing]

Wave 2 (Implementation):
├── Task 4: Implement `Stream.fromProducer` bridge [deep]
├── Task 5: Implement lifecycle/finalizer support [deep]
└── Task 6: Implement known-length propagation for producer-backed streams [quick]

Wave 3 (Validation):
├── Task 7: Add tests for emit/complete/fail/empty [unspecified-high]
├── Task 8: Add tests for cancellation semantics [unspecified-high]
├── Task 9: Add tests for chunk/array/producer equivalence [unspecified-high]
└── Task 10: Document downstream usage contract [writing]

Wave FINAL:
├── Task F1: Plan compliance audit (oracle)
├── Task F2: Code quality review (unspecified-high)
├── Task F3: QA of producer-backed stream behavior (unspecified-high)
└── Task F4: Scope fidelity check (deep)

Critical Path: 1 → 4 → 7 → F1-F4

---

## TODOs

- [x] 1. Define exact API contract for producer-backed streams

  **What to do**:
  - Finalize the constructor and callback shape (`fromProducer`, `ProducerSink`, `CancelToken`, or equivalent).
  - Specify exactly-once semantics for end/fail/cancel.
  - Keep the API minimal and transport-focused.

  **Must NOT do**:
  - Do not broaden this into a general async-runtime redesign.

  **Recommended Agent Profile**:
  - **Category**: `deep`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: 4, 5, 6
  - **Blocked By**: None

  **References**:
  - `/Users/nabil_abdel-hafeez/zio-repos/zio-blocks/streams/`
  - `/Users/nabil_abdel-hafeez/zio-repos/zio-blocks/http-model/shared/src/main/scala/zio/http/Body.scala`

  **Acceptance Criteria**:
  - [ ] API contract is concrete enough to implement directly
  - [ ] Contract preserves single-stream semantics

  **QA Scenarios**:
  ```
  Scenario: Contract is concrete
    Tool: Bash
    Preconditions: notes exist
    Steps:
      1. Read the contract notes
      2. Verify constructor shape, producer events, and cancellation semantics are explicit
    Expected Result: No core API behavior remains ambiguous
    Evidence: .sisyphus/evidence/task-1-contract.txt

  Scenario: Contract is minimal
    Tool: Bash
    Preconditions: notes exist
    Steps:
      1. Review proposed API surface
      2. Confirm only producer bridge, cancellation, and known-length concerns are added
    Expected Result: No unrelated stream redesign appears
    Evidence: .sisyphus/evidence/task-1-minimal.txt
  ```

- [x] 2. Define cancellation and known-length semantics

  **What to do**:
  - Specify what happens on consumer cancellation, producer completion, producer failure, and empty streams.
  - Specify how `knownLength` behaves for producer-backed streams.

  **Must NOT do**:
  - Do not leave lifecycle behavior implicit.

  **Recommended Agent Profile**:
  - **Category**: `deep`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: 5, 6, 8
  - **Blocked By**: None

  **References**:
  - `/Users/nabil_abdel-hafeez/zio-repos/zio-blocks/scope/`
  - `/Users/nabil_abdel-hafeez/zio-repos/zio-blocks/http-model/shared/src/main/scala/zio/http/Body.scala`

  **Acceptance Criteria**:
  - [ ] Lifecycle contract covers completion, failure, and cancellation
  - [ ] Known-length semantics are explicit

  **QA Scenarios**:
  ```
  Scenario: Lifecycle semantics are complete
    Tool: Bash
    Preconditions: lifecycle notes exist
    Steps:
      1. Read lifecycle notes
      2. Verify completion, failure, cancellation, and empty stream cases are all defined
    Expected Result: No lifecycle state is unspecified
    Evidence: .sisyphus/evidence/task-2-lifecycle.txt

  Scenario: Known-length semantics are explicit
    Tool: Bash
    Preconditions: lifecycle notes exist
    Steps:
      1. Read known-length section
      2. Verify both Some(length) and None behavior are specified
    Expected Result: known-length behavior is unambiguous
    Evidence: .sisyphus/evidence/task-2-length.txt
  ```

- [x] 3. Define test matrix for producer-backed streams

  **What to do**:
  - Enumerate tests for empty, single chunk, multiple chunks, fail-before-data, fail-after-data, cancellation, and equivalence with strict sources.

  **Recommended Agent Profile**:
  - **Category**: `writing`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 1
  - **Blocks**: 7, 8, 9
  - **Blocked By**: None

  **Acceptance Criteria**:
  - [ ] Test matrix covers all critical edge cases

  **QA Scenarios**:
  ```
  Scenario: Matrix covers critical cases
    Tool: Bash
    Preconditions: matrix exists
    Steps:
      1. Read matrix
      2. Verify empty, multi-chunk, fail, cancel, and equivalence cases are present
    Expected Result: No critical producer-stream case is missing
    Evidence: .sisyphus/evidence/task-3-matrix.txt
  ```

- [x] 4. Implement `Stream.fromProducer` bridge

  **What to do**:
  - Add the producer-backed stream constructor in `zio-blocks`.
  - Implement the internal bridge so producer-side callbacks feed a normal stream.

  **Must NOT do**:
  - Do not introduce a second stream abstraction.

  **Recommended Agent Profile**:
  - **Category**: `deep`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: 7, 8, 9, 10
  - **Blocked By**: 1

  **References**:
  - `/Users/nabil_abdel-hafeez/zio-repos/zio-blocks/streams/`

  **Acceptance Criteria**:
  - [ ] Producer can emit many chunks and end/fail cleanly
  - [ ] Result is a normal `Stream[E, A]`

  **QA Scenarios**:
  ```
  Scenario: Multi-chunk producer completes
    Tool: Bash
    Preconditions: implementation and tests exist
    Steps:
      1. Run targeted test for emit/emit/end
      2. Assert collected output preserves order and completion
    Expected Result: Producer-backed stream behaves as normal stream
    Evidence: .sisyphus/evidence/task-4-producer.txt
  ```

- [x] 5. Implement lifecycle/finalizer support

  **What to do**:
  - Ensure producer-backed streams trigger cleanup on cancellation/failure/completion.

  **Recommended Agent Profile**:
  - **Category**: `deep`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: 8, 10
  - **Blocked By**: 1, 2

  **Acceptance Criteria**:
  - [ ] Cleanup runs exactly once

  **QA Scenarios**:
  ```
  Scenario: Cancellation triggers cleanup exactly once
    Tool: Bash
    Preconditions: implementation and tests exist
    Steps:
      1. Run targeted cancellation test
      2. Assert cancel/finalizer callback runs once
    Expected Result: Exactly-once cleanup semantics
    Evidence: .sisyphus/evidence/task-5-cancel.txt
  ```

- [x] 6. Implement known-length propagation

  **What to do**:
  - Ensure producer-backed streams can expose `knownLength` when provided.

  **Recommended Agent Profile**:
  - **Category**: `quick`
  - **Skills**: []

  **Parallelization**:
  - **Can Run In Parallel**: YES
  - **Parallel Group**: Wave 2
  - **Blocks**: 7, 9
  - **Blocked By**: 1, 2

  **Acceptance Criteria**:
  - [ ] `knownLength` is visible without consuming the stream

  **QA Scenarios**:
  ```
  Scenario: knownLength metadata survives construction
    Tool: Bash
    Preconditions: implementation and tests exist
    Steps:
      1. Run targeted known-length test
      2. Assert stream.knownLength == provided value
    Expected Result: known-length is preserved
    Evidence: .sisyphus/evidence/task-6-known-length.txt
  ```

- [x] 7. Add tests for emit/complete/fail/empty
- [x] 8. Add tests for cancellation semantics
- [x] 9. Add tests for chunk/array/producer equivalence
- [x] 10. Document downstream usage contract

---

## Final Verification Wave (MANDATORY)

- [x] F1. **Plan Compliance Audit** — `oracle`
- [x] F2. **Code Quality Review** — `unspecified-high`
- [x] F3. **Real QA of producer-backed stream behavior** — `unspecified-high`
- [x] F4. **Scope Fidelity Check** — `deep`

---

## Success Criteria

### Verification Commands
```bash
# Run in /Users/nabil_abdel-hafeez/zio-repos/zio-blocks
# Use the repo's actual Mill/SBT test commands for the touched modules
```

### Final Checklist
- [ ] Producer-backed streams are a normal stream path in blocks
- [ ] Empty producer streams behave like empty chunk streams
- [ ] No hidden side-channel contract is required
- [ ] Cancellation/failure/completion semantics are explicit and tested
