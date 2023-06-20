---
id: your-first-zio-http-app
title: Your first zio http app
---

# Your first zio-http app

Welcome to the realm of ZIO-HTTP! This guide will take you through the necessary steps to start and set up your first Scala server application. By the end, you'll have a fully functional app that is built.

<br>

### **Pre-requisites**:
To install Scala and ZIO-HTTP, you'll need to have a few prerequisites in place. Here are the steps you can follow:

To install Scala and ZIO-HTTP, you'll need to have a few prerequisites in place. Here are the steps you can follow:

1. **Java Development Kit (JDK):** Scala runs on the Java Virtual Machine (JVM), so you'll need to have a JDK installed. Scala 2.13.x and ZIO-HTTP 1.0.x are compatible with Java 8 or later versions. Ensure that you have a JDK installed on your system by executing the `java -version` command in your terminal or command prompt. If Java is not installed, you can download it from the official Oracle website or use a package manager like Homebrew (for macOS) or apt (for Ubuntu).

2. **Scala Build Tool (sbt):** sbt is the recommended build tool for Scala projects. It simplifies project setup and dependency management. To install sbt, you can follow the installation instructions provided on the sbt website (https://www.scala-sbt.org/download.html). Make sure to download and install the appropriate version for your operating system. [scala](https://www.scala-lang.org/)

3. **IDE or Text Editor (Optional):** While not strictly necessary, having an Integrated Development Environment (IDE) or a text editor with Scala support can enhance your development experience. Popular choices include IntelliJ IDEA with the Scala plugin, Visual Studio Code with the Scala Metals extension, or Sublime Text with the Scala Syntax package.

Once you have these prerequisites set up, you can proceed with creating a new Scala project and adding ZIO-HTTP as a dependency using sbt.

Follow this steps: 

1. Create a new directory for your project `first-zio-http-app` name it what ever you want.

2. Inside the project directory, create a new `build.sbt` file and open it with a text editor.
3. Add the following lines to `build.sbt` to define the project and its dependencies:

      ```scala
      name := "YourProjectName"

      version := "1.0"

      scalaVersion := "2.13.6"

      libraryDependencies ++= Seq(
        "dev.zio" %% "zio-http" % "3.0.0-RC2"
      )

      Compile / unmanagedSourceDirectories += baseDirectory.value / "src"

      ```

4. Save the `build.sbt` file.

5. create a `src` direcotry in the same project directory This is where is `MainApp.scala` will reside in.

6. navigate to the `src` directory and create a scala file name it `MainApp.scala` add the following lines in it.

    ```scala
    import zio._
    import zio.http._

    object MainApp extends ZIOAppDefault {

      val app: App[Any] =
        Http.collect[Request] {
          case Method.GET -> Root / "text" => Response.text("Hello World!")
          case Method.GET -> Root / "fruits" / "b" => Response.text("banana")
          case Method.GET -> Root / "json" => Response.json("""{"greetings": "Hello World!"}""")
        }

      override val run =
        Server.serve(app).provide(Server.default)
    }
    ```
7. move over to the root directory and run the command `sbt run` 


<br>
<br>

**Let's break down what the code does ?**

The code defines an HTTP server using the ZIO HTTP library:

- Imports: The code imports the necessary dependencies from the ZIO and ZIO HTTP libraries.

- `MainApp` object: The `MainApp` object serves as the entry point of the application.

- `app` value: The `app` value is an instance of `zio.http.Http.App[Any]`, which represents an HTTP application. It is constructed using the `Http.collect` method, which allows you to define routes and their corresponding responses.

- Route Definitions: Within the `Http.collect` block, three routes are defined using pattern matching:

   - `case Method.GET -> Root / "text"`: This route matches a GET request with the path "/text". It responds with a text response containing the message "Hello World!".
   - `case Method.GET -> Root / "fruits" / "b"`: This route matches a GET request with the path "/fruits/b". It responds with a text response containing the word "banana".
   - `case Method.GET -> Root / "json"`: This route matches a GET request with the path "/json". It responds with a JSON response containing the message `{"greetings": "Hello World!"}`.

- `run` method: The `run` method is overridden from `ZIOAppDefault` and serves as the entry point for running the application. It starts an HTTP server using `Server.serve`, passing the `app` as the application to serve. The server is provided with a default configuration using `Server.default`.

<br>
<br>

To curl the routes defined in your ZIO HTTP application, you can use the following commands:

1. Route: GET /text

```shell
curl -X GET http://localhost:8080/text
```

2. Route: GET /fruits/b

```shell
curl -X GET http://localhost:8080/fruits/b
```

3. Route: GET /json

```shell
curl -X GET http://localhost:8080/json
```

**You can find the source code of the Complete implementation [Here](https://github.com/daveads/zio-http-examples)**