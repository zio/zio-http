---
id: http
title: Http
---

`Http` is a functional domain that models HTTP applications. It’s polymorphic on input and output type.

A `Http[-R, +E, -A, +B]` models a function from `A` to `ZIO[R, Option[E], Handler[R, E, A, B]]`.
When a value of type `A` is evaluated against an `Http[R,E,A,B]`, it can either succeed with a `Handler`, fail with
a `Some[E]` or if `A` is not defined in the application, fail with `None`.

`Handler[-R, +E, -A, +B]` models a function that takes an `A` and returns a `B`, possibly failing with an `E` and using
a ZIO effect. A handler can always succeed with a value (or fail) no matter what its input is.

`Http` on the other hand can decide to not handle a particular input, so it adds input based _routing_ on top of
the `Handler` type.

Both `Handler` and `Http` provides several operators and constructors to model the application as per your use case.

## Creating an HTTP Application

### HTTP application that always succeeds

To create an HTTP application that always returns the same response and never fails, you can use the `succeed`
constructor.

```scala mdoc:silent
import zio._
import zio.http._

val app1: Http[Any, Nothing, Any, Int] = Handler.succeed(1).toHttp
```

### HTTP application that always fails

To create an HTTP application that always fails with the given error, you can use the `fail` constructor.

```scala mdoc:silent
val app2: Http[Any, Error, Any, Nothing] = Handler.fail(new Error("Error_Message")).toHttp
```

HTTP applications can also be created from total and partial functions. These are some constructors to create HTTP
applications from total as well as partial functions.

### HTTP application from a partial function

`Http.collect` can create an `Http[Any, Nothing, A, B]` from a `PartialFunction[A, B]`. In case the input is not defined
for the partial function, the application will return a `None` type error.

```scala mdoc:silent
val app3: Http[Any, Nothing, String, String] =
  Http.collect[String] {
    case "case 1" => "response 1"
    case "case 2" => "response 2"
  }
```

`Http.collectZIO` can be used to create a `Http[R, E, A, B]` from a partial function that returns a ZIO effect,
i.e `PartialFunction[A, ZIO[R, E, B]`. This constructor is used when the output is effectful.

```scala mdoc:silent
val app4: Http[Any, Nothing, String, String] =
  Http.collectZIO[String] {
    case "case 1" => ZIO.succeed("response 1")
  }
```

### HTTP application from a total function

`Handler.fromFunction` can create an `Http[Any, Nothing, A, B]` from a function `f: A=>B`. This can be converted to
a `Http` which always routes to that given `Handler` by using `toHttp`:

```scala mdoc:silent
val app5: Http[Any, Nothing, Int, Int] =
  Handler.fromFunction[Int](i => i + 1).toHttp
```

`Handler.fromFunctionZIO` can create a `Http[R, E, A, B]` from a function that returns a ZIO effect,
i.e `f: A => ZIO[R, E, B]`.

```scala mdoc:silent
val app6: Http[Any, Nothing, Int, Int] =
  Handler.fromFunctionZIO[Int](i => ZIO.succeed(i + 1)).toHttp
```

## Transforming Http Applications

Http operators are used to transform one or more HTTP applications to create a new HTTP application. Http domain
provides plenty of such powerful operators.

### Transforming the output

To transform the output of the HTTP application, you can use `map` operator . It takes a function `f: B=>C` to convert
a `Http[R,E,A,B]`to `Http[R,E,A,C]`.

```scala mdoc:silent
val app7: Http[Any, Nothing, Any, String] = Handler.succeed("text").toHttp
val app8: Http[Any, Nothing, Any, Int] = app7.map(s => s.length())
```

To transform the output of the HTTP application effectfully, you can use `mapZIO` operator. It takes a
function `B => ZIO[R1, E1, C]` to convert a `Http[R,E,A,B]` to `Http[R,E,A,C]`.

```scala mdoc:silent
val app9: Http[Any, Nothing, Any, Int] = app7.mapZIO(s => ZIO.succeed(s.length()))
```

### Transforming the input

To transform the input of a `Handler`, you can use `contramap` operator.
Before passing the input on to the HTTP application, `contramap` applies a function `xa: X => A` on it.

```scala mdoc:silent
val handler1: Handler[Any, Nothing, String, String] = Handler.fromFunction[String](s => s + ' ' + s)
val app10: Http[Any, Nothing, Int, String] = handler1.contramap[Int](_.toString).toHttp
```

