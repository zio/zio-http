---
sidebar_position: "8"
---
# Middleware
Before introducing middleware, let us understand why they are needed.

## Need for middlewares and handling "aspects"

If we refer to the wikipedia for the definition of an "[Aspect](https://en.wikipedia.org/wiki/Aspect_(computer_programming))" we can glean following points.

* An aspect of a program is a feature linked to many other parts of the program (**_most common example logging_**)., 
* But it is not related to the program's primary function (**_core business logic_**) 
* An aspect crosscuts the program's core concerns (**_for example logging code intertwined with core business logic_**),  
* Therefore, it can violate principle of "separation of concerns" that tries to encapsulate unrelated functions. (**_Code duplication and maintenance nightmare_**)

Or in short, aspect is a common concern required throughout the application, and its implementation could lead to repeated boilerplate code and in violation of the principle of separation of concerns.
There is a paradigm in programming world called [aspect-oriented programming](https://en.wikipedia.org/wiki/Aspect-oriented_programming) that aims for modular handling of these common concerns in an application. 

Some examples of common "aspects" required throughout the application
- logging,
- timeouts (preventing long-running code)
- retries (or handling flakiness for example while accessing third party APIs)
- authenticating a user before using the REST resource (basic, or custom ones like oauth / single sign-on etc).

Consider following example where we have two endpoints within HttpApp 
* GET user by id and 
* GET multiple users paginated
```scala
  private val app = Http.collectZIO[Request] {
    case Method.GET -> !! / "users" / id =>
      // core business logic  
      dbService.lookupUsersById(id).map(Response.json(_.json))
    case Method.GET -> !! / "users"    =>
      // core business logic  
      dbService.paginatedUsers(pageNum).map(Response.json(_.json))
  }
```
#### The polluted code violates the principle of "Separation of concerns"

Suppose we want to code above-mentioned aspects like
  * basicAuth
  * request logging
  * response logging
  * timeout and retry

for both our example endpoints, our core business logic gets buried under boilerplate like this

```scala
            (for {
                // validate user
                _    <- MyAuthService.doAuth(request)
                // log request
                _    <- logRequest(request)
                // core business logic
                user <- dbService.lookupUsersById(id).map(Response.json(_.json))
                resp <- Response.json(user.toJson)
                // log response
                _    <- logResponse(resp)                
            } yield resp)
                    .timeout(2.seconds)
                    .retryN(5)
```
Imagine repeating this for all our end points!!! 

So there are two problems with this approach
* We are dangerously coupling our business logic with a lower level concern (like applying timeouts)
* Also, we will have to do it for every single route in the system. For 100 routes we will need to repeat 100 timeouts!!! 

This can lead to a lot of boilerplate clogging our neatly written endpoints affecting readability, thereby leading to maintenance cost.

This is where middleware comes to the rescue. 
Using middlewares we can compose out-of-the-box middlewares (or our custom middlewares) to address the above-mentioned concerns using ++ and @@ operators as shown below.

#### Cleaned up code using middleware to address cross-cutting concerns like auth, request/response logging, etc.
```scala
// compose basic auth, request/response logging, timeouts middlewares
val composedMiddlewares = Middleware.basicAuth("user","pw") ++ 
        Middleware.debug ++ 
        Middleware.timeout(5 seconds) 

private val app = Http.collectZIO[Request] {
  case Method.GET -> !! / "users" / id =>
    // core business logic  
    dbService.lookupUsersById(id).map(Response.json(_.json))
  case Method.GET -> !! / "users"    =>
    // core business logic  
    dbService.paginatedUsers(pageNum).map(Response.json(_.json))
} @@ composedMiddlewares // attach composedMiddlewares to the app using @@
```
Observe how we gained following benefits by using middlewares
* **Readability**: de-cluttering business logic.
* **Modularity**: we can manage aspects independently without making changes in 100 places (for example replacing logging mechanism from logback to log4j2 will require change in one place, the logging middleware).
* **Testability**: we can test our aspects independently.

## Middleware in zio-http

A middleware helps in addressing common cross-cutting concerns without duplicating boilerplate code.

#### Revisiting HTTP 
[`Http`](https://dream11.github.io/zio-http/docs/v1.x/dsl/http) is the most fundamental type for modelling Http applications

```Http[-R, +E, -A, +B]``` is equivalent to ```(A) => ZIO[R, Option[E], B]``` where

* `R` type of Environment 
* `E` type of the Error when function fails with Some[E]
* `A` is the type of the function parameter
* `B` type of the result when function succeeds 

Middleware is simply a function that takes one Http as a parameter and returns another Http,

```Http => Http```

So, a middleware represents transformation f1 => f2
 
They can modify requests and responses and also transform them into more concrete domain entities.
```scala
type Middleware[R, E, AIn, BIn, AOut, BOut] = Http[R, E, AIn, BIn] => Http[R, E, AOut, BOut]
```
* `AIn` and `BIn` are type params of the input `Http`
* `AOut` and `BOut` are type params of the output `Http`

This can also be seen as 
```
AIn => ZIO[R, Option[E], BIn]  ---transformed by middleware--->  AOut => ZIO[R, Option[E], BOut]
```
**HttpApp** is a specialized Http with `Request` and `Response` as input and output
```scala
type HttpApp[-R,+E] = Http[R, E, Request, Response]
```
#### Attaching middleware to Http
`@@` operator is used to attach a middleware to an Http. Example below shows a middleware attached to an HttpApp
```scala
val app = Http.collect[Request] {
  case Method.GET -> !! / name => Response.text(s"Hello $name")
}
val appWithMiddleware = app @@ Middleware.debug
```
Logically the code above translates to `debugFunction(app)`
#### A simple middleware example
Let us consider a simple example using out-of-the-box middleware called ```runAfter``` and ```addHeader```
We will write a middleware which will attach a custom header to the response. 

Start with imports
```scala
import zhttp.http._
import zhttp.service.Server
import zio.console.{putStrLn}
import zio.{App, ExitCode, URIO}
```
We create a middleware that appends additional header to the response indicating whether it is a Dev/Prod/Staging environment.
```scala
lazy val patchEnv = Middleware.addHeader("X-Environment", "Dev")
```
A test HttpApp with attached middleware
```scala
val app = Http.collect[Request] {
  case Method.GET -> !! / name => Response.text(s"Hello $name")
}
val appWithMiddleware = app @@ patchEnv
```
Start the server 
```scala
override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
  Server.start(8090, appWithMiddleware).exitCode
```
Fire a curl request and we see an additional header added to the response indicating the "Dev" environment
```
curl -i http://localhost:8090/name

HTTP/1.1 200 OK
content-type: text/plain
X-Environment: Dev
content-length: 10
Endpoint 1
```
## Creating Middleware

Refer to [Middleware.scala](https://github.com/dream11/zio-http/blob/main/zio-http/src/main/scala/zhttp/http/Middleware.scala) for various ways of creating a middleware.

Again remembering that a "middleware" is just a **_transformative function_**. There are ways of creating such transformative functions:  
* **identity**: works like an [identity function](https://en.wikipedia.org/wiki/Identity_function) in mathematics
  `f(x) = x`.
  It returns the same `Http` as input without doing any modification
```scala
val identityMW: Middleware[Any, Nothing, Nothing, Any, Any, Nothing] = Middleware.identity
app @@ identityMW // no effect on the http app.
```
* **succeed** creates a middleware that always returns the output `Http` that succeeds with the given value and never fails.

```scala
val middleware: Middleware[Any, Nothing, Nothing, Any, Any, Int] = Middleware.succeed(1)
```
* **fail** creates a middleware that always returns the output `Http` that always fails.

```scala
val middleware: Middleware[Any, String, Nothing, Any, Any, Nothing] = Middleware.fail("error")
```
* **collect** creates middleware using a specified function

```scala
val middleware: Middleware[Any, Nothing, Request, Response, Request, Response] = Middleware.collect[Request](_ => Middleware.addHeaders(Headers("a", "b")))
```
* **collectZIO** creates middleware using a specified effect function

```scala
val middleware: Middleware[Any, Nothing, Request, Response, Request, Response] = Middleware.collectZIO[Request](_ => ZIO.succeed(Middleware.addHeaders(Headers("a", "b"))))
```
* **codec** takes two functions `decoder: AOut => Either[E, AIn]` and `encoder: BIn => Either[E, BOut]`

The below snippet takes two functions:
  - decoder function to decode Request to String
  - encoder function to encode String to Response

```scala
val middleware: Middleware[Any, Nothing, String, String, Request, Response] = Middleware.codec[Request,String](r => Right(r.method.toString()), s => Right(Response.text(s)))
```
* **fromHttp** creates a middleware with output `Http` as specified `http`

```scala
val app: Http[Any, Nothing, Any, String] = Http.succeed("Hello World!")
val middleware: Middleware[Any, Nothing, Nothing, Any, Request, Response] = Middleware.fromHttp(app)
```

## Combining middlewares

Middlewares can be combined using several special operators like `++`, `<>` and `>>>`

### Using `++` combinator

`++` is an alias for `combine`. It combines two middlewares **_without changing their input/output types (`AIn` = `AOut` / `BIn` = `BOut`)_**

For example, if we have three middlewares f1, f2, f3

f1 ++ f2 ++ f3 applies on an `http`, from left to right with f1 first followed by others, like this 
```scala
  f3(f2(f1(http)))
```
#### A simple example using `++` combinator
Start with imports
```scala
import zhttp.http.Middleware.basicAuth
import zhttp.http._
import zhttp.service.Server
import zio.console.putStrLn
import zio.{App, ExitCode, URIO}
```
A user app with single endpoint that welcomes a user
```scala
val userApp: UHttpApp = Http.collect[Request] { case Method.GET -> !! / "user" / name / "greet" =>
  Response.text(s"Welcome to the ZIO party! ${name}")
}
```
A basicAuth middleware with hardcoded user pw and another patches response with environment value 
```scala
val basicAuthMW = basicAuth("admin", "admin")
lazy val patchEnv = Middleware.addHeader("X-Environment", "Dev")
// apply combined middlewares to the userApp
val appWithMiddleware = userApp @@ (basicAuthMW ++ patchEnv)
```
Start the server
```scala
override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
  Server.start(8090, appWithMiddleware).exitCode
```
Fire a curl request with incorrect user/password combination
```
curl -i --user admin:wrong http://localhost:8090/user/admin/greet

HTTP/1.1 401 Unauthorized
www-authenticate: Basic
X-Environment: Dev
content-length: 0
```
We notice in the response that first basicAuth middleware responded `HTTP/1.1 401 Unauthorized` and then patch middleware attached a `X-Environment: Dev` header. 

### Using `>>>`
`>>>` is an alias for `andThen` and similar to `++` with one BIG difference **_input/output types can be different (`AIn`≠ `AOut` / `BIn`≠ `BOut`)_**  
Whereas, in case of `++` types remain same (horizontal composition).

For example, if we have three middlewares f1, f2, f3

f1 >>> f2 >>> f3 applies on an `http`, sequentially feeding an http to f1 first followed by f2 and f3.

f1(http) => http1
f2(http1) => http2

### Using `<>` combinator
`<>` is an alias for `orElse`. While using `<>`, if the output `Http` of the first middleware fails, the second middleware will be evaluated, ignoring the result from the first.
#### A simple example using `<>`
```scala
val middleware: Middleware[Any, Nothing, Request, Response, Request, Response] = Middleware.fail("error") <> Middleware.addHeader("X-Environment", "Dev")
```
#### other operators
* **contraMap**,**contraMapZIO**,**delay**,**flatMap**,**flatten**,**map**: which are obvious as their name implies.

* **race** to race middlewares
* **runAfter** and **runBefore** to run effect before and after
* **when** to conditionally run a middleware (input of output Http meets some criteria)

## Transforming Middlewares (some advanced examples)

### Transforming the output of the output `Http`

- We can use `flatMap` or  `map` or `mapZIO` for transforming the output type of output Http

```scala
val middleware: Middleware[Any, Nothing, Nothing, Any, Any, Int] = Middleware.succeed(3)

val mid1: Middleware[Any, Nothing, Nothing, Any, Any, String] = middleware.map((i: Int) => i.toString)
val mid2: Middleware[Any, Nothing, Nothing, Any, Any, String] = middleware.mapZIO((i: Int) => ZIO.succeed(s"$i"))
val mid3: Middleware[Any, Nothing, Nothing, Any, Any, String] = middleware.flatMap((m: Int) => Middleware.succeed(m.toString))
```

- We can use `intercept` or `interceptZIO` to create a new middleware using transformation functions, which changes the output type of the output `Http` keeping input the same.
  
  The below snippet takes two functions:
  - (incoming: A => S)
  - (outgoing: (B, S) => BOut) 
  
```scala
val middleware: Middleware[Any, Nothing, String, String, String, Int] = Middleware.intercept[String, String](_.toInt + 2)((_, a) => a + 3)
  
val mid: Middleware[Any, Nothing, Int, Int, Int, Int] = Middleware.interceptZIO[Int, Int](i => UIO(i * 10))((i, j) => UIO(i + j))
```

### Transforming the Input of the output `Http`

We can use `contramap` or `contramapZIO` for transforming the input type of the output `Http`

```scala
val middleware: Middleware[Any, Nothing, Int, Int, Int, Int] = Middleware.codec[Int, Int](decoder = a => Right(a + 1), encoder = b => Right(b + 1))

val mid1: Middleware[Any, Nothing, Int, Int, String, Int] = middleware.contramap[String](_.toInt)
val mid2: Middleware[Any, Nothing, Int, Int, String, Int] = middleware.contramapZIO[String](a => UIO(a.toInt))
```

## Conditional application of middlewares

- `when` applies middleware only if the condition function evaluates to true

```scala
val middleware: Middleware[Any, Nothing, Nothing, Any, Any, String] = Middleware.succeed("yes")
val mid: Middleware[Any, Nothing, Nothing, Any, String, String] = middleware.when[String]((str: String) => str.length > 2)
```

-`whenZIO` applies middleware only if the condition function(with effect) evaluates

```scala
val middleware: Middleware[Any, Nothing, Nothing, Any, Any, String] = Middleware.succeed("yes")
val mid: Middleware[Any, Nothing, Nothing, Any, String, String] = middleware.whenZIO[Any, Nothing, String]((str: String) => UIO(str.length > 2))
```

Logical operators to decide which middleware to select based on the predicate:

- Using `ifThenElse` 

```scala
val mid: Middleware[Any, Nothing, Nothing, Any, Int, Int] = Middleware.ifThenElse[Int](_ > 5)(
    isTrue = i => Middleware.succeed(i + 1),
    isFalse = i => Middleware.succeed(i - 1)
  )
```
- Using `ifThenElseZIO` 

```scala
val mid: Middleware[Any, Nothing, Nothing, Any, Int, Int] = Middleware.ifThenElseZIO[Int](i => UIO(i > 5))(
    isTrue = i => Middleware.succeed(i + 1),
    isFalse = i => Middleware.succeed(i - 1),
  )
```

## A complete example of a middleware

<details>
<summary><b>Detailed example showing "debug" and "addHeader" middlewares</b></summary>

```scala
    import zhttp.http._
    import zhttp.http.middleware.HttpMiddleware
    import zhttp.service.Server
    import zio.clock.{Clock, currentTime}
    import zio.console.Console
    import zio.duration._
    import zio.{App, ExitCode, URIO, ZIO}
    
    import java.io.IOException
    import java.util.concurrent.TimeUnit
    
     val app: HttpApp[Clock, Nothing] = Http.collectZIO[Request] {
       // this will return result instantly
       case Method.GET -> !! / "text"         => ZIO.succeed(Response.text("Hello World!"))
       // this will return result after 5 seconds, so with 3 seconds timeout it will fail
       case Method.GET -> !! / "long-running" => ZIO.succeed(Response.text("Hello World!")).delay(5 seconds)
     }

    val middlewares: HttpMiddleware[Console with Clock, IOException] =
       // print debug info about request and response
       Middleware.debug ++
       // add static header
       Middleware.addHeader("X-Environment", "Dev") ++   

   override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
       Server.start(8090, (app @@ middlewares)).exitCode
```
   
</details>   

### A few "Out of the box" middlewares
- [Basic Auth](https://dream11.github.io/zio-http/docs/v1.x/examples/advanced-examples/middleware_basic_auth) 
- [CORS](https://dream11.github.io/zio-http/docs/v1.x/examples/advanced-examples/middleware_cors)
- [CSRF](https://dream11.github.io/zio-http/docs/v1.x/examples/advanced-examples/middleware_csrf)

