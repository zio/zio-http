---
id: h2-server
title: H2 Server
---

The v4 server (`LoomServer`) serves HTTP/2 only: cleartext H2C and H2 over
TLS. There is no HTTP/1.1 server mode, no Netty transport, and no H3/QUIC
transport (`H2Transport` rejects `Protocol.H3` with
`UnsupportedOperationException`).

```scala mdoc:compile-only
import zio.blocks.context.Context
import zio.http._

val connector = Connector(
  bind = BindAddress.localhost(8080),
  protocol = Protocol.H2C(),
)

val server = LoomServer(connector)
val handle = server.serve(Routes.empty, Context.empty)
```

## H2C prior knowledge

Cleartext clients speak H2C with prior knowledge: they send the connection
preface (`PRI * HTTP/2.0...`) immediately, with no HTTP/1.1 Upgrade dance.
The `H2CSmokeTest` main shows the exact byte exchange (preface, empty
`SETTINGS`, `SETTINGS` ack) against a default `Connector`, whose protocol
defaults to `Protocol.H2C()`.

## `Http2Config`: wire SETTINGS and bounds

`Http2Config` is the single source of truth for the server's SETTINGS frame
and its internal limits:

| Field                | Default | Wire effect and enforcement                                            |
| -------------------- | ------- | ---------------------------------------------------------------------- |
| `maxConcurrentStreams` | `100` | Sent as `SETTINGS_MAX_CONCURRENT_STREAMS`; over-limit streams are refused with `REFUSED_STREAM` |
| `initialWindowSize`  | `65535` | Sent as `SETTINGS_INITIAL_WINDOW_SIZE`; values outside `[0, 2147483647]` are rejected at config time |
| `maxFrameSize`       | `16384` | Sent as `SETTINGS_MAX_FRAME_SIZE`; values outside `[16384, 16777215]` throw `IllegalArgumentException` |
| `maxHeaderListSize`  | `8192`  | Sent as `SETTINGS_MAX_HEADER_LIST_SIZE`; oversized header blocks are rejected with `RST_STREAM(ENHANCE_YOUR_CALM)` or `GOAWAY(PROTOCOL_ERROR)` |

The `Http2SettingsWireSpec` captures the first SETTINGS frame and asserts the
configured values appear on the wire; `MaxHeaderListSizeSpec` proves an
oversized header block never leaks into the handler; the
`H2HardeningIntegrationSpec` replays the same attacker probes against a real
`LoomServer`.

## TLS: `TlsConfig` ALPN and version pinning

```scala mdoc:compile-only
import zio.blocks.config.Secret
import zio.http._

val tls = TlsConfig(
  certChain = TlsSource.PemString(Secret("...")),
  privateKey = TlsSource.PemString(Secret("...")),
  alpnProtocols = List("h2"),
  alpnPolicy = AlpnPolicy.StrictH2,
  tlsVersions = List("TLSv1.3", "TLSv1.2"),
)

val connector = Connector(
  bind = BindAddress.localhost(8443),
  protocol = Protocol.H2(tls),
)
```

- The ALPN offer list comes from `TlsConfig.alpnProtocols` (default
  `List("h2")`) on every path, including a caller-provided
  `TlsSource.SslContext`: a raw context without ALPN fails fast instead of
  silently bypassing negotiation.
- `AlpnPolicy.StrictH2` (the default) rejects non-`h2` clients at the TLS
  layer with an `SSLHandshakeException`. `NegotiateH2Preferred` accepts the
  negotiated protocol, but the server itself stays H2-only: the policy only
  governs TLS rejection versus acceptance.
- `tlsVersions` pins the accepted TLS versions (default
  `List("TLSv1.3", "TLSv1.2")`); older clients are rejected during the
  handshake. `AlpnConfigSpec`, `TlsVersionPinSpec`, and the
  `H2HardeningIntegrationSpec` prove each behavior over real handshakes.

## Timeouts: idle `GOAWAY` and request `RST_STREAM`

