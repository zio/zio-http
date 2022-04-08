import BuildHelper._
import Dependencies._

val releaseDrafterVersion = "5"

// CI Configuration
ThisBuild / githubWorkflowJavaVersions   := Seq(JavaSpec.graalvm("21.1.0", "11"), JavaSpec.temurin("8"))
ThisBuild / githubWorkflowPREventTypes   := Seq(
  PREventType.Opened,
  PREventType.Synchronize,
  PREventType.Reopened,
  PREventType.Edited,
  PREventType.Labeled,
)
ThisBuild / githubWorkflowAddedJobs      :=
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
  ) ++ ScoverageWorkFlow(50, 60) ++ BenchmarkWorkFlow() ++ JmhBenchmarkWorkflow(1)

ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches += RefPredicate.StartsWith(Ref.Tag("v"))
ThisBuild / githubWorkflowPublish        :=
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
ThisBuild / githubWorkflowBuildPreamble  := Seq(
  WorkflowStep.Run(
    name = Some("Check formatting"),
    commands = List(s"sbt ++${Scala213} fmtCheck"),
    cond = Some(s"matrix.scala == '${Scala213}'"),
  ),
)

ThisBuild / githubWorkflowBuildPostamble :=
  WorkflowJob(
    "checkDocGeneration",
    "Check doc generation",
    List(
      WorkflowStep.Run(
        commands = List(s"sbt ++${Scala213} doc"),
        name = Some("Check doc generation"),
        cond = Some("${{ github.event_name == 'pull_request' }}"),
      ),
    ),
    scalas = List(Scala213),
  ).steps

lazy val root = (project in file("."))
  .settings(stdSettings("root"))
  .settings(publishSetting(false))
  .aggregate(
    zhttp,
    zhttpBenchmarks,
    zhttpTest,
    example,
  )

lazy val zhttp = (project in file("zio-http"))
  .settings(stdSettings("zhttp"))
  .settings(publishSetting(true))
  .settings(meta)
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
      netty,
      `zio`,
      `zio-streams`,
      `zio-test`,
      `zio-test-sbt`,
      `netty-incubator`,
      `scala-compact-collection`,
      `zio-schema`,
      `zio-schema-json`,
    ),
  )

lazy val zhttpBenchmarks = (project in file("zio-http-benchmarks"))
  .enablePlugins(JmhPlugin)
  .dependsOn(zhttp)
  .settings(stdSettings("zhttpBenchmarks"))
  .settings(publishSetting(false))
  .settings(libraryDependencies ++= Seq(zio))

lazy val zhttpTest = (project in file("zio-http-test"))
  .dependsOn(zhttp)
  .settings(stdSettings("zhttp-test"))
  .settings(publishSetting(true))

lazy val example = (project in file("./example"))
  .settings(stdSettings("example"))
  .settings(publishSetting(false))
  .settings(runSettings("example.Main"))
  .settings(libraryDependencies ++= Seq(`jwt-core`))
  .dependsOn(zhttp)
