import BuildHelper.{Scala213, meta, publishSetting, stdSettings}
import Dependencies._

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

val releaseDrafterVersion = "5"

// CI Configuration
ThisBuild / githubWorkflowJavaVersions  := Seq(JavaSpec.graalvm("21.1.0", "11"), JavaSpec.temurin("8"))
ThisBuild / githubWorkflowPREventTypes  := Seq(
  PREventType.Opened,
  PREventType.Synchronize,
  PREventType.Reopened,
  PREventType.Edited,
)
ThisBuild / githubWorkflowAddedJobs     :=
  Seq(
    WorkflowJob(
      id = "update_release_draft",
      name = "Release Drafter",
      steps = List(WorkflowStep.Use(UseRef.Public("release-drafter", "release-drafter", s"v${releaseDrafterVersion}"))),
      cond = Option("${{ github.base_ref == 'main' }}"),
    ),
    WorkflowJob(
      id = "update_docs",
      name = "Publish Documentation",
      steps = List(
        WorkflowStep.Use(UseRef.Public("actions", "checkout", s"v2")),
        WorkflowStep.Use(UseRef.Public("actions", "setup-node", s"v2")),
        WorkflowStep.Run(
          env = Map("GIT_PASS" -> "${{secrets.ACTIONS_PAT}}", "GIT_USER" -> "${{secrets.GIT_USER}}"),
          commands = List(
            "cd ./docs/website",
            "npm install",
            "git config --global user.name \"${{secrets.GIT_USER}}\"",
            "npm run deploy",
          ),
        ),
      ),
      cond = Option("${{ github.ref == 'refs/heads/main' }}"),
    ),
  ) ++ ScoverageWorkFlow(50, 60) ++ BenchmarkWorkFlow()

ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches += RefPredicate.StartsWith(Ref.Tag("v"))
ThisBuild / githubWorkflowPublish       :=
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

// Projects

// Project Aggregate
lazy val root = (project in file("."))
  .settings(stdSettings("root"))
  .settings(publishSetting(false))
  .aggregate(zhttp, zhttpBenchmarks, zhttpTest, example)

// Project zio-http
lazy val zhttp = (project in file("./zio-http"))
  .settings(stdSettings("zhttp"))
  .settings(publishSetting(true))
  .settings(meta)
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
      `zio`,
      `zio-streams`,
      `zio-test`,
      `zio-test-sbt`,
      netty,
      `scala-compact-collection`,
      `netty-incubator`,
    ),
  )

// Project Benchmarks
lazy val zhttpBenchmarks = (project in file("./zio-http-benchmarks"))
  .enablePlugins(JmhPlugin)
  .dependsOn(zhttp)
  .settings(stdSettings("zhttpBenchmarks"))
  .settings(publishSetting(false))
  .settings(
    libraryDependencies ++= Seq(zio),
  )

// Project Test
lazy val zhttpTest = (project in file("./zio-http-test"))
  .dependsOn(zhttp)
  .settings(stdSettings("zhttp-test"))
  .settings(publishSetting(true))

// Project Example
lazy val example = (project in file("./example"))
  .enablePlugins(SbtTwirl)
  .settings(stdSettings("example"))
  .settings(libraryDependencies := libraryDependencies.value.map {
    case module if module.name == "twirl-api" =>
      module.cross(CrossVersion.for3Use2_13)
    case module                               => module
  })
  .settings(publishSetting(false))
  .settings(
    fork                      := true,
    Compile / run / mainClass := Option("example.HelloWorld"),
    libraryDependencies ++= Seq(`jwt-core`),
    TwirlKeys.templateImports := Seq(),
  )
  .dependsOn(zhttp)

Global / onChangedBuildSource := ReloadOnSourceChanges
Global / watchAntiEntropy     := FiniteDuration(2000, TimeUnit.MILLISECONDS)
