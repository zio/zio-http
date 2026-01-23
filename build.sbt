import BuildHelper.*
import Dependencies.{scalafmt, *}
import scala.sys.process.*

val releaseDrafterVersion = "5"

// Setting default log level to INFO
val _ = sys.props += ("ZIOHttpLogLevel" -> Debug.ZIOHttpLogLevel)

ThisBuild / resolvers += Resolver.sonatypeCentralSnapshots

inThisBuild(
  List(
    organization := "dev.zio",
    homepage     := Some(url("https://zio.dev/zio-http/")),
    licenses     := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
  ),
)

lazy val exampleProjects: Seq[ProjectReference] =
  if ("git describe --tags --exact-match".! == 0) Seq.empty[ProjectReference]
  else
    Seq[ProjectReference](
      zioHttpExampleBasicAuth,
      zioHttpExampleCookieAuth,
      zioHttpExampleDigestAuth,
      zioHttpExampleOpaqueBearerTokenAuth,
      zioHttpExampleJwtBearerTokenAuth,
      zioHttpExampleJwtBearerRefreshTokenAuth,
      zioHttpExampleOauthBearerTokenAuth,
      zioHttpExampleWebauthn,
    )

lazy val aggregatedProjects: Seq[ProjectReference] =
  if (Shading.shadingEnabled) {
    Seq(
      zioHttpJVM,
      zioHttpJS,
      zioHttpTestkit,
    )
  } else {
    Seq[ProjectReference](
      zioHttpJVM,
      zioHttpJS,
      zioHttpBenchmarks,
      zioHttpCli,
      zioHttpDatastarSdk,
      zioHttpGen,
      sbtZioHttpGrpc,
      sbtZioHttpGrpcTests,
      zioHttpHtmx,
      zioHttpStomp,
      zioHttpExample,
      zioHttpTestkit,
      zioHttpTools,
      docs,
    ) ++ exampleProjects
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
    autoCompilerPlugins := true,
    libraryDependencies ++= unroll,
    addCompilerPlugin("com.lihaoyi" %% "unroll-plugin" % "0.1.12"),
  )
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
      `scala-compat-collection`,
    ) ++ netty,
  )
  .jvmSettings(MimaSettings.mimaSettings(failOnProblem = true))
  .jvmSettings(
    coverageMinimumStmtTotal   := 50,
    coverageMinimumBranchTotal := 60,
  )
  .jsSettings(
    ThisProject / fork := false,
    testFrameworks     := Seq(new TestFramework("zio.test.sbt.ZTestFramework")),
    libraryDependencies ++= Seq(
      "org.scala-lang.modules" %%% "scala-collection-compat" % ScalaCompatCollectionVersion,
      "io.github.cquiroz"      %%% "scala-java-time"         % "2.6.0",
      "io.github.cquiroz"      %%% "scala-java-time-tzdb"    % "2.6.0",
      "org.scala-js"           %%% "scalajs-dom"             % "2.8.1",
      "dev.zio"                %%% "zio-test"                % ZioVersion % "test",
      "dev.zio"                %%% "zio-test-sbt"            % ZioVersion % "test",
      "dev.zio"                %%% "zio"                     % ZioVersion,
      "dev.zio"                %%% "zio-streams"             % ZioVersion,
      "dev.zio"                %%% "zio-schema"              % ZioSchemaVersion,
      "dev.zio"                %%% "zio-schema-json"         % ZioSchemaVersion,
      "dev.zio"                %%% "zio-schema-protobuf"     % ZioSchemaVersion,
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
      "com.softwaremill.sttp.tapir"   %% "tapir-http4s-server" % "1.13.5",
      "com.softwaremill.sttp.tapir"   %% "tapir-json-circe"    % "1.13.5",
      "com.softwaremill.sttp.client3" %% "core"                % "3.11.0",
//      "dev.zio"                     %% "zio-interop-cats"    % "3.3.0",
      "org.slf4j"                      % "slf4j-api"           % "2.0.17",
      "org.slf4j"                      % "slf4j-simple"        % "2.0.17",
    ),
  )
  .dependsOn(zioHttpJVM)

lazy val zioHttpCli = (project in file("zio-http-cli"))
  .settings(stdSettings("zio-http-cli"))
  .settings(publishSetting(true))
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

lazy val zioHttpDatastarSdk = (project in file("zio-http-datastar-sdk"))
  .settings(stdSettings("zio-http-datastar-sdk"))
  .settings(publishSetting(true))
  .settings(
    libraryDependencies ++= Seq(
      `zio`,
      `zio-streams`,
      `zio-schema`,
      `zio-schema-json`,
      `zio-test`,
      `zio-test-sbt`,
    ),
  )
  .dependsOn(zioHttpJVM)

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

