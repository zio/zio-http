---
title: "Socket"
sidebar_label: "Socket"
---

`Socket[-R, +E, -A, +B]` models a function from `A` to `ZStream[R, E, B]`. When a value of type `A` is evaluated against a `Socket[R, E, A, B]`, it can either succeed with a `B`, or fail with an `E`, and it could have its requirement on `R`.

## Creating Sockets

### An empty Socket

To create an empty Socket, you can use the `empty` constructor.

```scala
val socket = Socket.empty
```

### Socket that has ended

To create a Socket that has ended, you can use the `end` constructor.

```scala
val socket = Socket.end
```

### Socket that always succeeds

You can use the `succeed` constructor to create a Socket that always returns the same response and never fails.

```scala
val socket = Socket.succeed(WebSocketFrame.text("Hello, from ZIO-HTTP"))
```

### Socket that echoes the message

You can use the `echo` constructor to create a Socket that always echoes back the message.

```scala
val socket = Socket.echo(WebSocketFrame.text("Hello, from ZIO-HTTP"))
```

### Socket from a partial function

`Socket.collect` can create a `Socket[R, E, A, B]` from a `PartialFunction[A, B]`.

```scala
val fromCollect = Socket.collect[WebSocketFrame] {
  case WebSocketFrame.Text("fail") => ZStream.fail(new Exception("error"))
  case WebSocketFrame.Text(text)   => ZStream.succeed(text)
}
```

### Socket from a function

To create a Socket from a function, you can use the `fromFunction` constructor.

```scala
val socket = Socket.fromFunction[WebSocketFrame](wsf => ZStream.succeed(wsf))
```

### Socket from a ZStream

To create a socket from a `ZStream[R, E, B]`, you can use the `fromStream` constructor.

```scala
val transducer = ZTransducer[Int].map(elem => WebSocketFrame.Text(elem.toString))
val stream     = ZStream
  .fromIterable((0 to 10))
  .transduce(transducer)

val socket = Socket.fromStream(stream)
```

## Composing Sockets

### Using `merge`

You can merge to Sockets using the `merge` operator, the resulting Socket will emit the values of both Sockets.

```scala
val s1 = Socket.succeed(WebSocketFrame.text("Hello, from ZIO-HTTP"))
val s2 = Socket.succeed(WebSocketFrame.text("Welcome to the party"))
val socket = s1 merge s2
```

## Transforming Sockets

### `map` over a Socket's output channel

Socket is a domain, so you can use `map` to transform the output of a Socket from type `Socket[R, E, A, B]` to type `Socket[R, E, A, C]`, it takes a function from `B => Socket[R, E, A, C]`.

```scala
val sc     = Socket.succeed("Hello, from ZIO-HTTP")
val socket = sc.map(text => WebSocketFrame.text(text))
```

You can also transform the output of a Socket effecfully using the `mapZIO` operator. It takes a function
 `B => ZIO[R, E, C]` and returns a Socket of type `Socket[R, E, A, C]`.

### `contramap` over a Socket's input channel

Socket also comes with a contramap operator that lets you map over the input of Socket before it gets passed over to it.

```scala
val sc     = Socket.collect[String] { case text => ZStream(text) }
val socket = sc.contramap[WebSocketFrame.Text](wsf => wsf.text)

val res = socket(WebSocketFrame.Text("Hello, from ZIO-HTTP"))
```

 Additionally, you can use the `contramapZIO` operator to transform the input of a Socket effectfully.

```scala
val sc     = Socket.collect[String] { case text => ZStream(text) }
val socket = sc.contramapZIO[Any, Throwable, WebSocketFrame.Text](wsf => ZIO(wsf.text))

val res = socket(WebSocketFrame.Text("Hello, from ZIO-HTTP"))
```

## Providing environment

### Using `provideEnvironment`

You can use the `provideEnvironment` to provide a Socket with its required environment, which eliminates its dependency on R.

:::info
This operation assumes that the Socket requires an environment of type `R`.
:::

```scala
val socket = Socket
  .fromStream(ZStream.environment[WebSocketFrame])
  .provideEnvironment(WebSocketFrame.text("Hello, from ZIO-HTTP"))
```

## Special operators on Socket

There are special operators on Socket that let you transform it into other entities in ZIO-HTTP

:::info
These operators only work if the Socket is an instance of `Socket[R, Throwable, WebSocketFrame, WebSocketFrame]`
:::

### `toHttp`

You can use the `toHttp` operator to convert a Socket to an `HTTP[-R, +E, +A, -B]`.

```scala
val http = Socket.succeed(WebSocketFrame.text("Hello, from ZIO-HTTP")).toHttp
```

### `toResponse`

You can use the `toResponse` operator to convert a Socket to a `Response`.

```scala
val response = Socket.succeed(WebSocketFrame.text("Hello, from ZIO-HTTP")).toResponse
```

### `toSocketApp`

You can use the `toSocketApp` operator to covert a Socket to a `SocketApp`.

```scala
val app = Socket.succeed(WebSocketFrame.text("Hello, from ZIO-HTTP")).toSocketApp
```
