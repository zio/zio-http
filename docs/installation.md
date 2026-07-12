---
id: installation
title: "Installation"
---

In this guide, we will learn how to get started with a new ZIO HTTP project.

Before we dive in, we should make sure that we have the following on our computer:

* JDK 17 or higher
* a Scala build tool such as mill (scalaVersion >= 2.12)

## Manual Installation

To use ZIO HTTP, we should add the following dependencies in our project:

```scala
libraryDependencies += "dev.zio" %% "zio-http" % "@VERSION@"
```

## Mill

This repository is maintained with mill.

Use the checked-in wrapper to compile the codebase:

```shell
./mill __.compile
```

Run the currently configured JVM test suites with:

```shell
./mill core.jvm[2.13.18].test
./mill core.jvm[3.8.3].test
```