`Connector.idleTimeout` (default 60 seconds) drives graceful connection
shutdown: an idle connection receives `GOAWAY(NO_ERROR)` carrying the real
`lastStreamId` (never `Int.MaxValue`), drains, then closes TCP. Request
timeouts surface as `RST_STREAM(CANCEL)`. Both run on Loom virtual threads,
sharing the connection's single write lock. See `IdleTimeoutGoAwaySpec` and
the idle leg of `H2HardeningIntegrationSpec`.

## Hardening: bounds, trust, access log, raw-body tap

Operator reference for the general hardening knobs. Every default below is
read off `Connector` (`zio-http-server/shared/src/main/scala/zio/http/Connector.scala`,
companion `Default*` vals) or `ClientConfig`
(`zio-http-client/shared/src/main/scala/zio/http/ClientConfig.scala`) — not
guessed. The server knobs live on the top-level `Connector` and apply across
H2/H3 at the config-concept level; only the H2 transport enforces them today
(H3 wiring is future work). Wire-only settings stay on `Http2Config` (see
above).

| Knob | Default | Enforcement point | Violation signal |
| ---- | ------- | ----------------- | ---------------- |
| `maxRequestBodySize` | `1 MiB` (`1024L * 1024L`, `DefaultMaxRequestBodySize`) | `H2Transport.readRequestBody`: DATA payloads accounted incrementally, never buffered past the cap; a declared `content-length` over the cap is rejected before any body bytes are read | Over cap: `RST_STREAM(FLOW_CONTROL_ERROR)`; declared-vs-received mismatch or malformed length: `RST_STREAM(PROTOCOL_ERROR)`; per-stream, connection survives |
| `requestTimeoutMs` | `30000` (30 s, `DefaultRequestTimeoutMs`) | Per-stream request timer: whole-request deadline from stream start until the response completes. Non-positive disables | `RST_STREAM(CANCEL)`, per-stream; connection survives |
| `headerTimeoutMs` | `5000` (5 s, `DefaultHeaderTimeoutMs`) | `H2Connection` pending headers + `H2Transport.awaitHeaders`: time-to-complete from stream start covering trailing `CONTINUATION` frames and first `HEADERS` delivery. Non-positive disables | `RST_STREAM(CANCEL)`, per-stream; connection survives |
| `bodyTimeoutMs` | `10000` (10 s, `DefaultBodyTimeoutMs`) | `H2Transport.readRequestBody` / `awaitFrame`: time-to-complete from stream start until the full body arrives — total elapsed, so a drip that keeps moving but too slowly still times out. Non-positive disables | `RST_STREAM(CANCEL)`, per-stream; connection survives |
| `trustedProxy` | default-deny (`TrustedProxyConfig.default`: empty `trustedCidrs`, `trustPeerCert = false`) | `H2Transport.resolveProxyTrust`: forwarding headers (`X-Forwarded-For/Proto/Host`, RFC 7239 `Forwarded`) are honored only for allowlisted-CIDR or mTLS-authenticated peers; otherwise stripped with zero effect. Either way the raw forwarding headers plus any wire `x-client-ip` / `x-peer-address` are removed and re-added as normalized `x-peer-address` (socket peer) + `x-client-ip` (resolved client) | Spoofed headers ignored; client IP falls back to the socket peer address |
| `ClientConfig.maxResponseBodySize` | `16 MiB` (`16L * 1024L * 1024L`, `ClientConfig.DefaultMaxResponseBodySize`) | Every client leg accounts response bytes incrementally and fails fast past the cap: the one-shot `H2WireClient` leg (cap threaded from the driver), the pooled `PooledLoomH2Client` leg (lazy streaming bodies), the JDK `JavaH2Client` leg (bounded subscriber). Non-positive fails fast at config construction | `ResponseBodyTooLarge(maxBytes)` on every leg. Deliberately unchecked: the pooled leg streams through `Body.toArray`, which treats a mid-pull `IOException` as truncation — a checked cap would fail silently there. The one-shot driver never falls back to the JDK h1.1 leg on this error (the H2 exchange itself succeeded) |
| Log sink | `Middleware.accessLog(sink)` opt-in; transports emit nothing by default (no console noise, no per-request record) | One `AccessLogRecord` per served request that reaches a route: method, path (logged verbatim — standard access-log behavior, no scrub hook), route, status, duration (handler wall-clock only — response-body send time is excluded), request-id (sanitized: `[A-Za-z0-9-._~]` only, truncated to 128 chars, generated UUID when absent or empty after sanitizing), peer/client addresses, trust decision, deadline outcome (`ok` — the only outcome any built-in emitter reports; `header-timeout` / `body-timeout` are reserved for future transport-side timeout reporting and never emitted today, and streams reset by a deadline yield no record at all), protocol (request version string). The record is metadata-only by construction (no body/header-map/stream handle — verified by reflection in `H2AccessLogSpec`). `AccessLog.emit` swallows sink failures, so logging can never fail a request | N/A (never fails requests) |
| Raw-body tap | `request.body.toChunk` (synchronous, zio-blocks http-model `Body`) | `H2Transport` reassembles DATA payloads byte-identically under `maxRequestBodySize`; handlers tap `request.body.toChunk` FIRST and decode into domain types second. No verification logic lives here | N/A |

