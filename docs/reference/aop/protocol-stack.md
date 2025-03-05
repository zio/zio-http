---
id: protocol-stack
title: ProtocolStack
---

:::note
The `ProtocolStack` is a low-level data type typically utilized in other higher abstractions such as `HandlerAspect` and `Middleware` for building middlewares. If you intend to write middleware, it is advisable in most cases to utilize these higher abstractions, as they simplify the process of middleware creation.

The `ProtocolStack` is a more advanced concept that provides fine-grained control over the types of inputs and outputs at each layer of the middleware stack, instead of common `Request` and `Response` types. Learning about `ProtocolStack` is recommended as it can be beneficial for understanding the inner workings of how middleware is constructed.
:::

`ProtocolStack` is a data type that represents a stack of one or more protocol layers. Each layer in the stack is a function that transforms the incoming and outgoing values of some handler.

We can think of a `ProtocolStack` as a function (or a composition of functions) that takes a handler and returns a new handler. The new handler is the result of applying each layer in the stack to the handler:

```scala
trait ProtocolStack[-R, -II, +IO, -OI, +OO] {
  def apply[Env <: R, Err >: OO, IncomingOut >: IO, OutgoingIn <: OI](
    handler: Handler[Env, Err, IncomingOut, OutgoingIn],
  ): Handler[Env, Err, II, OO]
}
```

The `ProtocolStack` data type has 5 type parameters, one for the ZIO environment, and four for the incoming and outgoing input and output types of the protocol stack:

- **Incoming Input**: This refers to data coming into the middleware from the client's HTTP request or the previous middleware in the chain. It could include information such as headers, cookies, query parameters, and the request body.

- **Incoming Output**: This refers to the data leaving the middleware and heading towards the server or the next middleware in the chain. This could include modified request data or additional metadata added by the middleware.

- **Outgoing Input**: This refers to data coming into the middleware from the handler or the previous middleware in the chain. It typically includes the HTTP response from the server, including headers, status codes, and the response body.

- **Outgoing Output**: This refers to data leaving the middleware and heading back to the client. It could include modified response data, additional headers, or any other transformations applied by the middleware.

A `ProtocolStack` can be created by combining multiple middleware functions using the `++` operator. Using the `++` operator, we can stack multiple middleware functions on top of each other to create a composite middleware that applies each middleware in the order they are stacked.

The diagram below illustrates how `ProtocolStack` works:

<div style={{textAlign: 'center', margin: '10px'}}>

![ProtocolStack Diagram](protocol-stack.svg)

</div>

Here is the flow of data through the `ProtocolStack`:

1. The incoming input `II` is transformed by the first layer of the protocol stack to produce the incoming output `IO`.
2. The incoming output `IO` is passed to the next layer of the protocol stack (if exists) to produce a new incoming output. This process continues until all layers have been applied.
3. The incoming output `IO` is passed to the handler, which is the last layer where the actual processing of the request takes place. The handler processes the incoming output and produces the outgoing input `OI`.
4. The outgoing input `OI` is passed to the last layer of the protocol stack to produce the outgoing output `OO`.
5. The outgoing output `OO` is passed to the previous layer of the protocol stack (if exists) to produce a new outgoing output. This process continues until all layers have been applied.
6. The outgoing output `OO` is returned as the final result of the protocol stack.

<div style={{textAlign: 'center', margin: '10px'}}>

![Multiple ProtocolStack Diagram](multiple-protocol-stack.svg)

</div>

## Creating a ProtocolStack

There are several ways to create a `ProtocolStack`. One simple way is to start with an `identity` stack, which is a protocol stack that does nothing and simply passes the inputs to the outputs unchanged. Then, we can modify it by mapping over the inputs and outputs to apply transformations:

```scala mdoc:silent
import zio._
import zio.http._

type Request  = String
type Response = String
val identity: ProtocolStack[Any, Request, Request, Response, Response] =
  ProtocolStack.identity[Request, Response]
```

