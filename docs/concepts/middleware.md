---
id: middleware
title: Middleware
---

## Middleware Concepts in ZIO HTTP

Middleware plays a crucial role in addressing cross-cutting concerns without cluttering the core business logic. Cross-cutting concerns are aspects of a program that are linked to many parts of the application but are not directly related to its primary function. Examples of cross-cutting concerns include logging, timeouts, retries, authentication, and more.

### The Need for Middleware

Before introducing middleware, let's understand the challenges that arise when dealing with cross-cutting concerns in our application. Consider the following example, where we have two endpoints within an `HttpApp`:

1. GET a single user by id
2. GET all users

The core business logic for each endpoint is interwoven with concerns like request validation, logging, timeouts, retries, and authentication. As the application grows, the code becomes polluted with boilerplate, making it difficult to maintain and test. Furthermore, any changes to these concerns might require updates in multiple places, leading to code duplication and violation of the "Separation of concerns" principle.

### The Concept of Middleware

Middleware in ZIO HTTP enables a modular approach to handle cross-cutting concerns in an application. Middleware can be thought of as reusable components that can be composed together to address different aspects of the application. The idea is to separate these concerns from the core business logic, making the codebase cleaner, more maintainable, and easier to test.

### Composing Middleware

Middleware can be combined using the `++` operator, which applies the middlewares from left to right. This composition allows us to build complex middleware bundles that cater to multiple aspects of the application. By composing middleware functions, we can attach them to specific routes or the entire `HttpApp` in one go, keeping the core business logic clean and free from boilerplate.

### Middleware in Action

Let's look at an example of middleware usage in ZIO HTTP. Assume we have a basic `HttpApp` with a single endpoint to greet a user. We want to apply two middlewares: one for basic authentication and another to add a custom header indicating the environment.

```scala
val userApp = Routes(
  Method.GET / "user" / string("name") / "greet" -> handler { (name: String, req: Request) =>
    Response.text(s"Welcome to the ZIO party! ${name}")
  }
).toHttpApp

val basicAuthMW = Middleware.basicAuth("admin", "admin")
val patchEnv = Middleware.addHeader("X-Environment", "Dev")

val appWithMiddleware = userApp @@ (basicAuthMW ++ patchEnv)
```

Here, we use the `basicAuth` middleware provided by ZIO HTTP to secure the endpoint with basic authentication. Additionally, we add a custom header to the response indicating the environment is "Dev" using the `addHeader` middleware.

### Advantages of Middleware

Using middleware offers several benefits:

1. **Readability**: By removing boilerplate code from the core business logic, the application code becomes more readable and easier to understand.

2. **Modularity**: Middleware allows us to manage cross-cutting concerns independently, facilitating easier maintenance and updates.

3. **Testability**: With middleware, we can test concerns independently, which simplifies testing and ensures the core business logic remains clean.

4. **Reusability**: Middleware can be composed and reused across different routes and applications, promoting code reuse and consistency.

Middleware is a powerful concept in ZIO HTTP that enables developers to handle cross-cutting concerns in a modular and organized manner. By composing middleware functions, developers can keep the core business logic clean and focus on writing maintainable, readable, and efficient code.