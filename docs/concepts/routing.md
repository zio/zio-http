---
id: routing
title: "Routing"
---

# Routing

HTTP routing is a fundamental concept in ZIO HTTP. It allows developers to define how incoming requests are matched to specific handlers for processing and generating responses. ZIO HTTP facilitates routing through the `Http.collect` method, which accepts requests and produces corresponding responses. Pattern matching on routes is built-in, enabling developers to create precise routing logic.

## Key Concepts of Routing:

### Purpose of Routing

- Routing determines how incoming HTTP requests are directed to specific handlers based on the request's path and method.
- It enables the organization of application logic by mapping different endpoints to their respective handlers.

### Role of Routing in ZIO HTTP

- Routes are defined using the `Http.collect` method, which accepts a partial function from requests to responses.
- ZIO HTTP supports pattern matching on routes, allowing for precise and type-safe routing logic.
- Routes can be combined to create complex routing structures, enabling modular and maintainable application design.

### Path Matching

Routes in ZIO HTTP can be defined to match specific paths and HTTP methods, allowing to create precise routing logic tailored to the application's needs. Path segments can be specified using literals or extracted as parameters for dynamic routing.

### Combining Routes

ZIO HTTP allows you to combine multiple routes using the `++` operator. This enables you to build complex routing structures for your application.