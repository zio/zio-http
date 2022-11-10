---
sidebar_position: "2"
---
# Http

`Http` is a functional domain that models HTTP applications. It’s polymorphic on input and output type.

A `Http[-R, +E, -A, +B]` models a function from `A` to `ZIO[R, Option[E], B]`. When a value of type `A` is evaluated against an `Http[R,E,A,B]`, it can either succeed with a `B` , fail with a `Some[E]` or if `A` is not defined in the application, fail with `None`.

`Http` domain provides several operators and constructors to model the application as per your use case.

## Creating an HTTP Application

### HTTP application that always succeeds

To create an HTTP application that always returns the same response and never fails, you can use the `succeed` constructor.

```scala
  val app: Http[Any, Nothing, Any, Int] = Http.succeed(1)
```

### HTTP application that always fails

To create an HTTP application that always fails with the given error, you can use the `fail` constructor.

```scala
  val app: Http[Any, Error, Any, Nothing] = Http.fail(new Error("Error_Message"))
  ```
HTTP applications can also be created from total and partial functions. These are some constructors to create HTTP applications from total as well as partial functions.

### HTTP application from a partial function

`Http.Collect` can create an `Http[Any, Nothing, A, B]` from a `PartialFunction[A, B]`. In case the input is not defined for the partial function, the application will return a `None` type error.

```scala
  val app: Http[Any, Nothing, String, String] = Http.collect[String] {
    case "case 1" => "response 1"
    case "case 2" => "response 2"
  }
```

`Http.CollectZIO` can be used to create a `Http[R, E, A, B]` from a partial function that returns a ZIO effect, i.e `PartialFunction[A, ZIO[R, E, B]`. This constructor is used when the output is effectful.

```scala
  val app: Http[Any, Nothing, String, String] = Http.collectZIO[String] {
    case "case 1" => ZIO.succeed("response 1")
  }
```

### HTTP application from a total function

`Http.fromFunction` can create an `Http[Any, Nothing, A, B]` from a function `f: A=>B`.

```scala
  val app: Http[Any, Nothing, Int, Int] = Http.fromFunction[Int](i => i + 1)
```

`Http.fromFunctionZIO` can create a `Http[R, E, A, B]` from a function that returns a ZIO effect, i.e `f: A => ZIO[R, E, B]`.

```scala
  val app: Http[Any, Nothing, Int, Int] = Http.fromFunction[Int](i => ZIO.succeed(i + 1))
```

## Transforming Http Applications

Http operators are used to transform one or more HTTP applications to create a new HTTP application. Http domain provides plenty of such powerful operators.

### Transforming the output 

To transform the output of the HTTP application, you can use `map` operator . It takes a function `f: B=>C` to convert a `Http[R,E,A,B]`to `Http[R,E,A,C]`.

```scala
  val a: Http[Any, Nothing, Any, String] = Http.succeed("text")
  val app: Http[Any, Nothing, Any, Int] = a.map(s => s.length())
```

To transform the output of the HTTP application effectfully, you can use `mapZIO` operator. It takes a function `B => ZIO[R1, E1, C]` to convert a `Http[R,E,A,B]` to `Http[R,E,A,C]`.

```scala
  val a: Http[Any, Nothing, Any, String] = Http.succeed("text")
  val app: Http[Any, Nothing, Any, Int] = a.mapZIO(s => ZIO.succeed(s.length()))
```

### Transforming the input

To transform the input of the HTTP application, you can use `contramap` operator. Before passing the input on to the HTTP application, `contramap` applies a function `xa: X => A` on it.

```scala
  val a: Http[Any, Nothing, String, String] = Http.fromFunction[String](s => s + ' ' + s)
  val app: Http[Any, Nothing, Int, String] = a.contramap[Int](_.toString)
```

To transform the input of the HTTP application effectfully, you can use `contramapZIO` operator. Before passing the input on to the HTTP application, `contramapZIO` applies a function `xa: X => ZIO[R1, E1, A]` on it.

```scala
  val a: Http[Any, Nothing, String, String] = Http.fromFunction[String](s => s + ' ' + s)
  val app: Http[Any, Any, Int, String] = a.contramapZIO[Any, Any,Int](a=>ZIO.succeed(a.toString))
```

### Chaining HTTP applications

To chain two HTTP applications, you can use `flatMap` operator.It creates a new `Http[R1, E1, A1, C1]` from the output of a `Http[R,E,A,B]`, using a function `f: B => Http[R1, E1, A1, C1]`. `>>=` is an alias for flatMap.