Assume we have a handler that takes a request and reverses it to create a response:

```scala mdoc:silent
val uppercase: Handler[Any, Nothing, Request, Response] =
  Handler.fromFunction[Request](_.toUpperCase)
```

If we apply the `uppercase` handler to the `identity` stack, it will simply return the same handler without any modifications:

```scala mdoc:silent
val handler: Handler[Any, Response, Request, Response] = identity(uppercase)
```

The behavior of the `handler` remains the same. Let's test it:

```scala mdoc
Unsafe.unsafe{ implicit unsafe =>
  Runtime.default.unsafe.run(
    ZIO.scoped(handler("Hello World!"))
  )
}
```

The output should be `HELLO WORLD!`, which is the result of applying the `uppercase` handler to the `identity` stack.

The `ProtocolStack` has two main methods for transforming the incoming and outgoing values: `mapIncoming` and `mapOutgoing`. Using these methods, we can apply transformations to the incoming and outgoing values of the protocol stack.

Let's create a new protocol stack that trims the incoming request, calculates the length of the outgoing response, and returns a tuple of the response and its length:

```scala mdoc:silent
val trimAndLength: ProtocolStack[Any, Request, Response, Response, (Response, Int)] =
  identity.mapIncoming(_.trim).mapOutgoing(r => (r, r.length))
```

Now, let's apply the `uppercase` handler to the `trimAndLength` stack:

```scala mdoc:silent
val newHandler: Handler[Any, (Response, Int), Request, (Response, Int)] =
  trimAndLength(uppercase)

Unsafe.unsafe { implicit unsafe =>
  Runtime.default.unsafe.run(
    ZIO.scoped(newHandler("Hello World! ")),
  )
}
```

The output should be `(HELLO WORLD!, 12)`, which is the result of applying the `uppercase` handler to the `trimAndLength` stack.

Please note that the `ProtocolStack` also has `interceptIncomingHandler` and `interceptOutgoingHandler` constructors that allow us to create a `ProtocolStack` by intercepting the incoming and outgoing handlers and applying transformations to them:

```scala mdoc:silent
val trim: ProtocolStack[Any, Request, Request, Response, Response] =
  ProtocolStack.interceptIncomingHandler(Handler.fromFunction[String](_.trim))

val length: ProtocolStack[Any, Request, Request, Response, (Response, Int)] = 
  ProtocolStack.interceptOutgoingHandler(Handler.fromFunction[String](r => (r, r.length)))
```

Then we can combine them using the `++` operator:

```scala mdoc
val anotherTrimAndLength: ProtocolStack[Any, Request, Request, Response, (Response, Int)] =
  length ++ trim
```

Now, let's apply the `uppercase` handler to the `anotherTrimAndLength` stack:

```scala mdoc
Unsafe.unsafe { implicit unsafe =>
  Runtime.default.unsafe.run(
    ZIO.scoped(anotherTrimAndLength(uppercase).apply("Hello World!")),
  )
}
```

We should get the same output as before: `(HELLO WORLD!, 12)`.

When we want to apply a transformation to both the incoming and outgoing values, there is a very simple way to do it using the `interceptHandler` constructor. It takes two handlers, one for transforming the incoming input and one for transforming the outgoing input:

```scala mdoc:silent
val an: ProtocolStack[Any, Response, Response, Response, (Response, RuntimeFlags)] =
  ProtocolStack.interceptHandler(Handler.fromFunction[String](_.trim))(
    Handler.fromFunction[String](r => (r, r.length)),
  )
```

## Stateful ProtocolStacks

In some cases, we may need to maintain some state along with the protocol stack. We can achieve such stateful behavior by using the `interceptHandlerStateful` constructor:

```scala
object ProtocolStack {
  def interceptHandlerStateful[Env, State, II, IO, OI, OO](
    incomingInputHandler: Handler[Env, OO, II, (State, IO)],
  )(
    outgoingOutputHandler: Handler[Env, Nothing, (State, OI), OO],
  ): ProtocolStack[Env, II, IO, OI, OO] = ???
}
```

