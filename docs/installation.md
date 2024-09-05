---
id: installation
title: "Installation"
---

In this guide, we will learn how to get started with a new ZIO HTTP project.

Before we dive in, we should make sure that we have the following on our computer:

* JDK 1.8 or higher
* sbt (scalaVersion >= 2.12)

## Manual Installation

To use ZIO HTTP, we should add the following dependencies in our project:

```scala
libraryDependencies += "dev.zio" %% "zio-http" % "@VERSION@"
```

## Using g8 Template

To set up a ZIO HTTP project using the provided g8 template we can run the following command on our terminal:

```shell
sbt new zio/zio-http.g8
```

This template includes the following plugins:

* [sbt-native-packager](https://github.com/sbt/sbt-native-packager)
* [scalafmt](https://github.com/scalameta/scalafmt)
* [scalafix](https://github.com/scalacenter/scalafix)
* [sbt-revolver](https://github.com/spray/sbt-revolver)

These dependencies in the g8 template were added to enable an efficient development process.

### Hot-reload Changes (Watch Mode)

[Sbt-revolver](https://github.com/spray/sbt-revolver) can watch application resources for change and automatically re-compile and then re-start the application under development. This provides a fast development-turnaround, the closest we can get to real hot-reloading.

We can start our application from _sbt_ with the following command:

```shell
~reStart
```

Pressing enter will stop watching for changes, but not stop the application. We can use the following command to stop the application (shutdown hooks will not be executed):

```
reStop
```

In case we already have an _sbt_ server running, i.e. to provide our IDE with BSP information, we should use _sbtn_ instead of _sbt_ to run `~reStart`, this lets both _sbt_ sessions share one server.

### Formatting Source Code

Scalafmt will automatically format all source code and assert that all team members use consistent formatting.

### Refactoring and Linting

Scalafix will mainly be used as a linting tool during everyday development, for example by removing unused dependencies or reporting errors for disabled features. Additionally, it can simplify upgrades of Scala versions and dependencies, by executing predefined migration paths.

### SBT Native Packager

The `sbt-native-packager` plugin can package the application in the most popular formats, for example, Docker images, RPM packages, or graalVM native images.
