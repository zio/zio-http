# zio-http-endpoint

Endpoint API and codec layer for ZIO HTTP. This module contains:

- `Endpoint` — typed HTTP endpoint definitions
- `codec/` — `HttpCodec`, `PathCodec`, `QueryCodec`, `HeaderCodec`, etc.
- `endpoint/openapi/` — OpenAPI / Swagger UI generation
- `endpoint/grpc/` — gRPC endpoint support

## Maven coordinates

```scala
libraryDependencies += "dev.zio" %% "zio-http-endpoint" % "<version>"
```

## Dependencies

- `zio-http-core`

## Status

This module was introduced as part of the module split described in [#3472](https://github.com/zio/zio-http/issues/3472).
Source files currently live in `../zio-http/` and are referenced via `unmanagedSourceDirectories`.
Future work will physically migrate sources here.
