---
id: handler
title: Handler
---

A `Handler` is responsible for processing the matched incoming request and generating an appropriate response. It is a function that takes a `Request` and produces a `Response`. Thus, it is a crucial component of the ZIO HTTP that determines how the server should respond to a request matched by the corresponding `RoutePattern`.

:::note
In ZIO HTTP, each `Route` consists of a `RoutePattern` and a `Handler`. The `RoutePattern` is responsible for matching the **method** and **path** of the incoming request, while the `Handler` specifies the corresponding action to be taken when the request is matched.
:::note

## Definition

The `Hander` trait is defined as follows:

```scala
sealed trait Handler[-R, +Err, -In, +Out] {
  def apply(in: In): ZIO[R, Err, Out]
}
```

It has four type parameters. The first two parameters `R` and `Err` are the environment and error type of the underlying effect that the handler represents. The third and fourth parameters `In` and `Out` are the input and output types of the handler. In most cases, `In` is `Request` and `Out` is `Response`.

## Creating a Handler

The `Handler` trait comes with a companion object that has different methods to create handlers for various needs.

Additionally, there's a smart constructor called `handler` in the `zio.http` package. It automatically picks the right handler constructor based on the input type. Usually, using `handler` is enough, but if we need more control and specificity, we can also use the methods in the `Handler` companion object.

Let's look at some examples of creating handlers, using the `handler` smart constructor:

```scala mdoc
import zio._
import zio.http._

Routes(

  // 1. A simple handler that returns a "Hello, World!" response
  Method.GET / "hello" -> 
    handler(Response.text("Hello, World!")),
  
  // 2. A handler that echoes the request body
  Method.POST / "echo" ->
    handler { (req: Request) => req.body.asString(Charsets.Utf8).map(Response.text(_)).orDie },

  // 3. A handler that generates a random UUID
  Method.GET / "uuid" -> 
    handler(Random.nextUUID.map(u => Response.text(u.toString))),

  // 4. A handler that takes the name from the path and returns a greeting message
  Method.GET / "name" / string("name") -> 
    handler{ (name: String, _: Request) => (Response.text(s"Hello, $name!")) },

  // 5. A handler that takes the name and age from the path and returns birthday greetings
  Method.GET / "name" / string("name") / "age" / int("age") ->
    handler{ (name: String, age: Int, _: Request) => (Response.text(s"Happy $age-th birthday, $name!")) }

)
```

As we can see, the `handler` constructor is quite versatile and can be used to create handlers for different use cases. It automatically infers proper handler constructors based on the input we pass to it.

1. The first example shows a simple handler that only returns a "Hello, World!" response. It doesn't need any input, so we can directly pass the `Response` to the `handler` constructor.
2. The second example shows a handler that echoes the request body. Since it needs the request body, we pass a function that takes a `Request` and returns a `Response`.

   :::note
   Please note that this handler employs the `orDie` method to transform any failures in the effect into defects. In real-world applications, it's advisable to handle failures more gracefully, such as returning a descriptive error message in the response body. This approach provides clients with a clear understanding of what went wrong. We will delve into error handling in a separate section.
   :::

3. The third example shows a handler that generates a random UUID. It doesn't need any input, but it requires an effect that produces a `UUID`. So, we pass a `ZIO` effect that generates a random `UUID` and returns a `Response`.
4. The fourth example shows a handler that takes the name from the path and returns a greeting message. It needs the name from the path, so we pass a function that takes a `String`, (and also the `Request` which we ignore it using `_`), and returns a `Response`. Please note that whenever we need to access path parameters, we need also to pass the `Request` as an argument to the handler function, even if we don't use it.
5. The fifth example is similar to the previous one, but it takes two path parameters.

## Handler Constructors

As mentioned earlier, it is advisable to use the `handler` smart constructor for convenience. However, in some cases, we might use lower-level handler constructors. Let's look at some of the most commonly used handlers:


