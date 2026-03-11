# Module Architecture

As of [#3472](https://github.com/zio/zio-http/issues/3472), `zio-http` is being split into focused Maven artifacts.

## Module Overview

```
                    ┌─────────────────────────────────┐
                    │   zio-http  (umbrella / compat)  │
                    └──────────────┬──────────────────┘
                                   │ depends on
               ┌───────────────────┼────────────────────┐
               │                   │                    │
    ┌──────────▼──────────┐  ┌─────▼──────────┐  ┌─────▼──────────┐
    │  zio-http-endpoint  │  │  zio-http-netty │  │ zio-http-testkit│
    └──────────┬──────────┘  └─────┬──────────┘  └─────┬──────────┘
               │                   │                    │
               └───────────────────┼────────────────────┘
                                   │ depends on
                          ┌────────▼────────┐
                          │  zio-http-core  │
                          └─────────────────┘
```

## Module Descriptions

### `zio-http-core`
Fundamental HTTP types with no dependency on Netty or the Endpoint DSL.

- HTTP primitives: `Request`, `Response`, `Body`, `Headers`, `Status`, `Method`, `URL`
- Routing: `Handler`, `Routes`, `Route`, `Middleware`, `HandlerAspect`
- Connection abstractions: `Server` (trait), `ZClient` (trait), `Driver`, `ClientDriver`
- Cookie, Form, QueryParams, WebSocket frame types
- `template` / `template2` HTML DSL
- Platform adapters (non-Netty JVM, Scala.js)

**Maven coordinates:**
```scala
"dev.zio" %% "zio-http-core" % "<version>"
```

### `zio-http-endpoint`
Typed Endpoint DSL and HTTP codec layer.

- `codec/`: `HttpCodec`, `PathCodec`, `QueryCodec`, `HeaderCodec`, etc.
- `endpoint/`: `Endpoint`, `EndpointExecutor`, OpenAPI / Swagger UI generation, gRPC support

Depends on `zio-http-core`.

**Maven coordinates:**
```scala
"dev.zio" %% "zio-http-endpoint" % "<version>"
```

### `zio-http-netty`
Netty-based backend (JVM-only) providing concrete `Server` and `ZClient` implementations.

- `netty/server/`: `NettyDriver`, request/response pipeline, SSL, HTTP/2
- `netty/client/`: `NettyClientDriver`, connection pools, request encoder
- Native transports: epoll, kqueue, io_uring (optional/provided)

Depends on `zio-http-core`.

**Maven coordinates:**
```scala
"dev.zio" %% "zio-http-netty" % "<version>"
```

### `zio-http-testkit`
Test utilities and in-process test server/client.

Depends on `zio-http-core` (and `zio-http-netty` at runtime).

**Maven coordinates:**
```scala
"dev.zio" %% "zio-http-testkit" % "<version>" % Test
```

### `zio-http` (umbrella)
Backward-compatible aggregate module. Depends on all sub-modules above.
Existing users who depend on `"dev.zio" %% "zio-http"` are unaffected.

## Migration Guide

Most users can keep using `zio-http` as before. Power users who want a leaner dependency graph can switch:

| If you only use…        | Switch to              |
|-------------------------|------------------------|
| Core HTTP types / routing | `zio-http-core`      |
| Endpoint DSL / OpenAPI  | `zio-http-endpoint`    |
| Netty server / client   | `zio-http-netty`       |
| Test utilities          | `zio-http-testkit`     |
| Everything              | `zio-http` (unchanged) |
