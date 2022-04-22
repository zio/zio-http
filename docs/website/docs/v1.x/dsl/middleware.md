---
sidebar_position: "8"
---
# Middleware
Before introducing middleware, let us understand why they are needed.

## Need for middlewares and handling "aspects"

The definition of an "Aspect" in the programming world provided by Wikipedia
```
An aspect of a program is a feature linked to many other parts of the program, but which is not related to the program's primary function. 
An aspect crosscuts the program's core concerns, therefore violating its separation of concerns that tries to encapsulate unrelated functions.
```

Or in short, aspect is a common concern required throughout the application, an implementation could lead to repeated boilerplate code and in violation of the principle of separation of concerns.

Some examples of common "aspects" required throughout the application
- logging,
- timeouts (preventing long-running code)
- retries (or handling flakiness for example while accessing third party APIs)
- authenticating a user before using the REST resource.

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
Imagine repeating this for all our end points. So there are two problems with this approach

* We are dangerously coupling our business logic with a lower level concern (like applying timeouts)
* Also, we will have to do it for every single route in the system. For 100 routes we will need to repeat 100 timeouts!!! 

This can lead to a lot of boilerplate clogging our neatly written endpoints affecting readability.

This is where middleware comes to the rescue. 
Using middlewares we can compose out-of-the-box middlewares (or our custom middlewares) to address the above-mentioned concerns using ++ and @@ operators as shown below.

#### Cleaned up code using middleware to address cross-cutting concerns like auth, req/resp logging, etc.
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
Observe how we avoided cluttering our business logic using middlewares.
## Middleware in zio-http

A middleware helps in addressing common cross-cutting concerns without duplicating boilerplate code.

#### Revisiting HTTP 
[`Http`](https://dream11.github.io/zio-http/docs/v1.x/dsl/http) is the most fundamental type for modelling Http applications

```Http[-R, +E, -A, +B]``` is equivalent to ```(A) => ZIO[R, Option[E], B]``` where

* `R` type of Environment 
* `E` type of Error when function fails
* `A` is the type params of the function argument
* `B` type of result when function succeeds 

Middleware is simply a function that takes one Http as a parameter and returns another Http,

`Http => Http`

So, a middleware represents transformation f1 => f2
 
They can modify requests and responses and also transform them into more concrete domain entities.
```scala
type Middleware[R, E, AIn, BIn, AOut, BOut] = Http[R, E, AIn, BIn] => Http[R, E, AOut, BOut]
```
* `AIn` and `BIn` are type params of the input `Http`
* `AOut` and `BOut` are type params of the output `Http`

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
val testApp = Http.collect[Request] {
  case Method.GET -> !! / "endpoint1" => Response.text(s"Endpoint 1")
}
val testAppWithMiddleware = testApp @@ patchEnv
```
Start the server 
```scala
override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
  Server.start(8090, testAppWithMiddleware).exitCode
```
Fire a curl request and we see an additional header added to the response indicating the "Dev" environment
```
curl -i http://localhost:8090/endpoint1

HTTP/1.1 200 OK
content-type: text/plain
X-Environment: Dev
content-length: 10
Endpoint 1
```
## Create Middleware

Refer to [Middleware.scala](https://github.com/dream11/zio-http/blob/main/zio-http/src/main/scala/zhttp/http/Middleware.scala) for various ways of creating a middleware using functions like 
* identity
* succeed
* fail
* collect and collectZIO
* codec
* fromHttp

## Combining middlewares

Middlewares can be combined using several special operators like `++`, `<>` and `>>>`

### Using `++`

`++` is an alias for `combine`. It combines two middlewares without changing their input/output types (`AIn` = `AOut` / `BIn` = `BOut`)

For example, if we have three middlewares f1, f2, f3

f1 ++ f2 ++ f3 applies on an `http`, from left to right with f1 first followed by others, like this 
```scala
  f3(f2(f1(http)))
```
#### A simple example using `++`
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
val testAppWithMiddleware = userApp @@ (basicAuthMW ++ patchEnv)
```
Start the server
```scala
override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
  Server.start(8090, testAppWithMiddleware).exitCode
```
Fire a curl request with incorrect user/password combination
```
curl -i --user admin:wrong http://localhost:8090/user/admin/greet

HTTP/1.1 401 Unauthorized
www-authenticate: Basic
X-Environment: Dev
content-length: 0
```
We notice in the response that first basicAuth middleware responded `HTTP/1.1 401 Unauthorized` and then patch middleware attached a `X-Environment: Dev` 

### Using `<>`
`<>` is an alias for `orElse`. While using `<>`, if the output `Http` of the first middleware fails, the second middleware will be evaluated, ignoring the result from the first.
#### A simple example using `<>`
```scala
val middleware: Middleware[Any, Nothing, Request, Response, Request, Response] = Middleware.fail("error") <> Middleware.addHeader("X-Environment", "Dev")
```
### Using `>>>`
`>>>` is an alias for `andThen`. Creates a new middleware that passes the output `Http` of the current middleware as the input to the provided middleware.
#### A simple example using `>>>`
```scala
val middleware: Middleware[Any, Nothing, Int, Int, Int, Int] = Middleware.codec[Int, Int](decoder = a => Right(a + 1), encoder = b => Right(b + 1))
val mid: Middleware[Any, Nothing, Int, Int, Int, Int] =  middleware >>> middleware
```

## Transforming Middlewares

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

