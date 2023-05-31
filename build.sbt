import BuildHelper._
import Dependencies._
import sbt.librarymanagement.ScalaArtifacts.isScala3

val releaseDrafterVersion = "5"

// Setting default log level to INFO
val _ = sys.props += ("ZIOHttpLogLevel" -> Debug.ZIOHttpLogLevel)

ThisBuild / resolvers +=
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

// CI Configuration
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.graalvm("21.1.0", "11"), JavaSpec.temurin("8"))
ThisBuild / githubWorkflowPREventTypes := Seq(
  PREventType.Opened,
  PREventType.Synchronize,
  PREventType.Reopened,
  PREventType.Edited,
  PREventType.Labeled,
)
ThisBuild / githubWorkflowAddedJobs    :=
  Seq(
    WorkflowJob(
      id = "update_release_draft",
      name = "Release Drafter",
      steps = List(WorkflowStep.Use(UseRef.Public("release-drafter", "release-drafter", s"v${releaseDrafterVersion}"))),
      cond = Option("${{ github.base_ref == 'main' }}"),
    ),
  ) ++ ScoverageWorkFlow(50, 60) ++ BenchmarkWorkFlow() ++ JmhBenchmarkWorkflow(1)

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
ThisBuild / githubWorkflowBuildPreamble := Seq(
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

inThisBuild(
  List(
    organization := "dev.zio",
    homepage     := Some(url("https://zio.dev/zio-http/")),
    licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  ),
)

lazy val root = (project in file("."))
  .settings(stdSettings("zio-http-root"))
  .settings(publishSetting(false))
  .aggregate(
    zioHttp,
    zioHttpBenchmarks,
    zioHttpCli,
    zioHttpExample,
    zioHttpTestkit,
    docs,
  )

lazy val zioHttp = (project in file("zio-http"))
  .settings(stdSettings("zio-http"))
  .settings(publishSetting(true))
  .settings(settingsWithHeaderLicense)
  .settings(meta)
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= netty ++ Seq(
      `zio`,
      `zio-streams`,
      `zio-schema`,
      `zio-schema-json`,
      `zio-test`,
      `zio-test-sbt`,
      `netty-incubator`,
    ),
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n <= 12 => Seq(`scala-compact-collection`)
        case _                       => Seq.empty
      }
    },
  )

lazy val zioHttpBenchmarks = (project in file("zio-http-benchmarks"))
  .enablePlugins(JmhPlugin)
  .settings(stdSettings("zio-http-benchmarks"))
  .settings(publishSetting(false))
  .settings(
    libraryDependencies ++= Seq(
//      "com.softwaremill.sttp.tapir" %% "tapir-akka-http-server" % "1.1.0",
      "com.softwaremill.sttp.tapir" %% "tapir-http4s-server" % "1.5.0",
      "com.softwaremill.sttp.tapir" %% "tapir-json-circe"    % "1.5.0",
//      "dev.zio"                     %% "zio-interop-cats"    % "3.3.0",
    ),
  )
  .dependsOn(zioHttp)

lazy val zioHttpCli = (project in file("zio-http-cli"))
  .settings(stdSettings("zio-http-cli"))
  .settings(libraryDependencies ++= Seq(`zio-cli`))
  .dependsOn(zioHttp)

lazy val zioHttpExample = (project in file("zio-http-example"))
  .settings(stdSettings("zio-http-example"))
  .settings(publishSetting(false))
  .settings(runSettings(Debug.Main))
  .settings(libraryDependencies ++= Seq(`jwt-core`))
  .dependsOn(zioHttp, zioHttpCli)

lazy val zioHttpTestkit = (project in file("zio-http-testkit"))
  .settings(stdSettings("zio-http-testkit"))
  .settings(publishSetting(true))
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= netty ++ Seq(
      `zio`,
      `zio-test`,
      `zio-test-sbt`,
    ),
  )
  .dependsOn(zioHttp)

lazy val docs = project
  .in(file("zio-http-docs"))
  .settings(
    moduleName                                 := "zio-http-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    projectName                                := "ZIO Http",
    mainModuleName                             := (zioHttp / moduleName).value,
    projectStage                               := ProjectStage.Development,
    docsPublishBranch                          := "main",
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(zioHttp),
    ciWorkflowName                             := "Continuous Integration",
    libraryDependencies ++= Seq(
      `jwt-core`,
      "dev.zio" %% "zio-test" % ZioVersion,
    ),
  )
  .dependsOn(zioHttp)
  .enablePlugins(WebsitePlugin)
