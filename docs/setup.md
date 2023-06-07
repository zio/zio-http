---
id: setup
title: "Setup"
---

This guide will walk you through the process of setting up a new ZIO-HTTP project. Before we begin, please ensure that you have the following installed on your computer:

- JDK 1.8 or higher
- sbt (scalaVersion >= 2.12)

## Adding ZIO-HTTP as a Dependency

To use ZIO-HTTP in your project, add the following dependency to your build file:

```scala
libraryDependencies += "dev.zio" %% "zio-http" % "@VERSION@"
```

Replace `@VERSION@` with the desired version of ZIO-HTTP.

## Using Dream11's g8 Template

You can quickly set up a ZIO-HTTP project using Dream11's g8 template. Run the following command in your terminal:

```shell
sbt new dream11/zio-http.g8
```

This command will generate a project structure based on the template.

The template includes the following features:

- [sbt-native-packager](https://github.com/sbt/sbt-native-packager): Enables packaging the application in various formats such as Docker images, RPM packages, or GraalVM native images.
- [scalafmt](https://github.com/scalameta/scalafmt): Automatically formats the source code for consistent formatting across the team.
- [scalafix](https://github.com/scalacenter/scalafix): Provides refactoring and linting capabilities, ensuring code quality and simplifying upgrades.
  - Includes the [scalafix-organize-imports](https://github.com/liancheng/scalafix-organize-imports) rule for organizing imports.
- [sbt-revolver](https://github.com/spray/sbt-revolver): Enables hot-reloading of changes during development.

## Efficient Development Process

The provided dependencies in the Dream11 g8 template enable an efficient development process.

### sbt-revolver for "Hot-Reload" Changes

Sbt-revolver can watch application resources for changes and automatically recompile and restart the application during development. This allows for a fast development turnaround.

To start your application from sbt with automatic hot-reloading, use the following command:

```shell
~reStart
```

Pressing enter will stop watching for changes, but the application will continue running. Use the following command to stop the application without executing shutdown hooks:

```shell
reStop
```

If you already have an sbt server running, such as for IDE integration, use `sbtn` instead of `sbt` to run `~reStart`. This allows multiple sbt sessions to share the server.

### Automatic Source Code Formatting with Scalafmt

Scalafmt will automatically format the source code, ensuring consistent formatting across team members.

### Scalafix for Refactoring and Linting

Scalafix serves as a linting tool during everyday development, detecting issues such as unused dependencies or disabled features. It can also simplify Scala version and dependency upgrades by executing predefined migration paths.

### sbt-native-packager for Packaging Applications

The sbt-native-packager plugin allows packaging the application in popular formats such as Docker images, RPM packages, or GraalVM native images.

By following these steps and leveraging the provided tools, you can efficiently set up and develop your ZIO-HTTP project.