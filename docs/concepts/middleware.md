---
id: middleware
title: Middleware
---

Middleware plays a crucial role in addressing cross-cutting concerns without cluttering the core business logic. Cross-cutting concerns are aspects of a program that are linked to many parts of the application but are not directly related to its primary function. Examples of cross-cutting concerns include logging, timeouts, retries, authentication, and more.

## The Need for Middleware

Before introducing middleware, let's understand the challenges that arise when dealing with cross-cutting concerns in our application. Consider the following example, where we have two endpoints within an `HttpApp`:

1. GET a single user by id
2. GET all users

```scala mdoc:invisible
import zio._
trait Data {
  def json: String
}

object dbService {
  def lookupUsersById(id: Int):     Task[Data] = ???
  def paginatedUsers(pageNum: Int): Task[Data] = ???
}


object MyAuthService {
    def doAuth(request: Any): Task[Unit] = ???
}

def logRequest(request: Any): Task[Unit] = ???
def doSomething(request: Any): Task[Unit] = ???
def logResponse(response: Any): Task[Unit] = ???

```

```scala mdoc:compile-only
import zio.http._

val routes = Routes(
  Method.GET / "users" / int("id") -> 
    handler { (id: Int, req: Request) =>
      // core business logic  
      dbService.lookupUsersById(id).map(u => Response.json(u.json))
    },
  Method.GET / "users" ->
    handler {
      // core business logic  
      val pageNum: Int = ???
      dbService.paginatedUsers(pageNum).map(u => Response.json(u.json))
    }
)
```

For both of our example endpoints, our core business logic gets buried under boilerplate like this:

```scala mdoc:invisible
import zio.http._
val request = {}
val id: Int = 0
val pageNum: Int = 0
```

```scala mdoc:compile-only
(for {
    // validate user
    _    <- MyAuthService.doAuth(request)
    // log request
    _    <- logRequest(request)
    // core business logic
    resp <- dbService.lookupUsersById(id).map(u => Response.json(u.json))
    // log response
    _    <- logResponse(resp)                
} yield resp)
        .timeout(2.seconds)
        .retryN(5)
```

Imagine repeating this for all our endpoints!!!

So there are several problems with this approach:

* We are dangerously coupling our business logic with cross-cutting concerns (like applying timeouts)
* Also, addressing these concerns will require updating code for every single route in the system. For 100 routes we will need to repeat 100 timeouts! For example, any change related to a concern like the logging mechanism from logback to log4j2 may cause changing signature of `log(..)` function in 100 places.
* On the other hand, this also makes testing core business logic more cumbersome.

The core business logic for each endpoint is interwoven with concerns like request validation, logging, timeouts, retries, and authentication. As the application grows, the code becomes polluted with boilerplate, making it difficult to maintain and test. Furthermore, any changes to these concerns might require updates in multiple places, leading to code duplication and violation of the "Separation of concerns" principle.

This can lead to a lot of boilerplate clogging our neatly written endpoints affecting readability, thereby leading to increased maintenance costs.

## Solution: Aspect Oriented Programming

If we refer to Wikipedia for the definition of an "[Aspect](https://en.wikipedia.org/wiki/Aspect_(computer_programming))" we can glean the following points:

* An aspect of a program is a feature linked to many other parts of the program (**_most common example, logging_**),
* But it is not related to the program's primary function (**_core business logic_**)
* An aspect crosscuts the program's core concerns (**_for example logging code intertwined with core business logic_**),
* Therefore, it can violate the principle of "separation of concerns" which tries to encapsulate unrelated functions. (**_Code duplication and maintenance nightmare_**)

Or in short, an aspect is a common concern required throughout the application, and its implementation could lead to repeated boilerplate code and in violation of the principle of separation of concerns.

There is a paradigm in the programming world called [aspect-oriented programming](https://en.wikipedia.org/wiki/Aspect-oriented_programming) that aims for modular handling of these common concerns in an application.

Some examples of common "aspects" required throughout the application
- logging,
- timeouts (preventing long-running code)
- retries (or handling flakiness for example while accessing third party APIs)
- authenticating a user before using the REST resource (basic, or custom ones like OAuth / single sign-on, etc).

This is where middleware comes to the rescue.

## Middleware and Aspect Oriented Programming

Middleware in ZIO HTTP enables a modular approach to handle cross-cutting concerns in an application. They use aspect-oriented programming to address common concerns without cluttering the core business logic. They can be thought of as reusable components that can be composed together to address different aspects of the application. The idea is to separate these concerns from the core business logic, making the codebase cleaner, more maintainable, and easier to test.

Observe, how we can address multiple cross-cutting concerns using neatly composed middlewares, in a single place:

```scala mdoc:silent
import zio._
import zio.http._

// compose basic auth, request/response logging, timeouts middlewares
val composedMiddlewares = Middleware.basicAuth("user","pw") ++ 
    Middleware.debug ++ 
    Middleware.timeout(5.seconds) 
```

And then we can attach our composed bundle of middlewares to an Http using `@@`

```scala mdoc:compile-only
import zio.http._

val routes = Routes(
  Method.GET / "users" / int("id") -> 
    handler { (id: Int, req: Request) =>
      doSomething(req)
      // core business logic  
      dbService.lookupUsersById(id).map(u => Response.json(u.json))
    },
  Method.GET / "users" ->
    handler {
      // core business logic  
      dbService.paginatedUsers(pageNum).map(u => Response.json(u.json))
    }
) @@ composedMiddlewares // attach composedMiddlewares to the routes using @@
```

## Composing Middlewares

Middleware can be combined using the `++` operator, which applies the middlewares from left to right. This composition allows us to build complex middleware bundles that cater to multiple aspects of the application. By composing middleware functions, we can attach them to specific routes or the entire `HttpApp` in one go, keeping the core business logic clean and free from boilerplate.

## Middleware in Action

Let's look at an example of middleware usage in ZIO HTTP. Assume we have a basic `HttpApp` with a single endpoint to greet a user. We want to apply two middlewares: one for basic authentication and another to add a custom header indicating the environment.

```scala mdoc:silent
import zio.http._

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

It's time to start the server now:

```scala mdoc:silent
Server.serve(appWithMiddleware).provide(Server.default)
```

Fire a curl request with an incorrect user/password combination:

```
curl -i --user admin:wrong http://localhost:8080/user/admin/greet

HTTP/1.1 401 Unauthorized
www-authenticate: Basic
X-Environment: Dev
content-length: 0
```

We notice in the response that first basicAuth middleware responded `HTTP/1.1 401 Unauthorized` and then patch middleware attached a `X-Environment: Dev` header.

## Advantages of Middleware

Using middleware offers several benefits:

1. **Readability**: By removing boilerplate code from the core business logic, the application code becomes more readable and easier to understand.

2. **Modularity**: Middleware allows us to manage cross-cutting concerns independently, facilitating easier maintenance and updates. For example,
    * Replacing the logging mechanism from logback to log4j2 will require a change in one place, the logging middleware.
    * Replacing the authentication mechanism from OAuth to single sign-on will require changing the auth middleware

3. **Testability**: With middleware, we can test concerns independently, which simplifies testing and ensures the core business logic remains clean.

4. **Reusability**: Middleware can be composed and reused across different routes and applications, promoting code reuse and consistency.

Middleware is a powerful concept in ZIO HTTP that enables developers to handle cross-cutting concerns in a modular and organized manner. By composing middleware functions, developers can keep the core business logic clean and focus on writing maintainable, readable, and efficient code.
