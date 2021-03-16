import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration

import sbt.enablePlugins

// ZIO Version
val zioVersion       = "1.0.4-2"
val zioConfigVersion = "1.0.0"
val circeVersion     = "0.13.0"
val scala_2_13       = "2.13.3"

lazy val supportedScalaVersions = List(scala_2_13)

Global / scalaVersion := scala_2_13

lazy val root = (project in file("."))
  .settings(
    skip in publish := true,
    name := "root",
  )
  .aggregate(zhttp, zhttpBenchmarks, example)

// Test Configuration
ThisBuild / libraryDependencies ++=
  Seq(
    "dev.zio" %% "zio-test"     % zioVersion % "test",
    "dev.zio" %% "zio-test-sbt" % zioVersion % "test",
  )
ThisBuild / testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

// Scalafix
ThisBuild / scalafixDependencies += "com.github.liancheng" %% "organize-imports" % "0.5.0"

// Projects

// Project zio-http
lazy val zhttp = (project in file("./zio-http"))
  .settings(
    version := "1.0.0-RC2.1",
    organization := "io.d11",
    organizationName := "d11",
    crossScalaVersions := supportedScalaVersions,
    licenses += ("MIT License", new URL("https://github.com/dream11/zio-http/blob/master/LICENSE")),
    homepage in ThisBuild := Some(url("https://github.com/dream11/zio-http")),
    scmInfo in ThisBuild :=
      Some(
        ScmInfo(url("https://github.com/dream11/zio-http"), "scm:git@github.com:dream11/zio-http.git"),
      ),
    developers in ThisBuild :=
      List(
        Developer(
          "tusharmath",
          "Tushar Mathur",
          "tushar@dream11.com",
          new URL("https://github.com/tusharmath"),
        ),
        Developer(
          "amitksingh1490",
          "Amit Kumar Singh",
          "amit.singh@dream11.com",
          new URL("https://github.com/amitksingh1490"),
        ),
      ),
    publishMavenStyle in ThisBuild := true,
    crossPaths in ThisBuild := false,
    publishTo := {
      val nexus = "https://s01.oss.sonatype.org/"
      if (isSnapshot.value) Some("snapshots" at nexus + "content/repositories/snapshots")
      else Some("releases" at nexus + "service/local/staging/deploy/maven2")
    },
    libraryDependencies ++=
      Seq(
        "dev.zio" %% "zio"         % zioVersion,
        "dev.zio" %% "zio-streams" % zioVersion,
        "io.netty" % "netty-all"   % "4.1.59.Final",
      ),
  )

// Project Benchmarks
lazy val zhttpBenchmarks = (project in file("./zio-http-benchmarks"))
  .enablePlugins(JmhPlugin)
  .dependsOn(zhttp)
  .settings(
    skip in publish := true,
    libraryDependencies ++=
      Seq(
        "dev.zio" %% "zio" % zioVersion,
      ),
  )

lazy val example = (project in file("./example"))
  .settings(
    fork := true,
    skip in publish := true,
    mainClass in (Compile, run) := Option("HelloWorldAdvanced"),
  )
  .dependsOn(zhttp)

Global / onChangedBuildSource := ReloadOnSourceChanges

// Compiler options
// RECOMMENDED SETTINGS: https://tpolecat.github.io/2017/04/25/scalac-flags.html
Global / scalacOptions ++=
  Seq(
    "-language:postfixOps",          // Added by @tusharmath
    "-deprecation",                  // Emit warning and location for usages of deprecated APIs.
    "-encoding",
    "utf-8",                         // Specify character encoding used by source files.
    "-explaintypes",                 // Explain type errors in more detail.
    "-feature",                      // Emit warning and location for usages of features that should be imported explicitly.
    "-language:existentials",        // Existential types (besides wildcard types) can be written and inferred
    "-language:experimental.macros", // Allow macro definition (besides implementation and application)
    "-language:higherKinds",         // Allow higher-kinded types
    "-language:implicitConversions", // Allow definition of implicit functions called views
    "-unchecked",                    // Enable additional warnings where generated code depends on assumptions.
    "-Xcheckinit",                   // Wrap field accessors to throw an exception on uninitialized access.
    "-Xfatal-warnings",              // Fail the compilation if there are any warnings.
    "-Xlint:adapted-args",           // Warn if an argument list is modified to match the receiver.
    "-Xlint:constant",               // Evaluation of a constant arithmetic expression results in an error.
    "-Xlint:delayedinit-select",     // Selecting member of DelayedInit.
    "-Xlint:doc-detached",           // A Scaladoc comment appears to be detached from its element.
    "-Xlint:inaccessible",           // Warn about inaccessible types in method signatures.
    "-Xlint:missing-interpolator",   // A string literal appears to be missing an interpolator id.
    "-Xlint:nullary-unit",           // Warn when nullary methods return Unit.
    "-Xlint:option-implicit",        // Option.apply used implicit view.
    "-Xlint:package-object-classes", // Class or object defined in package object.
    "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:private-shadow",         // A private field (or class parameter) shadows a superclass field.
    "-Xlint:stars-align",            // Pattern sequence wildcard must align with sequence component.
    "-Xlint:type-parameter-shadow",  // A local type parameter shadows a type already in scope.
    "-Ywarn-dead-code",              // Warn when dead code is identified.
    "-Ywarn-extra-implicit",         // Warn when more than one implicit parameter section is defined.
    "-Ywarn-numeric-widen",          // Warn when numerics are widened.
    "-Ywarn-unused:implicits",       // Warn if an implicit parameter is unused.
    "-Ywarn-unused:imports",         // Warn if an import selector is not referenced.
    "-Ywarn-unused:locals",          // Warn if a local definition is unused.
    "-Ywarn-unused:params",          // Warn if a value parameter is unused.
    "-Ywarn-unused:patvars",         // Warn if a variable bound in a pattern is unused.
    "-Ywarn-unused:privates",        // Warn if a private member is unused.
    "-Ywarn-value-discard",          // Warn when non-Unit expression results are unused.

    // FIXME: Disabled because of scalac bug https://github.com/scala/bug/issues/11798
    //  "-Xlint:infer-any",                 // Warn when a type argument is inferred to be `Any`.
    //  "-Ywarn-infer-any",                 // Warn when a type argument is inferred to be `Any`.
  )

addCommandAlias("fmt", "scalafmt; test:scalafmt; sFix;")
addCommandAlias("fmtCheck", "scalafmtCheck; test:scalafmtCheck; sFixCheck")
addCommandAlias("sFix", "scalafix OrganizeImports; test:scalafix OrganizeImports")
addCommandAlias("sFixCheck", "scalafix --check OrganizeImports; test:scalafix --check OrganizeImports")

Global / semanticdbEnabled := true
Global / semanticdbVersion := scalafixSemanticdb.revision
Global / watchAntiEntropy := FiniteDuration(2000, TimeUnit.MILLISECONDS)
