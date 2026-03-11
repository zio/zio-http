# zio-http-core

Core HTTP types for ZIO HTTP. This module contains the fundamental building blocks:

- `Request`, `Response`, `Body`, `Headers`, `Status`, `Method`, `URL`
- `Handler`, `Routes`, `Route`, `Middleware`
- `Server` and `ZClient` interfaces (without Netty implementation)
- `Cookie`, `Form`, `QueryParams`, `WebSocket` types
- `template` and `template2` HTML DSL

## Maven coordinates

```scala
libraryDependencies += "dev.zio" %% "zio-http-core" % "<version>"
```

## Status

This module was introduced as part of the module split described in [#3472](https://github.com/zio/zio-http/issues/3472).
Source files currently live in `../zio-http/` and are referenced via `unmanagedSourceDirectories`.
Future work will physically migrate sources here.