To transform the input of the handler effectfully, you can use `contramapZIO` operator. Before passing the
input on to the HTTP application, `contramapZIO` applies a function `xa: X => ZIO[R1, E1, A]` on it.

```scala mdoc:silent
val app11: Http[Any, Any, Int, String] = handler1.contramapZIO[Any, Any, Int](a => ZIO.succeed(a.toString)).toHttp
```

### Chaining handlers

To chain two handlers applications, you can use `flatMap` operator.It creates a new `Handler[R1, E1, A1, C1]` from the
output
of a `Handler[R,E,A,B]`, using a function `f: B => Handler[R1, E1, A1, C1]`. `>>=` is an alias for flatMap.

```scala mdoc:silent
val handler2: Handler[Any, Nothing, Any, String] = Handler.succeed("text1")
val handler3: Handler[Any, Nothing, Any, String] = handler2 >>= (s => Handler.succeed(s + " text2"))
```

### Folding a handler

`foldHandler` lets you handle the success and failure values of a handler. It takes in two functions, one for
failure and one for success, and one more handler.

- If the handler fails with `E` the first function will be executed with `E`,
- If the application succeeds with `B`, the second function will be executed with `B` and

```scala mdoc:silent
val handler4: Handler[Any, String, String, String] = Handler.fromFunctionHandler[String] {
  case "case" => Handler.fail("1")
  case _ => Handler.succeed("2")
}
val handler5: Handler[Any, Nothing, String, String] = handler4.foldHandler(e => Handler.succeed(e), s => Handler.succeed(s))
```

## Error Handling

These are several ways in which error handling can be done in both the `Http` and the `Handler` domains.

### Catch all errors

To catch all errors in case of failure of an HTTP application, you can use `catchAllZIO` operator. It pipes the error to
a
function `f: E => Http[R1, E1, A1, B1]`.

```scala mdoc:silent
val app12: Http[Any, Throwable, Any, Nothing] = Handler.fail(new Throwable("Error_Message")).toHttp
val app13: Http[Any, Nothing, Any, Option[Throwable]] = app12.catchAllZIO(e => ZIO.succeed(Option(e)))
```

### Mapping the error

To transform the failure of an HTTP application, you can use `mapError` operator. It pipes the error to a
function `ee: E => E1`.

```scala mdoc:silent
val app14: Http[Any, Throwable, Any, Nothing] = Handler.fail(new Throwable("Error_Message")).toHttp
val app15: Http[Any, Option[Throwable], Any, Nothing] = app14.mapError(e => Option(e))
```

## Composition of HTTP applications

HTTP applications can be composed using several special operators.

### Using `++`

`++` is an alias for `defaultWith`. While using `++`, if the first HTTP application returns `None` the second HTTP
application will be evaluated, ignoring the result from the first. If the first HTTP application is failing with
a `Some[E]` the second HTTP application won't be evaluated.

```scala mdoc:silent
val app16: Http[Any, Nothing, String, String] = Http.collect[String] {
  case "case 1" => "response 1"
  case "case 2" => "response 2"
}
val app17: Http[Any, Nothing, String, String] = Http.collect[String] {
  case "case 3" => "response 3"
  case "case 4" => "response 4"
}
val app18: Http[Any, Nothing, String, String] = app16 ++ app17
```

### Using `<>`

`<>` is an alias for `orElse`. While using `<>`, if the first handler fails with `Some[E]`, the second handler will be
evaluated, ignoring the result from the first. This operator is not available on the `Http` level, to keep the rules of
applying middlewares simple.

```scala mdoc:silent
val handler6: Handler[Any, Nothing, Any, Int] = Handler.fail(1) <> Handler.succeed(2)
```

### Using `>>>`

`>>>` is an alias for `andThen`. It runs the first HTTP application and pipes the output into the other handler.
The right side must be a `Handler`, it cannot perform further routing.

```scala mdoc:silent
val app19: Http[Any, Nothing, Int, Int] = Handler.fromFunction[Int](a => a + 1).toHttp
val handler7: Handler[Any, Nothing, Int, Unit] = Handler.fromFunctionZIO[Int](b => ZIO.debug(b * 2))
val app20: Http[Any, Nothing, Int, Unit] = app19 >>> handler7
```

### Using `<<<`

