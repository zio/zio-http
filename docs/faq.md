---
id: faq
title: Frequently Asked Questions
sidebar_label: FAQ
---

### I'm New to ZIO! How Can I Get Started with ZIO HTTP?

If you are new to ZIO, you can start by reading the [ZIO documentation](https://zio.dev/overview/getting-started) to understand the core concepts of ZIO. Once you are comfortable with ZIO, you can explore the ZIO HTTP documentation to learn how to build HTTP applications using ZIO. There are also several [examples](https://github.com/zio/zio-http/tree/main/zio-http-example/src/main/scala/example) available in the ZIO HTTP repository that help you get started quickly.

### What Libraries Does ZIO HTTP Rely On?

ZIO HTTP is built on top of [Netty](https://netty.io/) for networking operations. It also leverages [ZIO Core](https://zio.dev/reference/core/zio/), [ZIO Schema](https://zio.dev/zio-schema/), and [ZIO Stream](https://zio.dev/reference/stream/) for concurrency, encoding/decoding, and streaming, respectively.

### I Love ZIO, But Don't Want to Use ZIO HTTP. What Alternatives Do I Have?

If you prefer not to use ZIO HTTP but still want to build HTTP applications using ZIO, you can consider using [Http4s](https://http4s.org/), a functional HTTP library for Scala that can be integrated with ZIO effects ([link](https://github.com/zio/zio-json/tree/series/2.x/examples/interop-http4s)). Additionally, if you are looking for a declarative approach to building HTTP applications, similar to ZIO HTTP's `Endpoint` API, you can explore [Tapir](https://tapir.softwaremill.com/). It's easy to integrate Tapir with ZIO using [tapir-zio-http-server](https://tapir.softwaremill.com/en/latest/server/ziohttp.html) module.

### How Can I Serialize/Deserialize Data to/from JSON in Requests and Responses?

ZIO HTTP provides built-in support for JSON serialization and deserialization using [ZIO Schema](https://zio.dev/zio-schema/). You can derive JSON codecs for your custom data types using ZIO Schema and use them to encode/decode data to/from request/response bodies. Check out the [BinaryCodecs](./reference/body/binary_codecs.md) section in the documentation for more details.

### How Can I Handle CORS Requests in ZIO HTTP?

ZIO has several middlewares including `CORS` that can be used to handle cross-origin resource sharing requests. Check out the [Middleware](./reference/aop/middleware.md) section in the documentation for more details.

### How Does ZIO HTTP Handle Errors?

As a ZIO-based library, ZIO HTTP leverages ZIO's error-handling capabilities to handle errors in HTTP applications. You can use the `ZIO#catch**` methods to handle errors at various levels in your application. If you are not familiar with error handling in ZIO, we recommend reading the [ZIO documentation](https://zio.dev/reference/error-management/).

Besides ZIO's error handling, ZIO HTTP has built-in support for handling errors. It has a notion of handled and unhandled errors at the HTTP layer. Let's say we have a `Handler[-R, +Err, Request, Response]`, if the error channel is `Response` or a sub-type of it, it is considered as handled error, otherwise, it is considered as a handler with the unhandled error.

We have the same thing at the route level. Let's say we have a `Routes[-Env, +Err]`, if the error channel is `Response` or a sub-type of it, it is considered as a collection of routes with all the errors handled, otherwise, it is considered as a collection of routes with unhandled errors. Before we can serve all routes, we should convert all unhandled errors to handled errors, using the `Routes#handlerError` method.

### Is ZIO HTTP Suitable for Building High-performance HTTP Servers?

Yes, ZIO HTTP is designed for performance, leveraging non-blocking I/O and asynchronous concurrency to handle high loads efficiently.

1. It is [Netty](https://netty.io/)-based, which is a proven, high-performance, asynchronous, event-driven network application framework. You can optimize Netty's performance by tuning various parameters, such as changing leak detection levels, adjusting the [channel type](reference/server.md#netty-configuration) (NIO, Epoll, KQueue, or io_uring), and configuring the number of threads.
2. It uses [ZIO's fiber-based concurrency model](https://zio.dev/reference/fiber/), which allows for lightweight and efficient handling of concurrent requests.
3. It has streaming support using [ZIO Stream](https://zio.dev/reference/stream/), enabling efficient handling of large data transfers without consuming excessive memory.
4. You can compare its performance with other Scala HTTP frameworks using benchmarks like the [TechEmpower Framework Benchmarks](https://www.techempower.com/benchmarks/). It is listed in  two benchmark categories of TechEmpower:
   - [JSON Serialization](https://www.techempower.com/benchmarks/#section=data-r23&test=json)
   - [Plaintext](https://www.techempower.com/benchmarks/#section=data-r23&test=plaintext)

### Can I integrate ZIO HTTP with Existing Scala/Java Libraries?

Yes, ZIO HTTP provides interoperability with existing libraries, allowing you to leverage functionality from the Scala/Java ecosystem seamlessly by importing blocking and non-blocking operations.

### Can I use the Netty io_uring incubator transport?
Yes there is a server configuration for this transport - but this transport is experimental so you must explicitly add the library in your project dependencies. Some Netty based libraries use transports like this by default if they are on the classpath - so make sure if you are using Netty elsewhere in your project that the library will not cause unintended channel type selection. An example of such a Netty based library is [lettuce](https://github.com/redis/lettuce/issues/3222) and there may be others like this as well. You can find the uring channel type library [here](https://github.com/netty/netty-incubator-transport-io_uring).   

### Is ZIO HTTP Suitable for Building Microservices?

Yes, ZIO HTTP along with the ZIO ecosystem is well-suited for building microservices, which provides many aspects that are essential for building cloud-native applications:

- **Configuration**- ZIO has a [built-in configuration system](https://zio.dev/reference/configuration/) that allows you to manage different configurations for different environments. It also has [ZIO Config](https://zio.dev/zio-config/) that provides various config providers to load configurations from different sources, such as HOCON, JSON, YAML, and environment variables.
- **Logging and LogAnnotations**- ZIO provides a [structured logging system](https://zio.dev/reference/observability/logging) that allows you to log messages with different log levels. You can also use log annotations to add additional context to log messages which can be useful for debugging and tracing in distributed systems. We can use any of the backend logging supported by [ZIO Logging](https://zio.dev/zio-logging/), such as Log4j, Logback, and more.
- **Distributed Tracing**- In microservice architectures, distributed tracing is essential for monitoring the flow of requests across different services. [ZIO Telemetry](https://zio.dev/zio-telemetry/) supports distributed tracing using OpenTelemetry, OpenTracing, and OpenCensus.
- **Instrumenting Metrics**- ZIO has [built-in support for metrics instrumentation](https://zio.dev/reference/observability/metrics/), with popular [metrics backends](https://zio.dev/zio-metrics-connectors/) such as Prometheus, Datadog, New Relic, and more.
- **Resilience to failures**- When building microservices, it is essential to handle failures gracefully. There is a project called [Rezilience](https://zio.dev/ecosystem/community/) that provides various resilience patterns such as retries, timeouts, circuit breakers, rate limiting, and more to build robust and resilient microservices.
- **Concurrency and Parallelism**- ZIO provides a powerful concurrency model that allows you to write highly concurrent and parallel applications. With [ZIO Fiber](https://zio.dev/reference/concurrency/fiber/), and its [Concurrency Primitives](https://zio.dev/reference/concurrency/#concurrency-primitives), you can write highly concurrent applications.
- **Resource-safety** - ZIO provides a resource system that ensures resources are acquired and released safely. With [ZIO Scopes](https://zio.dev/reference/resource/scope/) and also [scoped Layers](https://zio.dev/reference/resource/scope/#converting-resources-into-other-zio-data-types), we can manage resources in a structured way.
- **ZIO Aspect and Middlewares**- Both ZIO and ZIO HTTP support the idea of aspects and middlewares, which can be used to add cross-cutting concerns such as logging, metrics, authentication, and more to your services.
- **Modularity**- With [service pattern](https://zio.dev/reference/service-pattern/) in ZIO, we can define our services along with their dependencies, and finally, we can start the service with its dependencies. This pattern is useful for building modular and testable services.
