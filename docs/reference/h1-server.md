---
id: h1-server
title: H1 Server
---

The v4 server (`LoomServer`) can serve cleartext strict HTTP/1.1 when the
connector's protocol is `Protocol.H1C()`. H2C remains the default: when
`protocol` is omitted from `Connector`, the server speaks HTTP/2 with prior
knowledge (see the [H2 Server](./h2-server.md) page).

## Serving H1

```scala mdoc:compile-only
import zio.blocks.context.Context
import zio.http._

val connector = Connector(
  bind = BindAddress.anyHost(8080),
  protocol = Protocol.H1C(),
)

val server              = LoomServer(connector)
val routes: Routes[Any] = Routes.empty
val handle               = Server.serve(routes, Context.empty.add(server))
```

## What `Protocol.H1C()` means

`Protocol.H1C()` is cleartext strict HTTP/1.1 and nothing else:

- No TLS and no ALPN: the listener is plain TCP.
- No `Upgrade: h2c` and no HTTP/2 prior-knowledge dispatch. An HTTP/2 preface
  (`PRI * HTTP/2.0...`) arriving on an H1C port is a malformed request line:
  the server answers `400 Bad Request` with `Connection: close`.
- No HTTP/1.0: the request line must carry exactly `HTTP/1.1`.

## Request handling

- Connections are persistent (keep-alive) by default; a request that asks for
  `Connection: close` gets it, and the connection ends after that response.
- Pipelined requests are served strictly in wire order, one at a time. There
  is no concurrent or out-of-order pipelined execution.
- Request bodies are fully read and framed before the handler runs, so the
  server never responds before a declared body arrives.
- Responses with an unknown length are streamed as
  `Transfer-Encoding: chunked`.
- `HEAD`, `1xx`, `204`, and `304` responses carry no body (`HEAD` may still
  advertise `Content-Length`).
- Request trailers are parsed and validated against `Trailer`, but are not
  exposed to handlers in this release.

## Limits and timeouts

| Knob | Default | Behavior |
| ---- | ------- | -------- |
| `Connector.maxRequestBodySize` | `1 MiB` | A declared or received body over the cap fails with `413 Payload Too Large` and the connection closes. Request bodies are fully buffered before the handler runs, bounded by this cap and an internal retention bound, so a large cap increases per-connection memory (H2 accounts for bodies incrementally instead). |
| `Connector.requestTimeoutMs` | `30000` (30 s) | Whole-request deadline, from the first byte read until the response turn ends. Non-positive disables it. |
| `Connector.idleTimeout` | `60 s` | Keep-alive idle wait between requests; expiry closes the connection. |
| Header and line bounds | internal | Request line, header, and chunk-size line lengths are bounded by fixed internal defaults (not configurable in this release); overflow fails with `400` and close. |
| `Connector.trustedProxy` | default-deny | Exactly as on H2: forwarding headers (`X-Forwarded-*`, `Forwarded`) are honored only for allowlisted-CIDR or mTLS peers, otherwise stripped. No forwarding is performed either way. |
| `Connector.requestTelemetry` | `false` | Same gate as on H2; when enabled, each request is instrumented with protocol `http/1.1`. |

## Shared connector settings

`Connector` is shared with the H2/H3 surface, but H1C honors only some knobs:
`maxRequestBodySize`, `requestTimeoutMs`, `idleTimeout`, `trustedProxy`, and
`requestTelemetry` apply to H1C, with `requestTimeoutMs` covering both the body
read and the handler. `headerTimeoutMs` and `bodyTimeoutMs` are H2/H3-only
(H1C uses `requestTimeoutMs` instead), and `readBufferBytes` is currently not
honored by H1, which uses a fixed 8 KiB read buffer. See the
[H2 Server](./h2-server.md) page for the H2-specific limits.

## Security posture

Framing is strict and single-interpretation. The following are rejected with
`400 Bad Request` (or `417` where noted) and `Connection: close`:

- `Content-Length` together with `Transfer-Encoding`, duplicate or
  comma-joined `Content-Length`, and `Transfer-Encoding` other than a single
  `chunked`
- bare LF line endings, obs-fold continuation lines, whitespace before the
  header colon, control or non-ASCII bytes in header names or values
- malformed request lines, including any version other than `HTTP/1.1`
- a message that does not begin exactly with the request line: leading
  empty CRLF lines before it are rejected
- missing, empty, multiple, or authority-conflicting `Host` values: every
  HTTP/1.1 request must carry exactly one non-empty `Host`, including
  absolute-form targets
- trailers that were not announced by `Transfer-Encoding: chunked`
- chunk extensions on any chunk size
- any `Expect` other than a single `100-continue` (`417 Expectation Failed`)

`Expect: 100-continue` is answered with an interim `100 Continue` before the
body is read; the handler still runs only after the full body has arrived.

## Out of scope

This release does not include:

- HTTP/1.0
- `CONNECT` tunnels (rejected with `501 Not Implemented`)
- proxy forwarding: `trustedProxy` only gates or strips forwarding headers
- TLS/ALPN HTTP/1.1 (H1C is cleartext-only in this release)
- WebSockets and any other `Upgrade` protocol
- shared-port H1/H2 negotiation: one connector speaks one protocol