`<<<` is the alias for `compose`. Compose is similar to andThen, but it is only available on the `Handler` level.
It runs the second handler and pipes the output to the first handler.

```scala mdoc:silent
val handler8: Handler[Any, Nothing, Int, Int] = Handler.fromFunction[Int](a => a + 1)
val handler9: Handler[Any, Nothing, Int, Int] = Handler.fromFunction[Int](b => b * 2)
val handler10: Handler[Any, Nothing, Int, Int] = handler8 <<< handler9
```

## Providing environments

There are many operators to provide the HTTP application with its required environment, they work the same as the ones
on `ZIO`.

## Attaching Middleware

Middlewares are essentially transformations that one can apply to any `Http` or a `Handler` to produce a new one. To
attach middleware
to the HTTP application, you can use `middleware` operator. `@@` is an alias for `middleware`.

`RequestHandlerMiddleware` applies to `Handler`s converting a HTTP `Request` to `Response`. You can apply
a `RequestHandlerMiddleware` to both `Handler` and `Http`.
When applying it to a `Http`, it is equivalent to applying it to all handlers the `Http` can route to.  
`HttpAppMiddleware` applies only to `Http`s and they are capable of change the routing behavior.

## Unit testing

Since an HTTP application `Http[R, E, A, B]` is a function from `A` to `ZIO[R, Option[E], B]`, we can write unit tests
just like we do for normal functions.

The below snippet tests an app that takes `Int` as input and responds by adding 1 to the input.

```scala mdoc:silent
import zio.test.Assertion.equalTo
import zio.test.{test, _}

object Spec extends ZIOSpecDefault {

  def spec = suite("http")(
    test("1 + 1 = 2") {
      val app: Http[Any, Nothing, Int, Int] = Handler.fromFunction[Int](_ + 1).toHttp
      assertZIO(app.runZIO(1))(equalTo(2))
    }
  )
}
```

# What is App?

`App[-R]` is a type alias for `Http[R, Response, Request, Response]`.
ZIO HTTP server runs `App[E]` only. It is an application that takes a `Request` as an input, and it either produces
a successful `Response` or in case of failure it produces also a `Response`, representing the failure message to be sent
back.

## Special Constructors for Http and Handler

These are some special constructors for `Http` and `Handler`:

### Handler.ok

Creates a `Handler` that always responds with a 200 status code.

```scala mdoc:silent
Handler.ok
```

### Handler.text

Creates an `Handler` that always responds with the same plain text.

```scala mdoc:silent
Handler.text("Text Response")
```

### Handler.status

Creates an `Handler` that always responds with the same status code and empty data.

```scala mdoc:silent
Handler.status(Status.Ok)
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

### withMethod

Overwrites the method in the incoming request to the `HttpApp`

```scala
val a: HttpApp[Any, Nothing] = Http.collect[Request] {
  case Method.GET -> !! / "text" => Response.text("Hello World!")
}
val app = a withMethod (Method.POST)
```

### patch

Patches the response produced by the HTTP application using a `Patch`.

```scala
val a: HttpApp[Any, Nothing] = Http.collect[Request] {
  case Method.GET -> !! / "text" => Response.text("Hello World!")
}
val app = a.patch(Patch.withStatus(Status.ACCEPTED))
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

If you want to run an `Http[R, E, A, B]` app on the ZIO HTTP server you need to convert it to `HttpApp[R, E]` using
operators like `map`, `contramap`, `codec` etc.

### Using map and contramap

Below snippet shows an app of type `Http` which takes a string and responds with a string:

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

We can also convert an `Http` to `HttpApp` using codec middlewares that take in 2
functions `decoder: AOut => Either[E, AIn]` and `encoder: BIn => Either[E, BOut]`:

```scala
val a: Http[Any, Nothing, String, String] = Http.collect[String] {
  case "GET" => "Ok"
}
val app: Http[Any, Nothing, Request, Response] = a @@ Middleware.codec[Request, String](r => Right(r.method.toString()), s => Right(Response.text(s)))
```

Please find more operators in middlewares.

## Running an HttpApp

ZIO HTTP server needs an `HttpApp[R,E]` for running. We can use `Server.app()` method to bootstrap the server with
an `HttpApp[R,E]`:

```scala
import zio.http._
import zio.http.Server
import zio._

object HelloWorld extends App {
  val app: HttpApp[Any, Nothing] = Http.ok

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = Server.start(8090, app).exitCode
} 
```
