import BuildHelper._
import Dependencies._
import sbt.librarymanagement.ScalaArtifacts.isScala3
import scala.concurrent.duration._

val releaseDrafterVersion = "5"

// Setting default log level to INFO
val _ = sys.props += ("ZIOHttpLogLevel" -> Debug.ZIOHttpLogLevel)

ThisBuild / resolvers +=
  "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

// CI Configuration
ThisBuild / githubWorkflowJavaVersions := Seq(
  JavaSpec.graalvm(Graalvm.Distribution("graalvm"), "17"),
  JavaSpec.temurin("8"),
)
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
  ) ++ ScoverageWorkFlow(50, 60) ++ JmhBenchmarkWorkflow(1) // ++ BenchmarkWorkFlow()

ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches += RefPredicate.StartsWith(Ref.Tag("v"))
ThisBuild / githubWorkflowPublish       :=
  Seq(
    WorkflowStep.Sbt(
      List("ci-release"),
      name = Some("Release"),
      env = Map(
        "PGP_PASSPHRASE"      -> "${{ secrets.PGP_PASSPHRASE }}",
        "PGP_SECRET"          -> "${{ secrets.PGP_SECRET }}",
        "SONATYPE_PASSWORD"   -> "${{ secrets.SONATYPE_PASSWORD }}",
        "SONATYPE_USERNAME"   -> "${{ secrets.SONATYPE_USERNAME }}",
        "CI_SONATYPE_RELEASE" -> "${{ secrets.CI_SONATYPE_RELEASE }}",
      ),
    ),
    WorkflowStep.Sbt(
      List("ci-release"),
      name = Some("Release Shaded"),
      env = Map(
        Shading.env.PUBLISH_SHADED -> "true",
        "PGP_PASSPHRASE"           -> "${{ secrets.PGP_PASSPHRASE }}",
        "PGP_SECRET"               -> "${{ secrets.PGP_SECRET }}",
        "SONATYPE_PASSWORD"        -> "${{ secrets.SONATYPE_PASSWORD }}",
        "SONATYPE_USERNAME"        -> "${{ secrets.SONATYPE_USERNAME }}",
        "CI_SONATYPE_RELEASE"      -> "${{ secrets.CI_SONATYPE_RELEASE }}",
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
  ).steps ++
    WorkflowJob(
      id = "zio-http-shaded-tests",
      name = "Test shaded version of zio-http",
      steps = List(
        WorkflowStep.Sbt(
          name = Some("zio-http-shaded Tests"),
          commands = List("zioHttpShadedTests/test"),
          cond = Some(s"matrix.scala == '$Scala213'"),
          env = Map(Shading.env.PUBLISH_SHADED -> "true"),
        ),
      ),
    ).steps

inThisBuild(
  List(
    organization := "dev.zio",
    homepage     := Some(url("https://zio.dev/zio-http/")),
    licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  ),
)

ThisBuild / githubWorkflowBuildTimeout := Some(60.minutes)

lazy val aggregatedProjects: Seq[ProjectReference] =
  if (Shading.shadingEnabled) {
    Seq(
      zioHttp,
      zioHttpTestkit,
    )
  } else {
    Seq(
      zioHttp,
      zioHttpBenchmarks,
      zioHttpCli,
      zioHttpExample,
      zioHttpTestkit,
      docs,
    )
  }

lazy val root = (project in file("."))
  .settings(stdSettings("zio-http-root"))
  .settings(publishSetting(false))
  .aggregate(aggregatedProjects: _*)

lazy val zioHttp = (project in file("zio-http"))
  .enablePlugins(Shading.plugins(): _*)
  .settings(stdSettings("zio-http"))
  .settings(publishSetting(true))
  .settings(settingsWithHeaderLicense)
  .settings(meta)
  .settings(Shading.shadingSettings())
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= netty ++ Seq(
      `zio`,
      `zio-streams`,
      `zio-schema`,
      `zio-schema-json`,
      `zio-schema-protobuf`,
      `zio-test`,
      `zio-test-sbt`,
      `netty-incubator`,
    ),
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n <= 12 =>
          Seq(`scala-compact-collection`)
        case _                       => Seq.empty
      }
    },
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) =>
          Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
        case _            => Seq.empty
      }
    },
  )

