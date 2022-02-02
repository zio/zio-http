# Http Domain

`Http` is a functional domain that models HTTP applications using ZIO. It can work over any kind of input and output
types.

A `Http[-R, +E, -A, +B]` models a function from `A` to `ZIO[R, Option[E], B]`. When a value of type `A` is to be
evaluated against an `Http[R,E,A,B]`, internally a private method `execute` will be called and an `HExit` value is
returned that can be resolved further. If `A` is not defined in the application it will fail with `None`.

`Http` domain provides several operators and constructors to model the application as per your use case.

## Creating an HTTP Application

These are some constructors to make HTTP applications.

### Http.succeed

Creates an HTTP application that always returns the same response and never fails.

```scala
  val app = Http.succeed(1)
```

### Http.fail

Creates an HTTP application that always fails with the given error.

```scala
  val app = Http.fail(new Error("Error_Message"))
  ```

HTTP applications can also be created from total and partial functions. These are some constructors
to create HTTP applications from total as well as partial functions.

### Http.collect

Http.Collect can create an `Http[Any, Nothing, A, B]` from a `PartialFunction[A, B]`. In case an input is not defined
for the partial function, the application will return a `None` type error.

```scala
  val app: Http[Any, Nothing, String, String] = Http.collect[String] {
    case "case 1" => "response 1" 
    case "case 2" => "response 2"
  }
```

### Http.collectZIO

Http.CollectZIO can be used to create a `Http[R, E, A, B]` from a partial function that returns a ZIO effect,
i.e `PartialFunction[A, ZIO[R, E, B]`. This constructor is used when the output is effectful.

```scala
  val app: Http[Any, Nothing, String, String] = Http.collectZIO[String] {
    case "case 1" => ZIO.succeed("response 1")
  }
```

### Http.fromFunction

Http.fromFunction can create an `Http[Any, Nothing, A, B]` from a function `f: A=>B`.

```scala
  val app: Http[Any, Nothing, Int, Int] = Http.fromFunction[Int](i => i + 1)
```

### Http.fromFunctionZIO

Http.fromFunctionZIO can create a `Http[R, E, A, B]` from a function that returns a ZIO effect,
i.e `f: A => ZIO[R, E, B]`.

```scala
  val app: Http[Any, Nothing, Int, Int] = Http.fromFunction[Int](i => ZIO.succeed(i + 1))
```

Apart from these constructors, there are many more constructors that are special cases of the ones explained above. Kindly check out the examples for more
HTTP applications.

## Transforming Http Applications

Http operators are used to transform one or more HTTP applications to create a new HTTP application. Here are a few
handy operators.

### map

Transforms the output of the HTTP application. Map takes a function `f: B=>C` to convert a `Http[R,E,A,B]`
to `Http[R,E,A,C]`

```scala
  val a = Http.succeed("text")
  val app = a.map(s => s.length())
```

### contramap

Transforms the input of the HTTP application before passing it on using a function `xa: X => A`.

```scala
  val a = Http.fromFunction[String](s => s + ' ' + s)
  val app = a.contramap[Int](_.toString)
```

### flatMap

Create a new `Http[R1, E1, A1, C1]` from the output of a `Http[R,E,A,B]`, using a
function `f: B => Http[R1, E1, A1, C1]`. `>>=` is an alias for flatMap.

```scala
  val a = Http.succeed("text1")
  val app = a.map(s => Http.succeed(s + " text2"))
```

### middleware

Attaches the provided middleware to the HTTP application. `@@` is an alias for middleware.

```scala
  val app = Http.succeed(1) @@ Middleware.fail(2)
```

### foldHttp

Folds over the HTTP application by taking in two functions, one for failure and one for success respectively, and one
more HTTP application. If the application fails with `Some[E]` the first function will be executed with `E`, if the 
application succeed with B, the second function will be executed with `B`and if the application fails with `None` the 
given HTTP application will be executed with the original input. 

```scala
  val a = Http.succeed("text1")
  val b = Http.succeed("text2")
  val app = a.foldHttp(e => Http.fail(e), s => Http.succeed(s), b)
```

