
## Transforming Middleware (Advanced Techniques)

ZIO HTTP offers powerful ways to transform existing middleware functions, enabling to create more complex processing pipelines. Here's a breakdown of key transformation techniques:

#### Transforming Output Type

* **map and mapZIO**: These functions allows to modify the output type of the `Http` object produced by a middleware function.

   - **map:** Takes a pure function that transforms the output value.
  - **mapZIO:** Takes an effectful function (a `ZIO` effect) that transforms the output value.

```scala mdoc:silent 

val mid1: Middleware[Any, Nothing, Nothing, Any, Any, String] = middleware.map((i: Int) => i.toString)  // Pure transformation
val mid2: Middleware[Any, Nothing, Nothing, Any, Any, String] = middleware.mapZIO((i: Int) => ZIO.succeed(s"$i"))  // Effectful transformation
```

#### Transforming Output Type with Intercept

* **intercept and interceptZIO:** These functions create a new middleware by applying transformation functions to both the outgoing response (B) and an additional value (S) generated during the transformation.

```scala mdoc:silent 

val middleware: Middleware[Any, Nothing, String, String, String, Int] = 
  Middleware.intercept[String, String](_.toInt + 2)((_, a) => a + 3)

// Takes two functions: (incoming, outgoing)
// First function transforms String to Int and adds 2
// Second function takes the original String and transformed value, adds 3 to the transformed value
```
#### Transforming Input Type

* **contramap and contramapZIO:** These functions are used to modify the input type of the Http object a middleware function accepts.
  - **contramap:** Takes a pure function that transforms the input value.
  - **contramapZIO:** Takes an effectful function (a ZIO effect) that transforms the input value

```scala mdoc:silent 

val mid1: Middleware[Any, Nothing, Int, Int, String, Int] = middleware.contramap[String](_.toInt)  // Pure transformation
val mid2: Middleware[Any, Nothing, Int, Int, String, Int] = middleware.contramapZIO[String](a => UIO(a.toInt)) // Effectful transformation
```

