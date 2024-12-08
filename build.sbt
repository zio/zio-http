import BuildHelper.*
import Dependencies.{scalafmt, *}

import scala.concurrent.duration.*

val releaseDrafterVersion = "5"

// Setting default log level to INFO
val _ = sys.props += ("ZIOHttpLogLevel" -> Debug.ZIOHttpLogLevel)

// CI Configuration
ThisBuild / githubWorkflowEnv += ("JDK_JAVA_OPTIONS" -> "-Xms4G -Xmx8G -XX:+UseG1GC -Xss10M -XX:ReservedCodeCacheSize=1G -XX:NonProfiledCodeHeapSize=512m -Dfile.encoding=UTF-8")
ThisBuild / githubWorkflowEnv += ("SBT_OPTS" -> "-Xms4G -Xmx8G -XX:+UseG1GC -Xss10M -XX:ReservedCodeCacheSize=1G -XX:NonProfiledCodeHeapSize=512m -Dfile.encoding=UTF-8")

ThisBuild / resolvers ++= Resolver.sonatypeOssRepos("snapshots")

ThisBuild / githubWorkflowJavaVersions := Seq(
  JavaSpec.graalvm(Graalvm.Distribution("graalvm"), "17"),
  JavaSpec.graalvm(Graalvm.Distribution("graalvm"), "21"),
  JavaSpec.temurin("17"),
  JavaSpec.temurin("21"),
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
    WorkflowJob(
      id = "mima_check",
      name = "Mima Check",
      steps = List(
        WorkflowStep.Use(UseRef.Public("actions", "checkout", "v4"), Map("fetch-depth" -> "0")),
        WorkflowStep.Use(UseRef.Public("coursier", "setup-action", "v1")),
      ) ++ WorkflowStep.SetupJava(List(JavaSpec.temurin("21"))) :+ WorkflowStep.Sbt(List("mimaChecks")),
      cond = Option("${{ github.event_name == 'pull_request' }}"),
      javas = List(JavaSpec.temurin("21")),
    ),
  ) ++ ScoverageWorkFlow(50, 60) ++ JmhBenchmarkWorkflow(1) ++ BenchmarkWorkFlow()

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
  WorkflowStep.Use(UseRef.Public("coursier", "setup-action", "v1")),
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
      WorkflowStep.Use(UseRef.Public("coursier", "setup-action", "v1")),
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
      zioHttpJVM,
      zioHttpJS,
      zioHttpTestkit,
    )
  } else {
    Seq(
      zioHttpJVM,
      zioHttpJS,
      zioHttpBenchmarks,
      zioHttpCli,
      zioHttpGen,
      sbtZioHttpGrpc,
      sbtZioHttpGrpcTests,
      zioHttpHtmx,
      zioHttpExample,
      zioHttpReactjs,
      zioHttpTestkit,
      zioHttpTools,
      docs,
    )
  }

lazy val root = (project in file("."))
  .settings(stdSettings("zio-http-root"))
  .settings(publishSetting(false))
  .aggregate(aggregatedProjects *)

lazy val zioHttp = crossProject(JSPlatform, JVMPlatform)
  .in(file("zio-http"))
  .enablePlugins(Shading.plugins() *)
  .settings(stdSettings("zio-http"))
  .settings(publishSetting(true))
  .settings(settingsWithHeaderLicense)
  .settings(meta)
  .settings(crossProjectSettings)
  .settings(Shading.shadingSettings())
  .settings(
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) =>
          Seq("org.scala-lang" % "scala-reflect" % scalaVersion.value)
        case _            => Seq.empty
      }
    },
  )
  .jvmSettings(
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
      `zio`,
      `zio-streams`,
      `zio-schema`,
      `zio-schema-json`,
      `zio-schema-protobuf`,
      `zio-test`,
      `zio-test-sbt`,
    ),
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n <= 12 =>
          Seq(`scala-compact-collection`)
        case _                       => Seq.empty
      }
    },
    libraryDependencies ++= netty ++ Seq(`netty-incubator`),
  )
  .jvmSettings(MimaSettings.mimaSettings(failOnProblem = true))
  .jsSettings(
    ThisProject / fork := false,
    testFrameworks     := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
    libraryDependencies ++= Seq(
      "io.github.cquiroz" %%% "scala-java-time"      % "2.5.0",
      "io.github.cquiroz" %%% "scala-java-time-tzdb" % "2.5.0",
      "org.scala-js"      %%% "scalajs-dom"          % "2.8.0",
      "dev.zio"           %%% "zio-test"             % ZioVersion % "test",
      "dev.zio"           %%% "zio-test-sbt"         % ZioVersion % "test",
      "dev.zio"           %%% "zio"                  % ZioVersion,
      "dev.zio"           %%% "zio-streams"          % ZioVersion,
      "dev.zio"           %%% "zio-schema"           % ZioSchemaVersion,
      "dev.zio"           %%% "zio-schema-json"      % ZioSchemaVersion,
      "dev.zio"           %%% "zio-schema-protobuf"  % ZioSchemaVersion,
    ),
  )