```scala
  val a: Http[Any, Nothing, Any, String] = Http.succeed("text1")
  val app: Http[Any, Nothing, Any, String] = a >>= (s => Http.succeed(s + " text2"))
```

### Folding an HTTP application

`foldHttp` lets you handle the success and failure values of an HTTP application. It takes in two functions, one for failure and one for success, and one more HTTP application. 
- If the application fails with `Some[E]` the first function will be executed with `E`, 
- If the application succeeds with `B`, the second function will be executed with `B` and 
- If the application fails with `None` the given HTTP application will be executed with the original input.

```scala
  val a: Http[Any, String, String, String] = Http.collectHttp[String]{
    case "case" => Http.fail("1")
    case _ => Http.succeed("2")
  }
  val b: Http[Any, Nothing, Any, String] = Http.succeed("3")
  val app: Http[Any, Nothing, String, String] = a.foldHttp(e => Http.succeed(e), s => Http.succeed(s), b)
```
## Error Handling
These are several ways in which error handling can be done in `Http` domain,

### Catch all errors

To catch all errors in case of failure of an HTTP application, you can use `catchAll` operator. It pipes the error to a function `f: E => Http[R1, E1, A1, B1]`.

```scala
  val a: Http[Any, Throwable, Any, Nothing] = Http.fail(new Throwable("Error_Message"))
  val app: Http[Any, Nothing, Any, Option[Throwable]] = a.catchAll(e => Http.succeed(Option(e)))
```

### Mapping the error

To transform the failure of an HTTP application, you can use `mapError` operator. It pipes the error to a function `ee: E => E1`.

```scala
  val a: Http[Any, Throwable, Any, Nothing] = Http.fail(new Throwable("Error_Message"))
  val app: Http[Any, Option[Throwable], Any, Nothing] = a.mapError(e => Option(e))
```

## Composition of HTTP applications

HTTP applications can be composed using several special operators.

### Using `++`

`++` is an alias for `defaultWith`. While using `++`, if the first HTTP application returns `None` the second HTTP application will be evaluated, ignoring the result from the first. If the first HTTP application is failing with a `Some[E]` the second HTTP application won't be evaluated.

```scala
  val a: Http[Any, Nothing, String, String] = Http.collect[String] {
    case "case 1" => "response 1"
    case "case 2" => "response 2"
  }
  val b: Http[Any, Nothing, String, String] = Http.collect[String] {
    case "case 3" => "response 3"
    case "case 4" => "response 4"
  }
  val app: Http[Any, Nothing, String, String] = a ++ b
```

### Using `<>`

`<>` is an alias for `orElse`. While using `<>`, if the first HTTP application fails with `Some[E]`, the second HTTP application will be evaluated, ignoring the result from the first. If the first HTTP application returns `None`, the second HTTP application won't be evaluated. 

```scala
  val app: Http[Any, Nothing, Any, Int] = Http.fail(1) <> Http.succeed(2)
```

### Using `>>>`

`>>>` is an alias for `andThen`. It runs the first HTTP application and pipes the output into the other.

```scala
  val a: Http[Any, Nothing, Int, Int] = Http.fromFunction[Int](a => a + 1)
  val b: Http[Any, Nothing, Int, Int] = Http.fromFunction[Int](b => b * 2)
  val app: Http[Any, Nothing, Int, Int] = a >>> (b)
```

### Using `<<<`

`<<<` is the alias for `compose`. Compose is similar to andThen. It runs the second HTTP application and pipes the output to the first HTTP
application.

```scala
  val a: Http[Any, Nothing, Int, Int] = Http.fromFunction[Int](a => a + 1)
  val b: Http[Any, Nothing, Int, Int] = Http.fromFunction[Int](b => b * 2)
  val app: Http[Any, Nothing, Int, Int] = a <<< (b)
```

## Providing environments

There are many operators to provide the HTTP application with its required environment.

### provideCustomLayer

Provides the HTTP application with the part of the environment that is not part of the ZEnv, leaving an effect that only depends on the ZEnv.

```scala
  val a: Http[Clock, DateTimeException, String, OffsetDateTime] = Http.collectZIO[String] {
    case "case 1"    => clock.currentDateTime
  }
  val app: Http[zio.ZEnv, DateTimeException, String, OffsetDateTime] = a.provideCustomLayer(Clock.live)
```

## Attaching Middleware

