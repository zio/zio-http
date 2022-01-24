#HTTP
HTTP is a functional domain to model Http apps using ZIO. It can work over any kind of request and response type.

A `Http[-R, +E, -A, +B]` is essentially a function form `A` to `ZIO[R, Option[E], B]`. Whereas `HttpApp[-R, +E]` is a function from `Request` to `ZIO[R, E, Response]`. To start a zio-http server we need a `HttpApp[R, E]`.

There are several operators and constructors to model the application as required.
##HTTP Constructors
Here are some of the simple constructors to make useful Http applications.
###Http.ok
Creates an HTTP app that always responds with a 200 status code.
```scala
  val app = Http.ok
```
###Http.fail()
Creates an Http app that always fails with the given value.
```scala
  val app = Http.fail(new Error("Error_Message"))
```
###Http.succeed ()
Creates an Http that always returns the same response and never fails.
```scala
  val app = Http.succeed(1)
```
###Http.text ()
Creates an Http app that always responds with the same plain text.
```scala
  val app = Http.text("Text Response")
```

Apart from these, you can also create an Http application from total and partial functions. Here are some constructors to create Http applications from total as well as partial functions.
###Http.collect ()
Http.Collect() can create an `Http[Any, Nothing, A, B]` from a `PartialFunction[A, B]`. In case an input is not defined for the partial function, the application will return a 404 error.
```scala
  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
    case Method.GET -> !! / "json" => Response.ok
  }
```
###Http.collectZIO ()
Http.CollectZIO() can be used to create a `Http[R, E, A, B]` from a partial function that returns a ZIO effect, i.e `PartialFunction[A, ZIO[R, E, B]`.
```scala
  val app: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => ZIO[Response.ok]
  }
```
###Http.fromFunction ()
Http.fromFunction() can create an `Http[Any, Nothing, A, B]` from a function `f: A=>B`.
```scala
  val app: HttpApp[Any, Nothing] = Http.fromFunction[Request](req=>Response.text(req.url.path.toString))
```
###Http.fromFunctionZIO ()
Http.fromFunctionZIO() can create a `Http[R, E, A, B]` from a function that returns a ZIO effect, i.e `f: A => ZIO[R, E, B]`.
```scala
  val app: HttpApp[Any, Nothing] = Http.fromFunction[Request](req=>ZIO(Response.text(req.url.path.toString)))
```
Apart from these constructors, many constructors are special cases of the constructors explained above. Some of them directly create an `HttpApp[R,E]` were as others create normal `Http[R,E,A,B]`. Kindly check out the examples for more Http applications.
##Http Operators
Http operators are used to transforming one or more Http applications to create a new Http application. Here are a few handy operators.
###map ()
Transforms the output of the http app. Map takes a function `f: B=>C` to convert a `Http[R,E,A,B]` to `Http[R,E,A,C]`
```scala
  val app1 = Http.succeed("text")
  val app2 = app1.map(s => s.length())
```
###flatmap ()
Create a new `Http[R1, E1, A1, C1]` from the output of a `Http[R,E,A,B]`, using a function `f: B => Http[R1, E1, A1, C1]`.
```scala
  val app1 = Http.succeed("text1")
  val app2 = app1.map(s => Http.succeed(s + " text2"))
```
###foldHttp ()
Folds over the http app by taking in two functions one for failure and one for success respectively.
```scala
  val app1 = Http.succeed("text1")
  val app2 = Http.succeed("text2")
  val app3 = app1.foldHttp(e => Http.fail(e), s => Http.succeed(s), app2)
```
###defaultWith ()
Combines two Http into one.
```scala
  val app1: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "text" => Response.text("Hello World!")
  }
  val app2: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "json" => Response.ok
  }
  val app = app1 ++ app2
```
###andThen ()
Pipes the output of one app into the other.
```scala
  val app1 = Http.fromFunction[Int](a=> a + 1)
  val app2 = Http.fromFunction[Int](b=> b*2)
  val app = app1.andThen(app2)
```
###execute ()
Execute is a special method of Http that valuates the app and returns an HExit that can be resolved further.
```scala
  val app = Http.fromFunction[Int](a=> a + 1)
  val hExitValue = app.execute(1)
```
Apart from these, there are many more operators that let you transform an Http in specific ways.
##HttpAppSyntax
If the app is of type `Http[R, E, Request, Response]` i.e `HttpApp[R,E]`, there are some special operators available for them. Here are few handy HttpApp operators.
###middleware ()
Attaches the provided middleware to the HttpApp
```scala
  val app = Http.ok @@ Middleware.status(Status.ACCEPTED)
```
###setMethod ()
Overwrites the method in the incoming request to the HttpApp
```scala
  val app1: HttpApp[Any, Nothing] = Http.collect[Request] {
  case Method.GET -> !! / "text" => Response.text("Hello World!")
}
val app = app1 setMethod(Method.POST)
```
###patch ()
Patches the response produced by the app using a `Patch`.
```scala
  val app1: HttpApp[Any, Nothing] = Http.collect[Request] {
  case Method.GET -> !! / "text" => Response.text("Hello World!")
}
val app = app1.patch(Patch.setStatus(Status.ACCEPTED))
```