lazy val zioHttpJS = zioHttp.js
  .settings(scalaJSUseMainModuleInitializer := true)

lazy val zioHttpJVM = zioHttp.jvm

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
      "com.softwaremill.sttp.client3" %% "core"                % "3.9.5",
//      "dev.zio"                     %% "zio-interop-cats"    % "3.3.0",
      "org.slf4j"                      % "slf4j-api"           % "2.0.13",
      "org.slf4j"                      % "slf4j-simple"        % "2.0.13",
    ),
  )
  .dependsOn(zioHttpJVM)

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
  .dependsOn(zioHttpJVM)
  .dependsOn(zioHttpTestkit % Test)

lazy val zioHttpHtmx = (project in file("zio-http-htmx"))
  .settings(
    stdSettings("zio-http-htmx"),
    publishSetting(true),
    libraryDependencies ++= Seq(
      `zio-test`,
      `zio-test-sbt`,
    ),
  )
  .dependsOn(zioHttpJVM)
  .settings(MimaSettings.mimaSettings(failOnProblem = true))

lazy val zioHttpExample = (project in file("zio-http-example"))
  .settings(stdSettings("zio-http-example"))
  .settings(publishSetting(false))
  .settings(runSettings(Debug.Main))
  .settings(libraryDependencies ++= Seq(`jwt-core`, `zio-schema-json`))
  .settings(
    run / fork := true,
    run / javaOptions ++= Seq("-Xms4G", "-Xmx4G", "-XX:+UseG1GC"),
    libraryDependencies ++= Seq(
      `zio-config`,
      `zio-config-magnolia`,
      `zio-config-typesafe`,
      "dev.zio" %% "zio-metrics-connectors"            % "2.3.1",
      "dev.zio" %% "zio-metrics-connectors-prometheus" % "2.3.1",
    ),
  )
  .dependsOn(zioHttpJVM, zioHttpCli, zioHttpGen)

lazy val zioHttpReactjs = (project in file("zio-http-reactjs"))
  .settings(stdSettings("zio-http-reactjs"))
  .settings(publishSetting(false))
  .settings(runSettings(Debug.Main))
  .settings(libraryDependencies ++= Seq(`jwt-core`, `zio-schema-json`))
  .settings(
    run / fork := true,
    run / javaOptions ++= Seq("-Xms4G", "-Xmx4G", "-XX:+UseG1GC"),
    libraryDependencies ++= Seq(
      `zio-config`,
      `zio-config-magnolia`,
      `zio-config-typesafe`,
      "dev.zio" %% "zio-metrics-connectors"            % "2.3.1",
      "dev.zio" %% "zio-metrics-connectors-prometheus" % "2.3.1",
    ),
  )
  .dependsOn(zioHttpJVM, zioHttpCli, zioHttpGen)

lazy val zioHttpTools = (project in file("zio-http-tools"))
  .settings(stdSettings("zio-http-tools"))
  .settings(publishSetting(false))
  .settings(runSettings(Debug.Main))
  .dependsOn(zioHttpJVM)

