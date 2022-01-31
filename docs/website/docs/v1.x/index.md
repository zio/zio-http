---
sidebar_position: 1
sidebar_label: "Setup"
---

# Setup

In this guide, you'll learn how to get started with a new zio-http project.

Before we dive in, make sure that you have the following on your computer:

* JDK 1.8 or higher
* sbt (scalaVersion >= 2.12)

## As a dependency

To use zio-http, add the following dependencies in your project:

```scala
val ZHTTPVersion = "1.0.0.0-RC23"

libraryDependencies ++= Seq(
  "io.d11" %% "zhttp" % ZHTTPVersion,
  "io.d11" %% "zhttp-test" % ZHTTPVersion % Test
)
```
