# ZIO-WEB

| CI | Release | Issues |  Discord |
| --- | --- | --- | --- |
| [![Build Status][badge-ci]][link-ci] | [![Release Artifacts][badge-sonatype]][link-sonatype] | [![Average time to resolve an issue][badge-iim]][link-iim] | [![badge-discord]][link-discord] |

ZIO Web supports the features you need to be productive:

 * **Endpoints**. Define reliable and scalable endpoints concisely, type-safely, and composably.
 * **Protocol-Agnostic**. Deploy endpoints to any supported protocol, including HTTP and gRPC.
 * **Stream-friendly**. Handle requests and responses that are too big to fit in memory at once.
   * WebSockets
 * Introspection-friendly
   * **Documentation**. Generate documentation that is automatically in-sync with the endpoints.
   * **Client**. Interact with an endpoint type-safely from Scala without writing any code.
 * Middleware-friendly. Middleware is type-safe and compositional and can include built-in middleware or third-party middleware, or combinations.
   * **Metrics/Monitoring**. Built-in integration with ZIO ZMX.
   * **Rate-limiting**. Customizable rate-limiting with DDOS protection.
   * **Logging**. Request/response logging in Common Log format.
   * Via third-party libraries, pluggable authentication, authorization, persistence, caching, session management.
 * **High-performance**. Fastest functional Scala library.

Compared to the competition, ZIO Web features: 

 * ZIO native
 * Beginner-friendly
   * Minimal / no type classes, implicits, or higher-kinded types
   * Highly discoverable API
   * Good type inference 
   * Minimal jargon
 * Expert-friendly
   * Type-safe
   * No magic, edge cases, or surprises
 * GraalVM-ready
 * Fast performance
 * Composable

## Protocols

ZIO Web supports the following protocols out-of-the-box:

* HTTP
* WebSockets
* gRPC
* Avro

## Installation

Add in your `build.sbt`:

```scala
libraryDependencies += "dev.zio" %% "zio-http-core" % "<version>"
```

[badge-ci]: https://circleci.com/gh/zio/zio-web/tree/master.svg?style=svg
[badge-sonatype]: https://img.shields.io/nexus/r/https/oss.sonatype.org/dev.zio/zio-http-core_2.12.svg
[badge-iim]: https://isitmaintained.com/badge/resolution/zio/zio-web.svg
[badge-discord]: https://img.shields.io/discord/629491597070827530?logo=discord "chat on discord"

[link-ci]: https://circleci.com/gh/zio/zio-web/tree/master
[link-sonatype]: https://oss.sonatype.org/content/repositories/releases/dev/zio/zio-http-core_2.12/
[link-iim]: https://isitmaintained.com/project/zio/zio-web
[link-discord]: https://discord.gg/WUyWFfsP "Discord"
