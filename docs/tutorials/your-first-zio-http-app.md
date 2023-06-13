---
id: your-first-zio-http-app
title: Your first zio http app
---

# ZIO Quickstart: Hello World

The ZIO Quickstart Hello World is a simple example that demonstrates the basics of writing a ZIO application. It showcases how to interact with the console, read input from the user, and perform simple operations using ZIO's effect system.

## Running The Example

To run the example, follow these steps:

1. Open the console and clone the ZIO Quickstarts project using Git. You can also download the project directly.
   ```
   git clone git@github.com:zio/zio-quickstarts.git
   ```

2. Change the directory to the `zio-quickstarts/zio-quickstart-hello-world` folder.
   ```
   cd zio-quickstarts/zio-quickstart-hello-world
   ```

3. Once you are inside the project directory, execute the following command to run the application:
   ```
   sbt run
   ```

## Testing The Quickstart

The `sbt run` command searches for the executable class defined in the project, which in this case is `zio.dev.quickstart.MainApp`. The code for this class is as follows:

```scala
import zio._

object MainApp extends ZIOAppDefault {
  def run = Console.printLine("Hello, World!")
}
```

This code uses ZIO's `Console.printLine` effect to print "Hello, World!" to the console.

To enhance the quickstart and ask for the user's name, modify the code as shown below:

```scala
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    for {
      _    <- Console.print("Please enter your name: ")
      name <- Console.readLine
      _    <- Console.printLine(s"Hello, $name!")
    } yield ()
}
```

In this updated example, we use a for-comprehension to compose ZIO effects. It prompts the user to enter their name, reads the input using `Console.readLine`, and prints a customized greeting using `Console.printLine`.

Alternatively, you can rewrite the code using explicit `flatMap` operations:

```scala
import zio._

object MainApp extends ZIOAppDefault {
  def run =
    Console.print("Please enter your name: ")
      .flatMap { _ =>
        Console.readLine
          .flatMap { name =>
            Console.printLine(s"Hello, $name!")
          }
      }
}
```

Both versions of the code achieve the same result.

By running the application with the modified code, you will be prompted to enter your name, and the program will respond with a personalized greeting.

Feel free to experiment and modify the code to explore more features and capabilities of ZIO.