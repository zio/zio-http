import BuildHelper.*
import Dependencies.{scalafmt, *}

import scala.concurrent.duration.*

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
      zioHttpCommonJVM,
      zioHttpCommonJS,
      zioHttpServerJVM,
      zioHttpServerJS,
      zioHttpClientJVM,
      zioHttpClientJS,
      zioHttpEndpointJVM,
      zioHttpEndpointJS,
      zioHttpJVM,
      zioHttpJS,
      zioHttpTestkit,
    )
  } else {
    Seq(
      zioHttpCommonJVM,
      zioHttpCommonJS,
      zioHttpServerJVM,
      zioHttpServerJS,
      zioHttpClientJVM,
      zioHttpClientJS,
      zioHttpEndpointJVM,
      zioHttpEndpointJS,
      zioHttpJVM,
      zioHttpJS,
      zioHttpBenchmarks,
      zioHttpCli,
      zioHttpGen,
      zioHttpHtmx,
      zioHttpExample,
      zioHttpTestkit,
      docs,
    )
  }

lazy val root = (project in file("."))
  .settings(stdSettings("zio-http-root"))
  .settings(publishSetting(false))
  .aggregate(aggregatedProjects*)

lazy val zioHttpCommon = crossProject(JSPlatform, JVMPlatform)
  .in(file("zio-http-common"))
  .settings(stdSettings("zio-http-common"))
  .settings(publishSetting(true))
  .settings(settingsWithHeaderLicense)
  .settings(meta)
  .settings(crossProjectSettings)
  .settings(`zio`, `zio-schema`, `zio-schema-json`)
  .settings(libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
      case _            => Seq.empty
    }
  })

lazy val zioHttpCommonJS = zioHttpCommon.js

lazy val zioHttpCommonJVM = zioHttpCommon.jvm

lazy val zioHttpServer = crossProject(JSPlatform, JVMPlatform)
  .in(file("zio-http-server"))
  .settings(stdSettings("zio-http-server"))
  .settings(publishSetting(true))
  .settings(settingsWithHeaderLicense)
  .settings(meta)
  .settings(crossProjectSettings)
  .dependsOn(zioHttpCommon)
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    `zio`,
    `zio-schema`,
    `zio-schema-json`,
    `zio-schema-protobuf`,
  )

lazy val zioHttpServerJS = zioHttpServer.js

lazy val zioHttpServerJVM = zioHttpServer.jvm

lazy val zioHttpClient = crossProject(JSPlatform, JVMPlatform)
  .in(file("zio-http-client"))
  .settings(stdSettings("zio-http-client"))
  .settings(publishSetting(true))
  .settings(settingsWithHeaderLicense)
  .settings(meta)
  .settings(crossProjectSettings)
  .dependsOn(zioHttpCommon)
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    `zio`,
    `zio-schema`,
    `zio-schema-json`,
    `zio-schema-protobuf`,
  )

lazy val zioHttpClientJS = zioHttpClient.js

lazy val zioHttpClientJVM = zioHttpClient.jvm

lazy val zioHttpEndpoint = crossProject(JSPlatform, JVMPlatform)
  .in(file("zio-http-endpoint"))
  .settings(stdSettings("zio-http-endpoint"))
  .settings(publishSetting(true))
  .settings(settingsWithHeaderLicense)
  .settings(meta)
  .settings(crossProjectSettings)
  .dependsOn(zioHttpCommon, zioHttpServer, zioHttpClient)
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    `zio`,
    `zio-schema`,
    `zio-schema-json`,
    `zio-schema-protobuf`,
  )

lazy val zioHttpEndpointJS = zioHttpEndpoint.js

lazy val zioHttpEndpointJVM = zioHttpEndpoint.jvm

lazy val zioHttpNettyCommon = project
  .in(file("zio-http-netty-common"))
  .settings(stdSettings("zio-http-netty-common"))
  .settings(publishSetting(true))
  .settings(settingsWithHeaderLicense)
  .settings(meta)
  .dependsOn(zioHttpCommonJVM)
  .settings(
    `zio`,
    libraryDependencies ++= netty ++ Seq(`netty-incubator`),
  )

lazy val zioHttpServerNetty = project
  .in(file("zio-http-server-netty"))
  .settings(stdSettings("zio-http-server-netty"))
  .settings(publishSetting(true))
  .settings(settingsWithHeaderLicense)
  .settings(meta)
  .dependsOn(zioHttpServerJVM, zioHttpNettyCommon)
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    `zio`,
    `zio-schema`,
    libraryDependencies ++= netty ++ Seq(`netty-incubator`),
  )

lazy val zioHttpClientNetty = project
  .in(file("zio-http-client-netty"))
  .settings(stdSettings("zio-http-client-netty"))
  .settings(publishSetting(true))
  .settings(settingsWithHeaderLicense)
  .settings(meta)
  .dependsOn(zioHttpClientJVM, zioHttpNettyCommon)
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    `zio`,
    `zio-schema`,
    libraryDependencies ++= netty ++ Seq(`netty-incubator`),
  )

