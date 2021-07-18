import java.util.concurrent.TimeUnit
import BuildHelper.{Scala213, publishSetting, stdSettings}

import scala.concurrent.duration.FiniteDuration
import sbt.enablePlugins

// ZIO Version
val zioVersion            = "1.0.9"
val zioConfigVersion      = "1.0.2"
val releaseDrafterVersion = "5"

lazy val root = (project in file("."))
  .settings(stdSettings("root"))
  .settings(publishSetting(false))
  .aggregate(zhttp, zhttpBenchmarks, example)

// CI Configuration
ThisBuild / githubWorkflowAddedJobs :=
  Seq(
    WorkflowJob(
      id = "update_release_draft",
      name = "Release Drafter",
      steps = List(WorkflowStep.Use(UseRef.Public("release-drafter", "release-drafter", s"v${releaseDrafterVersion}"))),
      cond = Option("${{ github.base_ref == 'main' }}"),
    ),
  )
ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches += RefPredicate.StartsWith(Ref.Tag("v"))
ThisBuild / githubWorkflowPublish :=
  Seq(
    WorkflowStep.Sbt(
      List("ci-release"),
      env = Map(
        "PGP_PASSPHRASE"      -> "${{ secrets.PGP_PASSPHRASE }}",
        "PGP_SECRET"          -> "${{ secrets.PGP_SECRET }}",
        "SONATYPE_PASSWORD"   -> "${{ secrets.SONATYPE_PASSWORD }}",
        "SONATYPE_USERNAME"   -> "${{ secrets.SONATYPE_USERNAME }}",
        "CI_SONATYPE_RELEASE" -> "${{ secrets.CI_SONATYPE_RELEASE }}",
      ),
    ),
  )
//scala fix isn't available for scala 3 so ensure we only run the fmt check
//using the latest scala 2.13
ThisBuild / githubWorkflowBuildPreamble :=
  WorkflowJob(
    "fmtCheck",
    "Format",
    List(
      WorkflowStep.Run(List(s"sbt ++${Scala213} fmtCheck"), name = Some("Check formatting")),
    ),
    scalas = List(Scala213),
  ).steps

// Test Configuration
ThisBuild / libraryDependencies ++=
  Seq(
    "dev.zio" %% "zio-test"     % zioVersion % "test",
    "dev.zio" %% "zio-test-sbt" % zioVersion % "test",
  )
ThisBuild / testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")

// Projects

// Project zio-http
lazy val zhttp = (project in file("./zio-http"))
  .settings(stdSettings("zhttp"))
  .settings(publishSetting(true))
  .settings(
    organization := "io.d11",
    organizationName := "d11",
    licenses += ("MIT License", new URL("https://github.com/dream11/zio-http/blob/master/LICENSE")),
    ThisBuild / homepage := Some(url("https://github.com/dream11/zio-http")),
    ThisBuild / scmInfo :=
      Some(
        ScmInfo(url("https://github.com/dream11/zio-http"), "scm:git@github.com:dream11/zio-http.git"),
      ),
    ThisBuild / developers :=
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
    libraryDependencies ++=
      Seq(
        "dev.zio"                %% "zio"                     % zioVersion,
        "dev.zio"                %% "zio-streams"             % zioVersion,
        "io.netty"                % "netty-all"               % "4.1.66.Final",
        "org.scala-lang.modules" %% "scala-collection-compat" % "2.5.0",
      ),
  )

// Project Benchmarks
lazy val zhttpBenchmarks = (project in file("./zio-http-benchmarks"))
  .enablePlugins(JmhPlugin)
  .dependsOn(zhttp)
  .settings(stdSettings("zhttpBenchmarks"))
  .settings(publishSetting(false))
  .settings(
    libraryDependencies ++=
      Seq(
        "dev.zio" %% "zio" % zioVersion,
      ),
  )

lazy val example = (project in file("./example"))
  .settings(stdSettings("example"))
  .settings(publishSetting(false))
  .settings(
    fork := true,
    Compile / run / mainClass := Option("Authentication"),
    libraryDependencies ++= Seq(
      "com.github.jwt-scala" %% "jwt-core" % "8.0.2",
    ),
  )
  .dependsOn(zhttp)

addCommandAlias("fmt", "scalafmt; test:scalafmt; sFix;")
addCommandAlias("fmtCheck", "scalafmtCheck; test:scalafmtCheck; sFixCheck")
addCommandAlias("sFix", "scalafix OrganizeImports; test:scalafix OrganizeImports")
addCommandAlias("sFixCheck", "scalafix --check OrganizeImports; test:scalafix --check OrganizeImports")

Global / onChangedBuildSource := ReloadOnSourceChanges
Global / watchAntiEntropy := FiniteDuration(2000, TimeUnit.MILLISECONDS)