lazy val zioHttpGen = (project in file("zio-http-gen"))
  .settings(stdSettings("zio-http-gen"))
  .settings(publishSetting(true))
  .settings(
    libraryDependencies ++= Seq(
      `zio`,
      `zio-test`,
      `zio-test-sbt`,
      `zio-config`,
      scalafmt.cross(CrossVersion.for3Use2_13),
      scalametaParsers
        .cross(CrossVersion.for3Use2_13)
        .exclude("org.scala-lang.modules", "scala-collection-compat_2.13"),
      `zio-json-yaml` % Test,
    ),
  )
  .settings(
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n <= 12 =>
          Seq(`scala-compact-collection`)
        case _                       => Seq.empty
      }
    },
  )
  .dependsOn(zioHttpJVM)

lazy val sbtZioHttpGrpc = (project in file("sbt-zio-http-grpc"))
  .settings(stdSettings("sbt-zio-http-grpc"))
  .settings(publishSetting(true))
  .settings(
    libraryDependencies ++= Seq(
      "com.thesamet.scalapb" %% "compilerplugin"  % "0.11.15",
      "com.thesamet.scalapb" %% "scalapb-runtime" % "0.11.15" % "protobuf",
      "com.google.protobuf"   % "protobuf-java"   % "4.26.1"  % "protobuf",
    ),
  )
  .settings(
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n <= 12 =>
          Seq(`scala-compact-collection`)
        case _                       => Seq.empty
      }
    },
  )
  .dependsOn(zioHttpJVM, zioHttpGen)

lazy val sbtZioHttpGrpcTests = (project in file("sbt-zio-http-grpc-tests"))
  .enablePlugins(LocalCodeGenPlugin)
  .settings(stdSettings("sbt-zio-http-grpc-tests"))
  .settings(publishSetting(false))
  .settings(
    Test / skip          := (CrossVersion.partialVersion(scalaVersion.value) != Some((2, 12))),
    ideSkipProject       := (CrossVersion.partialVersion(scalaVersion.value) != Some((2, 12))),
    libraryDependencies ++= Seq(
      `zio-test-sbt`,
      `zio-test`,
      "com.google.protobuf"   % "protobuf-java"   % "4.26.1"  % "protobuf",
      "com.thesamet.scalapb" %% "scalapb-runtime" % "0.11.15" % "protobuf",
    ),
    Compile / run / fork := true,
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    Compile / PB.targets := {
      if (CrossVersion.partialVersion(scalaVersion.value) == Some((2, 12)))
        Seq(
          scalapb.gen(grpc = false)                  -> (Compile / sourceManaged).value,
          genModule("zio.http.grpc.ZIOHttpGRPCGen$") -> (Compile / sourceManaged).value,
        )
      else Seq.empty
    },
    codeGenClasspath     := (sbtZioHttpGrpc / Compile / fullClasspath).value,
  )
  .dependsOn(zioHttpJVM, sbtZioHttpGrpc)
  .disablePlugins(ScalafixPlugin)

lazy val zioHttpTestkit = (project in file("zio-http-testkit"))
  .enablePlugins(Shading.plugins() *)
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
  .dependsOn(zioHttpJVM)

lazy val docs = project
  .in(file("zio-http-docs"))
  .settings(stdSettings("zio-http-docs"))
  .settings(
    fork                                       := false,
    moduleName                                 := "zio-http-docs",
    scalacOptions -= "-Yno-imports",
    scalacOptions -= "-Xfatal-warnings",
    projectName                                := "ZIO Http",
    mainModuleName                             := (zioHttpJVM / moduleName).value,
    projectStage                               := ProjectStage.Development,
    ScalaUnidoc / unidoc / unidocProjectFilter := inProjects(zioHttpJVM),
    ciWorkflowName                             := "Continuous Integration",
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
      `jwt-core`,
      "dev.zio" %% "zio-test" % ZioVersion,
      `zio-config`,
      `zio-config-magnolia`,
      `zio-config-typesafe`,
    ),
    publish / skip                             := true,
    mdocVariables ++= Map(
      "ZIO_VERSION"        -> ZioVersion,
      "ZIO_SCHEMA_VERSION" -> ZioSchemaVersion,
      "ZIO_CONFIG_VERSION" -> ZioConfigVersion,
    ),
  )
  .dependsOn(zioHttpJVM)
  .enablePlugins(WebsitePlugin)
  .dependsOn(zioHttpTestkit)

Global / excludeLintKeys ++= Set(
  sbtZioHttpGrpcTests / autoAPIMappings,
  ideSkipProject,
)
