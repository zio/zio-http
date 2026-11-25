---
id: index
title: "Request-scoped Context Management"
sidebar_label: "Overview"
---

When building web applications with ZIO HTTP, you often need to share contextual information across different layers of your request processing pipeline. This might include user authentication data, correlation IDs for distributed tracing, session information, or request metadata. The challenge is making this data available throughout the request lifecycle without threading it through every function parameter or resorting to global mutable state.

ZIO HTTP offers two complementary approaches to managing request-scoped context, each with its own strengths, trade-offs, and use-cases. Understanding when to use each approach will help you build applications that are both maintainable and type-safe:

1. **[ZIO Environment with HandlerAspect](zio-environment.md)** leverages ZIO's type-safe dependency injection system to propagate request-scoped context through the `HandlerAspect` stack and finally the handlers. `HandlerAspect` produces typed context values that become part of the ZIO environment, making them accessible to handlers via `ZIO.service` or with the `withContext` DSL. This approach provides compile-time guarantees that all required context is present, catching missing dependencies before your code ever runs.

    The ZIO environment approach excels when compile-time safety is paramount, and it is specifically used for passing context from middlewares to handlers. The type system ensures that handlers explicitly declare their context requirements, making it impossible to forget to apply necessary middleware. This catches entire classes of runtime errors at compile time. The approach also provides better documentation through types—when you see `Handler[User & RequestId, ...]`, you immediately know what context or service the handler requires. 

2. **[RequestStore](request-store.md)** provides a FiberRef-based storage mechanism that acts like a request-scoped key-value store. You can store and retrieve typed values at any point during request processing without explicit parameter passing. The data is automatically isolated per request and cleaned up when the request completes.

   `RequestStore` shines when you don't need compile-time type safety. Unlike the previous approach, the `RequestStore` is not tied to the middleware stack, allowing you to store and retrieve context at any point of the request lifecycle without changing handler signatures. This is helpful when you don't want to pass the context through every layer explicitly, and you don't need compile-time guarantees about context presence. It's also ideal when you're working with legacy code or integrating with systems where compile-time type safety is less critical than ease of integration. The pattern feels familiar to developers coming from other web frameworks that use context managers with thread-local storage.
