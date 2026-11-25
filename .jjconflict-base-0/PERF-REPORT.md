# ZIO HTTP 4 — Perf Fix A: writer busy-poll → park/signal (with proof)

Status: **implemented + verified** in jj workspace `zio-http-v4-perf` (not committed/pushed).
Date: 2026-11-24 · JDK 25.0.4.1 LTS · 12 cores / 47 GB · Scala 3.9.0.

## 1. What changed

`zio-http-server-loom/.../h2/H2Connection.scala` — the per-connection writer virtual thread no
longer polls with `Thread.sleep(1)`:

- **Before:** `parkWriter()` = `Thread.sleep(1L)` in a loop ⇒ each *idle* connection woke
  ~1000×/s and re-scanned `activeStreams`.
- **After:** `parkWriter()` = `LockSupport.park()`; the writer is unparked on
  (a) any outbound enqueue via a `SignallingStream` wrapper (`send` → `wakeWriter()`), and
  (b) `shutdown()` → `wakeWriter()`. The `clear → re-check → park` sequence avoids lost wake-ups
  (a signal between clear and park leaves a `LockSupport` permit). A 1 s backstop is deliberately
  **not** used, since at 1M connections even 1 wake/s/conn would reproduce the O(connections) cost.

No queue semantics change: HEADERS still write directly (`writeHeadersDirect`); DATA still goes
through the mux outbound queue and is drained by the writer — the wrapper only adds a signal.
All stream state stays in the underlying `MuxStream`; wrapping is transparent.

## 2. Method (reproducible)

In-process harness `zio-http-server-loom/src/test/scala/zio/http/h2/PerfIdle.scala` spawns N
real `H2Connection`s over fake streams (preface+SETTINGS, then blocked read), isolating
per-connection cost without network noise. CPU from `OperatingSystemMXBean.getProcessCpuTime`;
allocation/GC from JFR + `GarbageCollectorMXBean`.

```bash
CP=$(./mill show 'serverLoom.jvm[3.9.0].test.runClasspath' | grep -o '"[^"]*"' \
      | sed -E 's#.*:(/.*)"#\1#' | grep '^/' | paste -sd:)      # saved: target/logs/perf-classpath.txt

# idle benchmark (N connections, S seconds)
java -cp "$CP" zio.http.h2.PerfIdle idle 1000 8

# allocation profile (small heap so GC is visible) + JFR
java -Xmx256m -Xlog:gc:file=/tmp/gc-base.log \
     -XX:StartFlightRecording=settings=profile,filename=/tmp/feed-base.jfr,dumponexit=true \
     -cp "$CP" zio.http.h2.PerfIdle feed 10 20000
jfr view allocation-by-site /tmp/feed-base.jfr
```

Logs: `target/logs/perf-idle-baseline.log`, `perf-idle-fixA.log`, `perf-fixA-tests.log`,
`perf-classpath.txt`.

## 3. Result — idle CPU

| build | conns | window | process CPU | cores | per idle conn | extrapolate 1M |
|---|---:|---:|---:|---:|---:|---:|
| **baseline** (`Thread.sleep(1)`) | 1000 | 8 s | **14.550 s** | **1.82** | **1.82 mcores** | **≈1819 cores** |
| **Fix A** (`LockSupport` park)   | 1000 | 8 s | **0.000 s** | **≈0** | **≈0** | **≈0 (noise floor)** |

Reduction: **> 99.99 %**; idle writer CPU goes from O(connections) to O(1).

```
baseline: mode=idle conns=1000 secs=8 cpu_seconds=14.550 cpu_per_conn_millicores=1.8188
fix A   : mode=idle conns=1000 secs=8 cpu_seconds=0.000  cpu_per_conn_millicores=0.0000
```

## 4. Correctness / regression evidence

`./mill 'serverLoom.jvm[3.9.0].test.testCached'` → **271 tests passed, 0 failed, 0 ignored**
(57.5 s). This includes the DATA/streaming/flow-control/SSE/TLS specs that exercise the writer
end-to-end (`StreamingEdgeSpec`, `StreamingBodySpec`, `SseEndToEndSpec`, `H2FlowControlSpec`,
`H2TlsSpec`, `IdleTimeoutGoAwaySpec`, …) — i.e., the park/signal path delivers bodies correctly.

## 5. Profiling finding — "Fix B" (8 KB per-read array) is low-ROI

Static review suggested `appendInput` allocates `new Array[Byte](8192)` per read. JFR
`allocation-by-site` (200k frames) shows:

```
ConcurrentHashMap.initTable()        38.41%   (one-time)
H2Connection.appendInput()           22.45%
java.util.Arrays.copyOf(byte[],int)  17.37%
...
GC: 1 young GC, 48.1 MB before (≈240 B/frame)
```

The 8 KB array is **JIT escape-analysed away** (monomorphic stream in the harness, and the
plaintext path is effect-steady per server); real per-frame allocation is ~240 B, dominated by
`copyOf` + `Chunk` concat + the PING ACK encode. So removing the 8 KB array would not measurably
help — **don't pursue without a real-socket profile showing otherwise.** (This is exactly the
value of measuring: it corrected the static estimate.)

## 6. Remaining per-idle-connection cost (next targets)

Each idle connection still holds **3 VTs**: connection VT (parked read), writer VT (now parked,
~0 CPU), and a **per-connection idle-timer VT** (`zio-http-h2-idle-timeout`, 60 s sleep). At 1M
connections that is ~3M VTs of heap/stack. Next, in impact order:

1. **Shared timer wheel** for idle/request/header/drain deadlines → removes the per-connection
   timer VT (VT/conn 3 → 2; lower heap). Provable via VT dump (`jcmd Thread.dump_to_file -format=json`)
   and heap/conn.
2. **Remove the writer VT** for HTTP/1.1 (write on the connection VT) → VT/conn 2 → 1 for H1;
   for H2 keep a parked writer but consider direct-under-`writeLock` writes for small bodies.
3. **Real-socket validation** with `H2Soak` (idle) + `h2load` (DATA latency) from
   `/home/user/ziohttp4-measure`, to confirm the in-process result and catch the 0–1 ms DATA
   latency floor that the old poll also imposed.

## 7. Files touched (working copy, uncommitted)

```
zio-http-server-loom/src/main/scala/zio/http/h2/H2Connection.scala   (Fix A)
zio-http-server-loom/src/test/scala/zio/http/h2/PerfIdle.scala       (harness)
```

Diff summary: +55/−6 in `H2Connection.scala` (fields, `run`, `openStream`, `SignallingStream`,
`parkWriter`, `wakeWriter`, `shutdown`), +125 harness.
