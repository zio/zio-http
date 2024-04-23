---
id: faq
title: Frequently Asked Questions
sidebar_label: FAQ
---

## I'm New to ZIO, How can I Get Started with ZIO HTTP?

If you are new to ZIO, you can start by reading the [ZIO documentation](https://zio.dev/overview/getting-started) to understand the core concepts of ZIO. Once you are comfortable with ZIO, you can explore the ZIO HTTP documentation to learn how to build HTTP applications using ZIO. There are also several [examples](https://github.com/zio/zio-http/tree/main/zio-http-example/src/main/scala/example) available in the ZIO HTTP repository that help you get started quickly.

## How Can I Serialize/Deserialize Data to/from JSON in Requests and Responses?

ZIO HTTP provides built-in support for JSON serialization and deserialization using ZIO Schema. You can derive JSON codecs for your custom data types using ZIO Schema and use them to encode/decode data to/from request/response bodies. Check out the [BinaryCodecs](./codecs.md) section in the documentation for more details.

## How Can I Handle CORS Requests in ZIO HTTP?

ZIO has several middlewares including `CORS` that can be used to handle cross-origin resource sharing requests. Check out the [Middleware](./dsl/middleware.md) section in the documentation for more details.

## How Does ZIO HTTP Handle Errors?

As a ZIO-based library, ZIO HTTP leverages ZIO's error-handling capabilities to handle errors in HTTP applications. You can use the `ZIO#catch**` methods to handle errors at various levels in your application. If you are not familiar with error handling in ZIO, we recommend reading the [ZIO documentation](https://zio.dev/reference/error-management/)

## Is ZIO HTTP suitable for building high-performance HTTP servers?

Yes, ZIO HTTP is designed for performance, leveraging non-blocking I/O and asynchronous concurrency to handle high loads efficiently.

## Can I integrate ZIO HTTP with existing Java libraries or frameworks?

Yes, ZIO HTTP provides interoperability with existing Java libraries and frameworks, allowing you to leverage functionality from the Java ecosystem seamlessly.

## Is ZIO HTTP suitable for building microservices?

Yes, ZIO HTTP along with the ZIO ecosystem is well-suited for building microservices, which provides many aspects that are essential for building cloud-native applications:

- **Configuration**- ZIO has a [built-in configuration system](https://zio.dev/reference/configuration/) that allows you to manage different configurations for different environments. It also has [ZIO Config](https://zio.dev/zio-config/) that provides various config providers to load configurations from different sources, such as HOCON, JSON, YAML, and environment variables.
- **Logging and LogAnnotations**- ZIO provides a [structured logging system](https://zio.dev/reference/observability/logging) that allows you to log messages with different log levels. You can also use log annotations to add additional context to log messages which can be useful for debugging and tracing in distributed systems. We can use any of the backend logging supported by [ZIO Logging](https://zio.dev/zio-logging/), such as Log4j, Logback, and more.
- **Distributed Tracing**- In microservice architectures, distributed tracing is essential for monitoring the flow of requests across different services. [ZIO Telemetry](https://zio.dev/zio-telemetry/) supports distributed tracing using OpenTelemetry, OpenTracing, and OpenCensus.
- **Instrumenting Metrics**- ZIO has [built-in support for metrics instrumentation](https://zio.dev/reference/observability/metrics/), with popular [metrics backends](https://zio.dev/zio-metrics-connectors/) such as Prometheus, Datadog, New Relic, and more.
- **Resilience to failures**- When building microservices, it is essential to handle failures gracefully. There is a project called [Rezilience](https://zio.dev/ecosystem/community/) that provides various resilience patterns such as retries, timeouts, circuit breakers, rate limiting, and more to build robust and resilient microservices.
- **Resource-safety** - ZIO provides a resource system that ensures resources are acquired and released safely. With [ZIO Scopes](https://zio.dev/reference/resource/scope/) and also [scoped Layers](https://zio.dev/reference/resource/scope/#converting-resources-into-other-zio-data-types), we can manage resources in a structured way.
- **ZIO Aspect and Middlewares**- Both ZIO and ZIO HTTP support the idea of aspects and middlewares, which can be used to add cross-cutting concerns such as logging, metrics, authentication, and more to your services.
- **Modularity**- With [service pattern](https://zio.dev/reference/service-pattern/) in ZIO, we can define our services along with their dependencies, and finally, we can start the service with its dependencies. This pattern is useful for building modular and testable services.