Example:

```scala mdoc:compile-only
import zio.http._

val connector = Connector(
  bind = BindAddress.localhost(8080),
  protocol = Protocol.H2C(),
  maxRequestBodySize = Connector.DefaultMaxRequestBodySize, // 1 MiB
  requestTimeoutMs = Connector.DefaultRequestTimeoutMs,     // 30 s
  headerTimeoutMs = Connector.DefaultHeaderTimeoutMs,       // 5 s
  bodyTimeoutMs = Connector.DefaultBodyTimeoutMs,           // 10 s
  trustedProxy = TrustedProxyConfig(trustedCidrs = Set("10.0.0.0/8")),
)

val logged = routes @@ Middleware.accessLog(AccessLogSink.console)
```

### Isolation contract

Every bound/timeout violation above is per-stream: the offending stream is
reset with the listed code and sibling streams on the same connection keep
working. The one exception is a peer that violates the HPACK contract itself
(e.g. an undecodable header block): that corrupts connection-level
compression state, so the server tears the connection down instead of serving
anything on it. The combined matrix (`H2HardeningMatrixSpec`) pins all of
this against a real loopback `LoomServer`.

### Qaizn handoff

v4 preserves bytes; Qaizn decides what they mean.

- The v4 contract ends at the `toChunk` tap: `H2Transport` delivers the DATA
  payloads byte-identically, and the handler hashes `request.body.toChunk`
  BEFORE any decoding (`H2RawBodySpec` pins this with the RFC 4231 Test Case 1
  HMAC-SHA256 vector). v4 performs no HMAC verification, no dedup, no
  reconciliation.
- Qaizn consumes `(keyId, messageBytes, macHex)` triples produced exactly at
  that tap point and owns accept/reject: it verifies the HMAC against stored
  keys, dedupes repeat deliveries, and reconciles MACs across its own store.
- Access/audit logging stays metadata-only: body bytes must never reach the
  log sink (pinned by `H2AccessLogSpec` and the matrix sink cell), so any
  payload Qaizn needs for reconciliation must travel via its own channel, not
  the access log.

### Migration note (strict defaults)

Defaults are strict: 1 MiB body cap (`maxRequestBodySize`), 10 s total body
timeout (`bodyTimeoutMs`), 8192-byte header cap (`maxHeaderListSize`), 16 MiB
response cap (`ClientConfig.maxResponseBodySize`). Raise them via the general
`Connector` / `ClientConfig` knobs when legitimate traffic exceeds them — the
violation signals above tell you which knob to turn.

## Outbound client: `PooledLoomH2Client`

`PooledLoomH2Client` is a blocking, virtual-thread H2 client that reuses the
`h2-codec` frame layer (`FrameCodec`/HPACK) and multiplexes streams per
connection:

```scala mdoc:compile-only
import zio.http._

val pool = PooledLoomH2Client(
  ClientConfig(
    alpn = ClientAlpnPolicy.H2PreferredWithH11Fallback,
    pool = PoolConfig(maxPerHost = 10),
  ),
)

val response = pool.send(Request.get(URL.parse("http://127.0.0.1:8080/").toOption.get))
pool.close()
```

