---
id: protocol-stack
title: ProtocolStack
---

ProtocolStack is a data type that represents a stack of one or more protocol layers. Each layer in the stack is a function that transforms the incoming and outgoing values of some handler.

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

**Outgoing Input**: This refers to data coming into the middleware from the handler or the previous middleware in the chain. It typically includes the HTTP response from the server, including headers, status codes, and the response body.

**Outgoing Output**: This refers to data leaving the middleware and heading back to the client. It could include modified response data, additional headers, or any other transformations applied by the middleware.

A `ProtocolStack` can be created by combining multiple middleware functions using the `++` operator. Using the `++` operator, we can stack multiple middleware functions on top of each other to create a composite middleware that applies each middleware in the order they are stacked.

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
    handler("Hello World!")
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
    newHandler("Hello World! "),
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
    anotherTrimAndLength(uppercase).apply("Hello World!"),
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

In some cases, we may need to maintain some state along with the protocol stack. For example, we may want to keep track of the number of requests processed or the total response time. We can achieve such stateful behavior by using the `interceptHandlerStateful` constructor.

We need to design a middleware to measure the total response time of the server. To achieve this, we should store the start time when the request enters the incoming input handler, and then return this information along with the incoming output by encapsulating them in a tuple. So the incoming input handler returns a tuple of the state, which is the start time, and the incoming output:

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
    responseTime(handler).apply("Hello, World!").debug("Response along with its latency"),
  )
}
```

In the output, we should see the response which is the input converted to uppercase, and the response time in milliseconds.