Middlewares are essentially transformations that one can apply to any `Http` to produce a new one. To attach middleware to the HTTP application, you can use `middleware` operator. `@@` is an alias for `middleware`.

```scala
  val app: Http[Any, Int, Any, Nothing] = Http.succeed(1) @@ Middleware.fail(2)
```

## Unit testing

Since an HTTP application `Http[R, E, A, B]` is a function from `A` to `ZIO[R, Option[E], B]`, we can write unit tests just like we do for normal functions.

The below snippet tests an app that takes `Int` as input and responds by adding 1 to the input.
```scala
    package zio.http.middleware
    
    import zio.http._
    import zio.test.Assertion.equalTo
    import zio.test._
    
    object Spec extends DefaultRunnableSpec {
    
      def spec = suite("http")(
        test("1 + 1 = 2") {
          val app: Http[Any, Nothing, Int, Int] = Http.fromFunction[Int](_ + 1)
          assertZIO(app(1))(equalTo(2))
        }
      )
    }
```

# What is HttpApp?

`HttpApp[-R, +E]` is a type alias for `Http[R, E, Request, Response]`, i.e `HttpApp[-R, +E]` is a function
from `Request` to `ZIO[R, Option[E], Response]`. ZIO HTTP server runs `HttpApp[R, E]` only.

## Special Constructors for HttpApp

These are some of the special constructors for HttpApp:

### Http.ok

Creates an `HttpApp` that always responds with a 200 status code.

```scala
  val app: HttpApp[Any, Nothing] = Http.ok
```

### Http.text

Creates an `HttpApp` that always responds with the same plain text.

```scala
  val app: HttpApp[Any, Nothing] = Http.text("Text Response")
```

### Http.status

Creates an `HttpApp` that always responds with the same status code and empty data.

```scala
  val app: HttpApp[Any, Nothing] = Http.status(Status.OK)
```

### Http.error

Creates an `HttpApp` that always fails with the given `HttpError`.

```scala
  val app: HttpApp[Any, Nothing] = Http.error(HttpError.Forbidden())
```

### Http.response

Creates an `HttpApp` that always responds with the same `Response`.

```scala
  val app: HttpApp[Any, Nothing] = Http.response(Response.ok)
```

## Special operators on HttpApp

These are some special operators for `HttpApps`.

### setMethod

Overwrites the method in the incoming request to the `HttpApp`

```scala
  val a: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
  }
  val app = a setMethod (Method.POST)
```

### patch

Patches the response produced by the HTTP application using a `Patch`.

```scala
  val a: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
  }
  val app = a.patch(Patch.setStatus(Status.ACCEPTED))
```

### getBodyAsString

`getBodyAsString` extract the body of the response as a string and make it the output type.

```scala
  val a: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
  }
  val app: Http[Any, Throwable, Request, String] = a.bodyAsString
```

## Converting an `Http` to `HttpApp`
If you want to run an `Http[R, E, A, B]` app on the ZIO HTTP server you need to convert it to `HttpApp[R, E]` using operators
like `map`, `contramap`, `codec` etc.

### Using map and contramap

Below snippet shows an app of type `Http` which takes a string and responds with a string.
```scala
  val http: Http[Any, Nothing, String, String] = Http.collect[String] {
    case "GET" => "Ok"
  }
```
Now, to convert it into an `HttpApp`
- use `contramap` to transform the input ie `String` to `Request`
- use `map` to transform the output ie `String` to `Response`

```scala
  val app: HttpApp[Any, Nothing] = http.contramap[Request](r => r.method.toString()).map[Response](s => Response.text(s))
```

### Using middleware

We can also convert an `Http` to `HttpApp` using codec middlewares that take in 2 functions `decoder: AOut => Either[E, AIn]` and
`encoder: BIn => Either[E, BOut]`. Please find more operators in middlewares.

```scala
  val a: Http[Any, Nothing, String, String] = Http.collect[String] { 
    case "GET" => "Ok"
  }
  val app: Http[Any, Nothing, Request, Response]  = a @@ Middleware.codec[Request,String](r => Right(r.method.toString()),s => Right(Response.text(s)))
```
## Running an HttpApp

ZIO HTTP server needs an `HttpApp[R,E]` for running.
We can use `Server.app()` method to bootstrap the server with an `HttpApp[R,E]`
```scala
  import zio.http._
  import zio.http.Server
  import zio._
    
  object HelloWorld extends App {
    val app: HttpApp[Any, Nothing] = Http.ok
    override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = Server.start(8090, app).exitCode
  } 
```