lazy val zioHttpStomp = (project in file("zio-http-stomp"))
  .settings(
    stdSettings("zio-http-stomp"),
    publishSetting(true),
    testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
    libraryDependencies ++= Seq(
      `zio`,
      `zio-streams`,
      `zio-schema`,
      `zio-test`,
      `zio-test-sbt`,
    ),
  )
  .dependsOn(zioHttpJVM)
  .dependsOn(zioHttpTestkit % Test)
  .settings(MimaSettings.mimaSettings(failOnProblem = true))

lazy val zioHttpExample = (project in file("zio-http-example"))
  .settings(stdSettings("zio-http-example"))
  .settings(publishSetting(false))
  .settings(runSettings(Debug.Main))
  .settings(
    libraryDependencies ++= Seq(
      `jwt-core`,
      `jwt-zio-json`,
      `zio-schema-json`,
    ),
  )
  .settings(
    run / fork := true,
    run / javaOptions ++= Seq("-Xms4G", "-Xmx4G", "-XX:+UseG1GC"),
    libraryDependencies ++= Seq(
      `zio-config`,
      `zio-config-magnolia`,
      `zio-config-typesafe`,
      "dev.zio" %% "zio-metrics-connectors"            % "2.5.5",
      "dev.zio" %% "zio-metrics-connectors-prometheus" % "2.5.5",
    ),
  )
  .dependsOn(zioHttpJVM, zioHttpCli, zioHttpGen, zioHttpDatastarSdk)

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
        .exclude("org.scala-lang.modules", "scala-collection-compat_2.13")
        .exclude("com.lihaoyi", "sourcecode_2.13"),
      `zio-json-yaml` % Test,
    ),
  )
  .settings(
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n <= 12 =>
          Seq(`scala-compat-collection`)
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
      "com.thesamet.scalapb" %% "compilerplugin"  % "0.11.20",
      "com.thesamet.scalapb" %% "scalapb-runtime" % "0.11.20" % "protobuf",
      "com.google.protobuf"   % "protobuf-java"   % "4.33.4"  % "protobuf",
    ),
  )
  .settings(
    libraryDependencies ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, n)) if n <= 12 =>
          Seq(`scala-compat-collection`)
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
      "com.google.protobuf"   % "protobuf-java"   % "4.33.4"  % "protobuf",
      "com.thesamet.scalapb" %% "scalapb-runtime" % "0.11.20" % "protobuf",
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
      "dev.zio" %% "zio-test" % ZioVersion,
      `zio-test-sbt`,
    ),
  )
  .dependsOn(zioHttpJVM)

lazy val docs = project
  .in(file("zio-http-docs"))
  .settings(stdSettings("zio-http-docs"))
  .settings(publishSetting(false))
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
    mdocOut                                    := file("website/docs"),
    mdocVariables ++= Map(
      "ZIO_VERSION"        -> ZioVersion,
      "ZIO_SCHEMA_VERSION" -> ZioSchemaVersion,
      "ZIO_CONFIG_VERSION" -> ZioConfigVersion,
      "ZIO_JSON_VERSION"   -> ZioJsonVersion,
      "JWT_CORE_VERSION"   -> JwtCoreVersion,
    ),
  )
  .dependsOn(zioHttpJVM, zioHttpGen, zioHttpDatastarSdk)
  .enablePlugins(WebsitePlugin)
  .dependsOn(zioHttpTestkit)

Global / excludeLintKeys ++= Set(
  sbtZioHttpGrpcTests / autoAPIMappings,
  ideSkipProject,
)

lazy val zioHttpExampleBasicAuth =
  RootProject(file("zio-http-example-basic-auth"))

lazy val zioHttpExampleCookieAuth =
  RootProject(file("zio-http-example-cookie-auth"))

lazy val zioHttpExampleDigestAuth =
  RootProject(file("zio-http-example-digest-auth"))

lazy val zioHttpExampleOpaqueBearerTokenAuth =
  RootProject(file("zio-http-example-opaque-bearer-token-auth"))

lazy val zioHttpExampleJwtBearerTokenAuth =
  RootProject(file("zio-http-example-jwt-bearer-token-auth"))

lazy val zioHttpExampleJwtBearerRefreshTokenAuth =
  RootProject(file("zio-http-example-jwt-bearer-refresh-token-auth"))

lazy val zioHttpExampleOauthBearerTokenAuth =
  RootProject(file("zio-http-example-oauth-bearer-token-auth"))

lazy val zioHttpExampleWebauthn =
  RootProject(file("zio-http-example-webauthn"))
