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
val ZHTTPVersion = "1.0.0.0-RC24"

libraryDependencies ++= Seq(
  "io.d11" %% "zhttp" % ZHTTPVersion,
  "io.d11" %% "zhttp-test" % ZHTTPVersion % Test
)
```

## Using Dream11's g8 template

Run the following command on your terminal to set up a ZIO-HTTP project using the provided g8 template:

```shell
sbt new dream11/zio-http.g8
```

### Includes

* [sbt-native-packager](https://github.com/sbt/sbt-native-packager)
* [scalafmt](https://github.com/scalameta/scalafmt)
* [scalafix](https://github.com/scalacenter/scalafix)
    * Included rule(s):
        * [scalafix-organize-imports](https://github.com/liancheng/scalafix-organize-imports)
* [sbt-revolver](https://github.com/spray/sbt-revolver)