- `ClientTlsConfig` selects trust/key material and pins `tlsVersions`
  (default `TLSv1.3`/`TLSv1.2`); empty version lists are rejected.
- `ClientAlpnPolicy` is independent from the server-side `AlpnPolicy`:
  `H2PreferredWithH11Fallback` (the default) falls back to an HTTP/1.1 leg
  against third-party servers, while `StrictH2` and `H2OnlyH2C` fail fast
  with `SSLHandshakeException`. The server never falls back; only the client
  does, and only under the fallback policy.
- `PoolConfig` sizes the pool (`maxPerHost`, `maxTotal`, `idleTimeout`,
  `queueSize`); idle connections past `idleTimeout` are reclaimed on
  checkout, and an overflowing queue fails fast instead of hanging.
- `DeadlineConfig` carries optional per-stage overrides read through
  `ClientConfig.effectiveConnectTimeout` / `effectiveRequestTimeout` /
  `effectiveStreamTimeout`; expiries surface as `TimeoutException`.
- Response bodies stream lazily (`knownChunk.isEmpty`): the pool slot stays
  checked out until the body is fully consumed or the exchange is cancelled
  (cancel sends `RST_STREAM(CANCEL)` and evicts the connection), so always
  consume or cancel. `ClientMiddleware` composition is preserved.

`ClientPoolStreamingSpec` proves reuse, reclamation, cancellation,
deadlines, and heap-bounded 8 MiB streaming; `LoomH2ClientAlpnSpec` proves
`h2` negotiation and fallback behavior.

## Server-sent events

`ServerSentEvent(data, event, id, retry)` models one SSE message;
`SseCodec.encode` frames it (`data:`/`event:`/`id:`/`retry:` lines plus the
blank-line terminator, splitting multi-line data). `Sse.body` builds an
unknown-length `text/event-stream` body that frames each event as it flows
(never materialized), and `Sse.response` adds `Content-Type:
text/event-stream` with `Cache-Control: no-cache`:

```scala mdoc:compile-only
import zio.blocks.streams.Stream
import zio.http._
import zio.http.sse.Sse._
import zio.http.sse.ServerSentEvent

val events: Stream[Nothing, ServerSentEvent] =
  Stream.fromIterable(List(ServerSentEvent("hello", event = Some("greeting"))))

val response: Response = Response.sse(events)
val body: Body = Body.sse(events)
```

Inter-message delay survives end to end: the transport streams
`body.toStream` chunk-by-chunk through flow control, so a paced event stream
arrives paced (not batched) at the client. `SseCodecSpec` covers framing,
`SseDelayIntegrationSpec` proves 5 events at 200 ms spacing arrive spaced
over H2 DATA frames with per-event incremental reads, and the SSE leg of
`SseEndToEndSpec` proves the same through the pooled client.

## End-to-end coverage (what each leg actually proves)

`SseEndToEndSpec` composes three legs over a real `LoomServer` plus
`PooledLoomH2Client` on ephemeral ports. Each leg's proof is bounded:

- SSE leg: paced events with client-observed timing and byte equality
  (`SseCodec` wire encoding, per-event incremental reads).
- Endpoint-shaped leg: byte-identical TRANSPORT proof only. The request is
  hand-built to match what T12 proves `EndpointBridge.buildRequest` renders
  (`GET /users/42?active=true` with header and JSON body, never `URL.root`);
  the leg proves those bytes survive pool plus server to echo-equality. It
  does NOT exercise the walker (`EndpointCodecWalker.decompose`) or
  query/header rendering — that breadth lives in `EndpointRoundTripSpec`
  (fixed shapes) and `EndpointCodecFuzzSpec` (64 generated shapes proving
  auth-header no-drop/no-duplication and query render→parse stability),
  together with the T12 render proof in `EndpointBridgeRenderingSpec`.
- Pool/streaming leg: connection reuse with large-body lazy streaming.
