# Middleware

Middlewares are transformations that one can apply on any Http to produce a new one. 
They can modify requests and responses and also transform them into more concrete domain entities.

Middleware is simply a function that takes one `Http` as a parameter and returns another `Http` `f(Http) => Http` elaborated below in terms of type parameters "In" and "Out"

```scala

type Middleware[R, E, AIn, BIn, AOut, BOut] = Http[R, E, AIn, BIn] => Http[R, E, AOut, BOut]

```

* `AIn` and `BIn` are type params of the input `Http`
* `AOut` and `BOut` are type params of the output `Http`

To summarize 
- A middleware is a wrapper around `HTTP` that provides a means of manipulating the Request sent to the service, and/or the Response returned by the service. 
- For example, an "Authentication" middleware, can prevent the service from being invoked and respond with "401 Not Authorized".

## Create Middleware

### Middleware that does nothing

```scala

val middleware = Middleware.identity

```

### Middleware that always succeeds

creates a middleware that always returns the same response and never fails.

```scala

val middleware = Middleware.succeed(1)

```

### Middleware that always fails

creates a middleware that always returns the same response and always fails.

```scala

val middleware = Middleware.fail("error")

```

### Middleware from a partial function

- Using `collect` middleware using a specified function

```scala

val middleware = Middleware.collect[Request](_ => Middleware.addHeaders(Headers("a", "b")))

```

- Using `collectZIO` middleware using specified effect function

```scala

val middleware = Middleware.collectZIO[Request](_ => ZIO.succeed(Middleware.addHeaders(Headers("a", "b"))))
  
```

### Middleware using transformation functions

We can use `intercept` or `interceptZIO` to create a new middleware using transformation functions

```scala

  val middleware = Middleware.intercept[String, String](_.toInt + 2)((_, a) => a + 3)
  val mid = Middleware.interceptZIO[Int, Int](i => UIO(i * 10))((i, j) => UIO(i + j))

```

### Middleware using codec

codec takes two functions `decoder: AOut => Either[E, AIn]` and `encoder: BIn => Either[E, BOut]`

The below snippet takes two functions:
- decoder function to decode Request to String 
- encoder function to encode String to Response

```scala

val middleware = Middleware.codec[Request,String](r => Right(r.method.toString()),s => Right(Response.text(s)))

```

### Middleware from an HttpApp

- Using `fromHttp` with a specified HttpApp

```scala

val app = Http.succeed("Hello World!")
val middleware = Middleware.fromHttp(app)

```

## Composition of middlewares

Middlewares can be composed using several special operators:

### Using `++`

`++` is an alias for `combine`. It combines that operates on the same input and output types into one.

```scala

// print debug info about request and response
Middleware.debug ++
// add static header
Middleware.addHeader("X-Environment", "Dev") 

```

### Using `<>`

`<>` is an alias for `orElse`. While using `<>`, if the first middleware fails, the second middleware will be evaluated, ignoring the result from the first.

```scala

// print debug info about request and response
 Middleware.fail("error") ++
// add static header
Middleware.addHeader("X-Environment", "Dev") 

```

### Using `>>>`

`>>>` is an alias for `andThen`. Creates a new middleware that passes the output Http of the current middleware as the input to the provided middleware.

```scala

val middleware = Middleware.codec[Int, Int](decoder = a => Right(a + 1), encoder = b => Right(b + 1))
middleware >>> middleware

```

## Transforming Middlewares

### Transforming the output of output `Http`

We can use `flatMap` or  `map` or `mapZIO` for transforming the output type of output Http

```scala

val middleware = Middleware.succeed(3)

val mid1 = middleware.map((i: Int) => i.toString)
val mid2= middleware.mapZIO((i: Int) => ZIO.succeed(s"$i"))
val mid3 = middleware.flatMap((m: Int) => Middleware.succeed(m.toString))

```

### Transforming the Input of the output `Http`

We can use `contramap` or `contramapZIO` for transforming the input type of the output `Http`

```scala

val middleware = Middleware.codec[Int, Int](decoder = a => Right(a + 1), encoder = b => Right(b + 1))
val mid1 = middleware.contramap[String](_.toInt)
val mid2 = middleware.contramapZIO[String](a => UIO(a.toInt))

```


## Conditional application of middlewares

- Using `when`, only if the condition function evaluates to true

```scala

val mid = Middleware.succeed("yes")
val m = mid.when[String]((str: String) => str.length > 2)

```

- Using `whenZIO`, only if the condition effectful function evaluates

```scala

val middleware = Middleware.succeed("yes")
val mid = middleware.whenZIO[Any, Nothing, String]((str: String) => UIO(str.length > 2))  

```

Logical operators to decide which middleware to select based on the predicate:

- Using `ifThenElse` with a specified HttpApp

```scala

val mid = Middleware.ifThenElse[Int](_ > 5)(
            isTrue = i => Middleware.succeed(i + 1),
            isFalse = i => Middleware.succeed(i - 1)
          )

```
- Using `ifThenElseZIO` for specified effectful encoder and decoder

```scala

val mid = Middleware.ifThenElseZIO[Int](i => UIO(i > 5))(
          isTrue = i => Middleware.succeed(i + 1),
          isFalse = i => Middleware.succeed(i - 1),
        ) 

```

## Example of a middleware

<details>
<summary><b>Detailed example </b></summary>

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

## A few "Out of the box" middlewares
### Basic Auth
### CORS
### CSRF

