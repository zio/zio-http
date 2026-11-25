# ZIO HTTP 4 — Perf work: writer poll, idle timer, writer VT (with proof)

Workspace: `zio-http-v4-perf` (jj task workspace). JDK 25.0.4.1 LTS · 12 cores / 47 GB · Scala 3.9.0.
All numbers are process-level measurements from an in-process harness of real `H2Connection`s
(fake streams, no network noise) plus the full `serverLoom` test suite as the correctness gate.

## Summary of changes (one commit each)

| # | Commit | Change | Verified effect |
|---|---|---|---|
| 1 | `perf(server-loom): park writer instead of 1ms poll` | `parkWriter` `Thread.sleep(1)` → `LockSupport.park`, woken by `SignallingStream.send` / `shutdown` | idle CPU **1.82 cores → 0** for 1000 conns |
| 2 | `test(server-loom): in-process idle/write perf harness + report` | `PerfIdle` harness | reproducible benchmarks |
| 3 | `perf(server-loom): shared scheduler for idle deadlines` | per-conn idle-timer VT → 1 shared `ScheduledThreadPoolExecutor` | VTs/conn **3 → 2**; idle-timer VTs 500 → 0 |
| 4 | `perf(server-loom): write response DATA directly, remove per-conn writer VT` | DATA written under `writeLock` from the handler VT; writer VT + queue drain deleted | VTs/conn **2 → 1** |

Each commit: `271/271 serverLoom tests pass`.

## Headline numbers

### Idle CPU (1000 idle connections, 8 s)

| build | process CPU | cores | per idle conn | 1M extrapolation |
|---|---:|---:|---:|---:|
| baseline (`sleep(1)` poll) | 14.550 s | **1.82** | 1.82 mcores | ≈1,819 cores |
| after commit 1 | 0.000 s | **≈0** | ≈0 | ≈0 |

```
baseline: mode=idle conns=1000 secs=8 cpu_seconds=14.550 cpu_per_conn_millicores=1.8188
fixed   : mode=idle conns=1000 secs=8 cpu_seconds=0.000  cpu_per_conn_millicores=0.0000
```

### Virtual threads per idle connection (500 connections, jcmd dump)

| build | connection | writer | idle-timer | shared timer | total | per conn |
|---|---:|---:|---:|---:|---:|---:|
| baseline | 500 | 500 | 500 | 0 | **1500** | **3** |
| after commit 3 | 500 | 500 | 0 | 1 (platform) | **1000** | **2** |
| after commit 4 | 500 | 0 | 0 | 1 (platform) | **500** | **1** |

At 1M connections: **3M VTs → 1M VTs**, all idle-writer CPU removed, and idle-timer work is one
shared thread instead of 1M sleeping threads.

## Profiling (JFR)

`jfr view allocation-by-site` over 200k filmed frames (baseline):

```
ConcurrentHashMap.initTable()        38.41%   (one-time)
H2Connection.appendInput()           22.45%
java.util.Arrays.copyOf(byte[],int)  17.37%
GC: 1 young GC, 48.1 MB before (≈240 B/frame)
```

**Finding that changed the plan:** the `new Array[Byte](8192)` in `appendInput` is JIT
escape-analysed away; real per-frame allocation is ~240 B (copyOf + Chunk concat + ACK encode).
So removing the 8 KB array ("Fix B") would not measurably help — dropped with evidence instead
of shipping a placebo. Static analysis had over-estimated this cost.

## What each change does (design notes)

- **Park/signal writer (1):** the mux `MuxStream` has no blocking `takeOutbound`, so a
  `SignallingStream` wrapper wakes the writer on every `send`; `clear → re-check → park`
  avoids lost wake-ups. No 1 s backstop (it would reintroduce O(connections) wakeups at 1M).
- **Shared idle timer (3):** one `ScheduledThreadPoolExecutor` daemon thread for all idle
  deadlines. `resetIdleTimer` is now a single atomic timestamp write (it used to *interrupt a
  VT per inbound frame*). On expiry the task hands GOAWAY+drain to a short-lived VT so the timer
  thread never blocks on socket I/O.
- **Direct DATA writes (4):** HEADERS/control already wrote directly under `writeLock`; DATA now
  does too (`writeDataFrame`). Blocking write on the handler VT = natural per-stream backpressure.
  The post-send `isClosed` guard is limited to non-terminal frames: a local END_STREAM on a
  remotely-half-closed stream is legitimate and must not emit a spurious `RST_STREAM(CANCEL)`.

## Trade-offs / risks noted

- DATA now flushes **per frame**. Good for small responses (the common case); large streaming
  bodies pay one flush/frame. Could batch per response later if a large-body throughput
  regression is observed.
- A slow client now parks that stream's handler VT (backpressure) instead of a writer VT.
- `request`/`header` timeout deadlines still use one short-lived VT per request
  (`startRequestTimer`, `scheduleTimeoutRst`). That is a per-request, not per-connection cost;
  a candidate next step is to move them onto the same shared scheduler.

## Reproduce

```bash
cd /home/user/zio-http-v4-perf
CP=$(./mill show 'serverLoom.jvm[3.9.0].test.runClasspath' | grep -o '"[^"]*"' \
      | sed -E 's#.*:(/.*)"#\1#' | grep '^/' | paste -sd:)

java -cp "$CP" zio.http.h2.PerfIdle idle 1000 8          # idle CPU
# VT count while it runs:
java -cp "$CP" zio.http.h2.PerfIdle idle 500 30 & P=$!; sleep 6
/opt/jdk-25/bin/jcmd $P Thread.dump_to_file -format=json /tmp/vt.json
# allocation profile:
java -Xmx256m -XX:StartFlightRecording=settings=profile,filename=/tmp/f.jfr,dumponexit=true \
     -cp "$CP" zio.http.h2.PerfIdle feed 10 20000
/opt/jdk-25/bin/jfr view allocation-by-site /tmp/f.jfr

./mill 'serverLoom.jvm[3.9.0].test.testCached'           # 271 tests
```

Logs: `target/logs/perf-*.log`.
