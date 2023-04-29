---
id: setup
title: "Setup"
---

In this guide, you'll learn how to get started with a new zio-http project.

Before we dive in, make sure that you have the following on your computer:

* JDK 1.8 or higher
* sbt (scalaVersion >= 2.12)

## As a dependency

To use zio-http, add the following dependencies in your project:

```scala
libraryDependencies += "dev.zio" %% "zio-http" % "@VERSION@"
```

## Using our g8 template

Run the following command on your terminal to set up a ZIO Http project using the provided g8 template:

```shell
sbt new zio/zio-http.g8
```

### Includes

* [sbt-native-packager](https://github.com/sbt/sbt-native-packager)
* [scalafmt](https://github.com/scalameta/scalafmt)
* [scalafix](https://github.com/scalacenter/scalafix)
    * Included rule(s):
        * [scalafix-organize-imports](https://github.com/liancheng/scalafix-organize-imports)
* [sbt-revolver](https://github.com/spray/sbt-revolver)

## Efficient development process

The dependencies in the g8 template were added to enable an efficient development process.

### sbt-revolver "hot-reload" changes

Sbt-revolver can watch application resources for change and automatically re-compile and then re-start the application under development. This provides a fast development-turnaround, the closest you can get to real hot-reloading.

Start your application from _sbt_ with the following command

```shell
~reStart
```

Pressing enter will stop watching for changes, but not stop the application. Use the following command to stop the application (shutdown hooks will not be executed).

```
reStop
```

In case you already have an _sbt_ server running, i.e. to provide your IDE with BSP information, use _sbtn_ instead of _sbt_ to run `~reStart`, this let's both _sbt_  sessions share one server.

### scalafmt automatically format source code

scalafmt will automatically format all source code and assert that all team members use consistent formatting.

### scalafix refactoring and linting

scalafix will mainly be used as a linting tool during everyday development, for example by removing unused dependencies or reporting errors for disabled features. Additionally it can simplify upgrades of Scala versions and dependencies, by executing predefined migration paths.

### sbt-native-packager

sbt-native-packager can package the application in the most popular formats, for example Docker images, rpm packages or graalVM native images.
