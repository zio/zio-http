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

A middleware helps in addressing common cross-cutting concerns without writing or duplicating boilerplate code.

Middlewares are transformations that one can apply on any [`Http`](https://dream11.github.io/zio-http/docs/v1.x/dsl/http) to produce a new one. 
They can modify requests and responses and also transform them into more concrete domain entities.

Middleware is simply a function that takes one `Http` as a parameter and returns another `Http`, i.e, `Http => Http` 

```scala
type Middleware[R, E, AIn, BIn, AOut, BOut] = Http[R, E, AIn, BIn] => Http[R, E, AOut, BOut]
```

* `AIn` and `BIn` are type params of the input `Http`
* `AOut` and `BOut` are type params of the output `Http`

## Create Middleware

### Middleware that does nothing

To return the input `Http` as the 
output without doing any modification

```scala
val middleware: Middleware[Any, Nothing, Nothing, Any, Any, Nothing] = Middleware.identity
```

### Middleware that always succeeds

To create a middleware that always returns an output `Http` that succeeds with the given value and never fails.

```scala
val middleware: Middleware[Any, Nothing, Nothing, Any, Any, Int] = Middleware.succeed(1)
```

### Middleware that always fails

To create a middleware that always returns an output `Http` that always fails.

```scala
val middleware: Middleware[Any, String, Nothing, Any, Any, Nothing] = Middleware.fail("error")
```

### Middleware from a partial function

- `collect` creates middleware using a specified function, `f: PartialFunction[AOut, Middleware[R, E, AIn, BIn, AOut, BOut]]`

```scala
val middleware: Middleware[Any, Nothing, Request, Response, Request, Response] = Middleware.collect[Request](_ => Middleware.addHeaders(Headers("a", "b")))
```

- `collectZIO` creates middleware using a specified effect function, `f: PartialFunction[AOut, ZIO[R, E, Middleware[R, E, AIn, BIn, AOut, BOut]]]`

```scala
val middleware: Middleware[Any, Nothing, Request, Response, Request, Response] = Middleware.collectZIO[Request](_ => ZIO.succeed(Middleware.addHeaders(Headers("a", "b"))))
```

### Middleware using codec

`codec` creates a middleware that transforms the input `Http` using two functions `decoder: AOut => Either[E, AIn]` and `encoder: BIn => Either[E, BOut]`

The below snippet takes two functions:
- decoder function to decode Request to String 
- encoder function to encode String to Response

and creates a middleware that gives out an `Http[R,E,Request,Response]` from `Http[R,E,String,String]`

```scala
val middleware: Middleware[Any, Nothing, String, String, Request, Response] = Middleware.codec[Request,String](r => Right(r.method.toString()), s => Right(Response.text(s)))
```

### Middleware from an `Http`

- `fromHttp` creates a middleware with output `Http` as specified `Http`

```scala
val app: Http[Any, Nothing, Any, String] = Http.succeed("Hello World!")
val middleware: Middleware[Any, Nothing, Nothing, Any, Request, Response] = Middleware.fromHttp(app)
```

## Composition of middlewares

Middlewares can be composed using several special operators:

### Using `++`

`++` is an alias for `combine`. It combines that operates on the same input and output types into one.

```scala
val middleware: Middleware[Console with Clock, IOException, Request, Response, Request, Response] = Middleware.debug ++ Middleware.addHeader("X-Environment", "Dev")
```

### Using `<>`

`<>` is an alias for `orElse`. While using `<>`, if the output `Http` of the first middleware fails, the second middleware will be evaluated, ignoring the result from the first.

```scala
val middleware: Middleware[Any, Nothing, Request, Response, Request, Response] = Middleware.fail("error") <> Middleware.addHeader("X-Environment", "Dev")
```

### Using `>>>`

`>>>` is an alias for `andThen`. Creates a new middleware that passes the output `Http` of the current middleware as the input to the provided middleware.

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

## Example of a middleware

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