/**
 * Special subproject to sanity test the shaded version of zio-http. Run using
 * `sbt -Dpublish.shaded zioHttpShadedTests/test`. This will trigger
 * `publishLocal` on zio-http and then run tests using the shaded artifact as a
 * dependency, instead of zio-http classes.
 */
lazy val zioHttpShadedTests = if (Shading.shadingEnabled) {
  (project in file("zio-http-shaded-tests"))
    .settings(stdSettings("zio-http-shaded-tests"))
    .settings(
      Compile / sources        := Nil,
      Test / sources           := (
        baseDirectory.value / ".." / "zio-http" / "src" / "test" / "scala" ** "*.scala" ---
          // Exclude tests of netty specific internal stuff
          baseDirectory.value / ".." / "zio-http" / "src" / "test" / "scala" ** "netty" ** "*.scala"
      ).get,
      Test / scalaSource       := (baseDirectory.value / ".." / "zio-http" / "src" / "test" / "scala"),
      Test / resourceDirectory := (baseDirectory.value / ".." / "zio-http" / "src" / "test" / "resources"),
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
      libraryDependencies ++= Seq(
        `zio-test-sbt`,
        `zio-test`,
        "dev.zio" %% "zio-http-shaded" % version.value,
      ),
    )
    .settings(publishSetting(false))
    .settings(Test / test := (Test / test).dependsOn(zioHttp / publishLocal).value)
} else {
  (project in file(".")).settings(
    Compile / sources := Nil,
    Test / sources    := Nil,
    name              := "noop",
    publish / skip    := true,
  )
}

lazy val zioHttpBenchmarks = (project in file("zio-http-benchmarks"))
  .enablePlugins(JmhPlugin)
  .settings(stdSettings("zio-http-benchmarks"))
  .settings(publishSetting(false))
  .settings(
    libraryDependencies ++= Seq(
//      "com.softwaremill.sttp.tapir" %% "tapir-akka-http-server" % "1.1.0",
      "com.softwaremill.sttp.tapir"   %% "tapir-http4s-server" % "1.5.1",
      "com.softwaremill.sttp.tapir"   %% "tapir-json-circe"    % "1.5.1",
      "com.softwaremill.sttp.client3" %% "core"                % "3.9.0",
//      "dev.zio"                     %% "zio-interop-cats"    % "3.3.0",
      "org.slf4j"                      % "slf4j-api"           % "2.0.9",
      "org.slf4j"                      % "slf4j-simple"        % "2.0.9",
    ),
  )
  .dependsOn(zioHttp)

lazy val zioHttpCli = (project in file("zio-http-cli"))
  .settings(stdSettings("zio-http-cli"))
  .settings(
    libraryDependencies ++= Seq(`zio-cli`),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
      `zio-test`,
      `zio-test-sbt`,
    ),
  )
  .dependsOn(zioHttp)
  .dependsOn(zioHttpTestkit % Test)

lazy val zioHttpExample = (project in file("zio-http-example"))
  .settings(stdSettings("zio-http-example"))
  .settings(publishSetting(false))
  .settings(runSettings(Debug.Main))
  .settings(libraryDependencies ++= Seq(`jwt-core`))
  .dependsOn(zioHttp, zioHttpCli)

lazy val zioHttpTestkit = (project in file("zio-http-testkit"))
  .enablePlugins(Shading.plugins(): _*)
  .settings(stdSettings("zio-http-testkit"))
  .settings(publishSetting(true))
  .settings(Shading.shadingSettings())
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