There are a number of ways in which error handling can be done in `Http` domain. These are few constructors to do so.

### catchAll

Collects all errors in case of a failure and pipes it to a function `f: E => Http[R1, E1, A1, B1]`.

```scala
  val a = Http.fail(new Throwable("Error_Message"))
  val app = a.catchAll(e => Http.succeed(Option(e)))
```

### mapError

Transforms the failure of an HTTP application using a function `ee: E => E1`.

```scala
  val a = Http.fail(new Throwable("Error_Message"))
  val app = a.mapError(e => Option(e))
```

## Http Combinators

`Http` combinators are special operators that combine several HTTP applications into one. These are a few handy
combinators.

### defaultWith

Combines two HTTP applications into one. `++` is an alias for defaultWith. If the first HTTP application returns `None`
the second HTTP application will be evaluated. If the first HTTP application is failing with a `Some[E]` the second HTTP
application won't be evaluated.

```scala
  val a: Http[Any, Nothing, String, String] = Http.collect[String] {
    case "case 1" => "response 1"
    case "case 2" => "response 2"
  }
  val b: Http[Any, Nothing, String, String] = Http.collect[String] {
    case "case 1" => "response 1"
    case "case 2" => "response 2"
  }
  val app = a ++ b
```

### orElse

Runs the first HTTP application but if it fails with `Some[E]`, runs the second HTTP application, ignoring the result
from the first. If the first HTTP application returns `None`, the second HTTP application won't be evaluated. `<>` is an
alias for orElse.

```scala
  val app = Http.fail(1) <> Http.succeed(2)
```

### andThen

Runs the first HTTP application and pipes the output into the other. `>>>` is an alias for andThen.

```scala
  val a = Http.fromFunction[Int](a => a + 1)
  val b = Http.fromFunction[Int](b => b * 2)
  val app = a >>> (b)
```

### compose

Compose is similar to andThen. It runs the second HTTP application and pipes the output to the first HTTP
application. `<<<` is the alias for compose.

```scala
  val a = Http.fromFunction[Int](a => a + 1)
  val b = Http.fromFunction[Int](b => b * 2)
  val app = a <<< (b)
```

Apart from these, there are many more operators that let you transform an Http in specific ways.

# HttpApp

`HttpApp[-R, +E]` is a type alias for `Http[R, E, Request, Response]`, i.e `HttpApp[-R, +E]` is a function
from `Request` to `ZIO[R, Option[E], Response]`. ZIO HTTP server runs only `HttpApp[R, E]`.

## Constructors for HttpApp

These are some of the special constructors for HttpApp.

### Http.ok

Creates an HTTP application that always responds with a 200 status code.

```scala
  val app: HttpApp[Any, Nothing] = Http.ok
```

### Http.text

Creates an HTTP application that always responds with the same plain text.

```scala
  val app = Http.text("Text Response")
```

### Http.status

Creates an HTTP application that always responds with the same status code and empty data.

```scala
  val app: HttpApp[Any, Nothing] = Http.status(Status.OK)
```

### Http.error

Creates an HTTP application that always fails with the given `HttpError`.

```scala
  val app: HttpApp[Any, Nothing] = Http.error(HttpError.Forbidden())
```

### Http.response

Creates an HTTP application that always responds with the same `Response`.

```scala
  val app: HttpApp[Any, Nothing] = Http.response(Response.ok)
```

## Operators for HttpApp

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
  val app: Http[Any, Throwable, Request, String] = a.getBodyAsString
```

## Converting an `Http` to `HttpApp`

If you want to run an `Http[R, E, A,B]` on ZIO HTTP server you need to convert it to `HttpApp[R, E]` using operators
like `map`,`contramap`, etc.

```scala
  val a: Http[Any, Nothing, String, String] = Http.fromFunction[String] {
    case "GET" => "Ok"
  }
  val app: HttpApp[Any, Nothing] = a.contramap[Request](r => r.method.toString()).map[Response](s => Response.text(s))
```