The `interceptHandlerStateful` constructor takes two handlers:

- **Incoming Input Handler**— Takes the incoming input of type `II` and returns the state along with the incoming output of type `(State, IO)`.
- **Outgoing Input Handler**— Takes the state and the outgoing input of type `(State, OI)`, and returns the outgoing output of type `OO`.

For example, assume we want to design a middleware to measure the total response time of the server. To achieve this, we should store the start time when the request enters the incoming input handler, and then access this state in the outgoing input handler to calculate the response time.

Let's create a protocol stack that measures the response time of the server:

```scala mdoc:silent
import java.util.concurrent.TimeUnit

val incomingInputHandler: Handler[Any, Nothing, String, (Long, String)] =
  Handler.fromFunctionZIO((in: String) => ZIO.clockWith(_.currentTime(TimeUnit.MILLISECONDS)).map(t => (t, in)))

val outgoingInputHandler: Handler[Any, Nothing, (Long, String), (String, Long)] =
  Handler.fromFunctionZIO { case (startedTime: Long, in: String) =>
    ZIO.clockWith(_.currentTime(TimeUnit.MILLISECONDS).map(t => (in, t - startedTime)))
  }

val responseTime: ProtocolStack[Any, String, String, String, (String, Long)] =
  ProtocolStack.interceptHandlerStateful(incomingInputHandler)(outgoingInputHandler)
```

Finally, let's have a handler that converts the input to uppercase and takes some random time to process the request:

```scala mdoc:silent:nest
val handler: Handler[Any, Nothing, String, String] = Handler.identity.mapZIO { (o: String) =>
  ZIO.randomWith(_.nextLongBetween(0, 3000).flatMap(x => ZIO.sleep(Duration.fromMillis(x)))) *> ZIO.succeed(
    o.toUpperCase,
  )
}
```

Now, we are ready to test the `responseTime` protocol stack by applying the `handler` to it:

```scala mdoc
Unsafe.unsafe { implicit unsafe =>
  Runtime.default.unsafe.run(
    ZIO.scoped(responseTime(handler).apply("Hello, World!").debug("Response along with its latency")),
  )
}
```

In the output, we should see the response which is the input converted to uppercase, and the response time in milliseconds.

## Working with ZIO Environment

The first type parameter of the `ProtocolStack` data type represents the ZIO environment. This allows us to obtain access to the services and resources available in the environment when defining the protocol stack, like logging, configuration, database access, etc.

In the following example, we will create a protocol stack that keeps track of the number of requests received by the server by storing the global state (`Ref[Int]`) in the environment:

```scala mdoc:passthrough
import utils._

printSource("zio-http-example/src/main/scala/example/middleware/CounterProtocolStackExample.scala")
```

## Conditional ProtocolStacks

In some cases, we may want to apply a protocol stack conditionally based on some criteria. We can achieve this by using the `cond` and `condZIO` constructors inside the `ProtocolStack` companion object.

They take a predicate function that determines which protocol stack to apply based on the incoming input:


```scala mdoc:silent:reset
```

```scala mdoc:compile-only
import zio._
import zio.http._

def requestCounter[I, O]: ProtocolStack[Ref[Long], I, I, O, O] =
  ProtocolStack.interceptIncomingHandler {
    Handler.fromFunctionZIO[I] { (incomingInput: I) =>
      ZIO.serviceWithZIO[Ref[Long]](_.update(_ + 1)).as(incomingInput)
    }
  }

def getMethodRequestCounter: ProtocolStack[Ref[Long], Request, Request, Response, Response] =
  ProtocolStack
    .cond[Request](_.method.matches(Method.GET))(
      ifTrue = requestCounter[Request, Response],
      ifFalse = ProtocolStack.identity[Request, Response],
    )
```

In the above example, we defined a protocol stack that only counts the number of requests for the `GET` method. The state will be stored in a `Ref[Long]` service in the ZIO environment.
