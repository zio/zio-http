# zio-http-netty

Netty backend for ZIO HTTP. This module contains the Netty-based implementations of:

- `Server` — Netty HTTP/1.1 and HTTP/2 server
- `ZClient` — Netty HTTP client with connection pooling
- WebSocket server and client handlers
- SSL/TLS support
- Native transport support (epoll, kqueue, io_uring)

## Maven coordinates

```scala
libraryDependencies += "dev.zio" %% "zio-http-netty" % "<version>"
```

## Dependencies

- `zio-http-core`

## Status

This module was introduced as part of the module split described in [#3472](https://github.com/zio/zio-http/issues/3472).
Source files currently live in `../zio-http/` under `jvm/src/main/scala/zio/http/netty/` and are referenced via `unmanagedSourceDirectories`.
Future work will physically migrate sources here.