lazy val zioHttpClientFetch = crossProject(JSPlatform)
  .in(file("zio-http-client-fetch"))
  .settings(stdSettings("zio-http-client-fetch"))
  .settings(publishSetting(true))
  .settings(settingsWithHeaderLicense)
  .settings(meta)
  .settings(crossProjectSettings)
  .dependsOn(zioHttpClient)
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    `zio`,
    `zio-schema`,
    `zio-schema-json`,
    `zio-schema-protobuf`,
  )
  .js

lazy val zioHttp = crossProject(JSPlatform, JVMPlatform)
  .in(file("zio-http"))
  .enablePlugins(Shading.plugins()*)
  .settings(stdSettings("zio-http"))
  .settings(publishSetting(true))
  .settings(settingsWithHeaderLicense)
  .settings(meta)
  .settings(crossProjectSettings)
  .settings(Shading.shadingSettings())
  .dependsOn(zioHttpEndpoint)
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    `zio`,
    `zio-schema`,
    `zio-schema-json`,
    `zio-schema-protobuf`,
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
//  .jsSettings(
//    testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
//    libraryDependencies ++= Seq(
//      "io.github.cquiroz" %%% "scala-java-time"      % "2.5.0",
//      "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.5.0",
//      "org.scala-js"      %%% "scalajs-dom"          % "2.8.0",
//      "dev.zio"           %%% "zio-test"             % ZioVersion % "test",
//      "dev.zio"           %%% "zio-test-sbt"         % ZioVersion % "test",
//      "dev.zio"           %%% "zio"                  % ZioVersion,
//      "dev.zio"           %%% "zio-cli"              % ZioCliVersion,
//      "dev.zio"           %%% "zio-streams"          % ZioVersion,
//      "dev.zio"           %%% "zio-schema"           % ZioSchemaVersion,
//      "dev.zio"           %%% "zio-schema-json"      % ZioSchemaVersion,
//      "dev.zio"           %%% "zio-schema-protobuf"  % ZioSchemaVersion,
//      "dev.zio"           %%% "zio-prelude"          % "1.0.0-RC21",
//    ),
//  )

lazy val zioHttpJS = zioHttp.js
  .settings(scalaJSUseMainModuleInitializer := true)
  .dependsOn(zioHttpClientFetch)

lazy val zioHttpJVM = zioHttp.jvm
  .dependsOn(zioHttpServerNetty, zioHttpClientNetty)

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
        "dev.zio" %% "zio-http-shaded" % version.value,
      ),
    )
    .settings(publishSetting(false))
    .settings(Test / test := (Test / test).dependsOn(zioHttpJVM / publishLocal).value)
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
      "com.softwaremill.sttp.client3" %% "core"                % "3.9.1",
//      "dev.zio"                     %% "zio-interop-cats"    % "3.3.0",
      "org.slf4j"                      % "slf4j-api"           % "2.0.11",
      "org.slf4j"                      % "slf4j-simple"        % "2.0.11",
    ),
  )
  .dependsOn(zioHttpJVM)

lazy val zioHttpCli = (project in file("zio-http-cli"))
  .settings(stdSettings("zio-http-cli"))
  .settings(
    `zio`,
    `zio-cli`,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  )
  .dependsOn(zioHttpJVM)
  .dependsOn(zioHttpTestkit % Test)

lazy val zioHttpHtmx = (project in file("zio-http-htmx"))
  .settings(
    stdSettings("zio-http-htmx"),
    publishSetting(true),
  )
  .dependsOn(zioHttp)

lazy val zioHttpExample = (project in file("zio-http-example"))
  .settings(stdSettings("zio-http-example"))
  .settings(publishSetting(false))
  .settings(runSettings(Debug.Main))
  .settings(libraryDependencies ++= Seq(`jwt-core`))
  .dependsOn(zioHttpJVM, zioHttpCli)

lazy val zioHttpGen = project
  .in(file("zio-http-gen"))
  .settings(stdSettings("zio-http-gen"))
  .settings(publishSetting(true))
  .settings(
    `zio`,
    libraryDependencies += scalafmt.cross(CrossVersion.for3Use2_13),
  )
  .dependsOn(zioHttpJVM)

lazy val zioHttpTestkit = project
  .in(file("zio-http-testkit"))
  .enablePlugins(Shading.plugins()*)
  .settings(stdSettings("zio-http-testkit"))
  .settings(publishSetting(true))
  .settings(Shading.shadingSettings())
  .settings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    `zio`,
  )
  .dependsOn(zioHttpJVM)

lazy val docs = project
  .in(file("zio-http-docs"))
  .settings(
    moduleName                                 := "zio-http-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    projectName                                := "ZIO Http",
    mainModuleName                             := (zioHttpJVM / moduleName).value,
    projectStage                               := ProjectStage.Development,
    docsPublishBranch                          := "main",
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(zioHttpJVM),
    ciWorkflowName                             := "Continuous Integration",
    libraryDependencies ++= Seq(
      `jwt-core`,
      "dev.zio" %% "zio-test" % ZioVersion,
    ),
  )
  .dependsOn(zioHttpJVM)
  .enablePlugins(WebsitePlugin)
