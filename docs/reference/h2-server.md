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
