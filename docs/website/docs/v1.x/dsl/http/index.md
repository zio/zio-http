# Http Domain

`Http` is a functional domain that models HTTP applications using ZIO. It can work over any kind of input and output
types.

A `Http[-R, +E, -A, +B]` models a function from `A` to `ZIO[R, Option[E], B]`. Whereas `HttpApp[-R, +E]` is a function
from `Request` to `ZIO[R, Option[E], Response]`. ZIO HTTP server works on `HttpApp[R, E]`. When a value of type `A` is
to be evaluated against an `Http[R,E,A,B]`, internally a private method `execute` will be called and an `HExit` value is
returned that can be resolved further.

`Http` domain provides several operators and constructors to model the application as per your use case.

## HTTP Constructors

These are some constructors to make HTTP applications.

### Http.ok

Creates an HTTP application that always responds with a 200 status code.

```scala
  val app = Http.ok
```

### Http.fail

Creates an HTTP application that always fails with the given error.

```scala
  val app = Http.fail(new Error("Error_Message"))
```

### Http.succeed

Creates an HTTP application that always returns the same response and never fails.

```scala
  val app = Http.succeed(1)
```

### Http.text

Creates an HTTP application that always responds with the same plain text.

```scala
  val app = Http.text("Text Response")
```

Apart from these, you can also create an HTTP application from total and partial functions. These are some constructors
to create HTTP applications from total as well as partial functions.

### Http.collect

Http.Collect can create an `Http[Any, Nothing, A, B]` from a `PartialFunction[A, B]`. In case an input is not defined
for the partial function, the application will return a `None` type error.

```scala
  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
    case Method.GET -> !! / "json" => Response.ok
  }
```

### Http.collectZIO

Http.CollectZIO can be used to create a `Http[R, E, A, B]` from a partial function that returns a ZIO effect,
i.e `PartialFunction[A, ZIO[R, E, B]`. This constructor is used when the output is effectful.

```scala
  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => ZIO[Response.ok]
  }
```

### Http.fromFunction

Http.fromFunction can create an `Http[Any, Nothing, A, B]` from a function `f: A=>B`.

```scala
  val app: HttpApp[Any, Nothing] = Http.fromFunction[Request](req => Response.text(req.url.path.toString))
```

### Http.fromFunctionZIO

Http.fromFunctionZIO can create a `Http[R, E, A, B]` from a function that returns a ZIO effect,
i.e `f: A => ZIO[R, E, B]`.

```scala
  val app: HttpApp[Any, Nothing] = Http.fromFunction[Request](req => ZIO(Response.text(req.url.path.toString)))
```

Apart from these constructors, many constructors are special cases of the constructors explained above. Some of them
directly create an `HttpApp[R,E]` were as others create normal `Http[R,E,A,B]`. Kindly check out the examples for more
HTTP applications.

## Http Operators

`Http` operators are used to transforming one or more HTTP applications to create a new HTTP application. Here are a few
handy operators.

### map

Transforms the output of the HTTP application. Map takes a function `f: B=>C` to convert a `Http[R,E,A,B]`
to `Http[R,E,A,C]`

```scala
  val app1 = Http.succeed("text")
  val app2 = app1.map(s => s.length())
```

### flatMap

Create a new `Http[R1, E1, A1, C1]` from the output of a `Http[R,E,A,B]`, using a
function `f: B => Http[R1, E1, A1, C1]`. `>>=` is an alias for flatMap.

```scala
  val app1 = Http.succeed("text1")
  val app2 = app1.map(s => Http.succeed(s + " text2"))
```

### middleware

Attaches the provided middleware to the HTTP application. `@@` is as alias for middleware.

```scala
  val app = Http.ok @@ Middleware.status(Status.ACCEPTED)
```

### foldHttp

Folds over the HTTP application by taking in two functions, one for failure and one for success respectively, and one
more HTTP application.

```scala
  val app1 = Http.succeed("text1")
  val app2 = Http.succeed("text2")
  val app3 = app1.foldHttp(e => Http.fail(e), s => Http.succeed(s), app2)
```

## Http Combinators

`Http` combinators are special operators that combine several HTTP applications into one. These are few handy combinators.

### defaultWith

Combines two HTTP applications into one. `++` is an alias for defaultWith. If the first HTTP application returns `None`
the second HTTP application will be evaluated.

```scala
  val app1: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
  }
  val app2: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "json" => Response.ok
  }
  val app = app1 ++ app2
```

### orElse

Runs the first HTTP application but if it fails, runs other, ignoring the result from self. `<>` is an alias for orElse.

```scala
  val app = Http.fail(1) <> Http.succeed(2)
```

### andThen

Runs the first HTTP application and pipes the output into the other. `>>>` is an alias for andThen.

```scala
  val app1 = Http.fromFunction[Int](a => a + 1)
  val app2 = Http.fromFunction[Int](b => b * 2)
  val app = app1 >>> (app2)
```

### compose

Compose is similar to andThen. It runs the second HTTP application and pipes the output to the first HTTP
application. `<<<` is the alias for compose.

```scala
  val app1 = Http.fromFunction[Int](a => a + 1)
  val app2 = Http.fromFunction[Int](b => b * 2)
  val app = app1 <<< (app2)
```

Apart from these, there are many more operators that let you transform an Http in specific ways.

## HttpAppSyntax

If the application is of type `Http[R, E, Request, Response]` i.e `HttpApp[R,E]`, there are some special operators
available for them. Here are few handy `HttpApp` operators.

### setMethod

Overwrites the method in the incoming request to the `HttpApp`

```scala
  val app1: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
  }
  val app = app1 setMethod (Method.POST)
```

### patch

Patches the response produced by the HTTP application using a `Patch`.

```scala
  val app1: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
  }
  val app = app1.patch(Patch.setStatus(Status.ACCEPTED))
```

### getBodyAsString

`getBodyAsString` extract the body of response as a string and make it the output type.

```scala
  val app1: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
  }
  val app: Http[Any, Throwable, Request, String] = app1.getBodyAsString
```
