---
id: your-first-zio-http-app
title: Your First Zio http app
---

# Tutorial: Your First ZIO HTTP App

ZIO is a modern, high-performance library for asynchronous programming in Scala. It provides a powerful and expressive API for building scalable and reliable applications. ZIO also has a strong focus on type safety and resource management, which makes it a popular choice for building server-side applications.

In this tutorial, we will learn how to build a simple HTTP server using ZIO and the ZIO HTTP module. By the end of this tutorial, you will have a basic understanding of how to build HTTP servers using ZIO and the ZIO HTTP module.

## Introduction

ZIO is a modern, high-performance library for asynchronous programming in Scala. It provides a powerful and expressive API for building scalable and reliable applications. ZIO also has a strong focus on type safety and resource management, which makes it a popular choice for building server-side applications.

In this tutorial, we will learn how to build a simple HTTP server using ZIO and the ZIO HTTP module. By the end of this tutorial, you will have a basic understanding of how to build HTTP servers using ZIO and the ZIO HTTP module.

## Setting up the Project

To get started, we need to set up a new SBT project. Here are the steps to do that:

1. Open your preferred text editor and create a new directory for your project.
2. Open a terminal window and navigate to the project directory.
3. Run the following command to create a new SBT project:

```scala
scalaVersion := "2.13.8"

libraryDependencies ++= Seq(
  "dev.zio" %% "zio" % "2.0.14",
  "dev.zio" %% "zio-streams" % "2.0.14",
  "dev.zio" %% "zio-interop-cats" % "2.5.1.0",
  "dev.zio" %% "zio-logging" % "0.5.12",
  "dev.zio" %% "zio-logging-slf4j" % "0.5.12",
  "org.http4s" %% "http4s-blaze-server" % "1.0.0-M23",
  "org.http4s" %% "http4s-dsl" % "1.0.0-M23"
)
```
4.Save the file.
Building the HTTP Server
Create a new file named Main.scala in the project's root directory.

Open Main.scala and add the following code:

```import zio._
import zio.console._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.blaze.server.BlazeServerBuilder
import scala.concurrent.ExecutionContext.global

object Main extends App {

  val dsl = Http4sDsl[Task]
  import dsl._

  val appLogic: HttpRoutes[Task] = HttpRoutes.of[Task] {
    case GET -> Root / "hello" =>
      Ok("Hello, ZIO HTTP!")
  }

  val server: ZIO[ZEnv, Throwable, Unit] =
    ZIO.runtime[ZEnv].flatMap { implicit rts =>
      BlazeServerBuilder[Task](global)
        .bindHttp(8080, "0.0.0.0")
        .withHttpApp(appLogic.orNotFound)
        .serve
        .compile
        .drain
    }

  def run(args: List[String]): URIO[ZEnv, ExitCode] =
    server.provideCustomLayer(Console.live).exitCode
}
```
.Save the file.

#Running the HTTP Server
To run the HTTP server, open your terminal and navigate to the project's root directory.

Run the following command:

```
sbt run

```
Wait for the server to start. You should see a message indicating that the server is running on http://0.0.0.0:8080.

#Testing the HTTP Server
To test the HTTP server, open your web browser or use a tool like cURL or Postman.

Open your browser and visit http://localhost:8080/hello.

You should see the response "Hello, ZIO HTTP!".

Congratulations! You have successfully built.