import java.util.concurrent.TimeUnit
import BuildHelper.{Scala213, stdSettings}
import scala.concurrent.duration.FiniteDuration
import sbt.enablePlugins

// ZIO Version
val zioVersion       = "1.0.5"
val zioConfigVersion = "1.0.2"

lazy val root = (project in file("."))
  .settings(stdSettings("root"))
  .settings(
    skip in publish := true,
    publishArtifact := false,
    sonatypeCredentialHost := "s01.oss.sonatype.org",
    sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
    sonatypeProfileName := "io.d11",
  )
  .aggregate(zhttp, zhttpBenchmarks, example)

// CI Configuration
ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches :=
  Seq(RefPredicate.StartsWith(Ref.Tag("v")), RefPredicate.Equals(Ref.Branch("experiment-release")))

ThisBuild / githubWorkflowPublish :=
  Seq(
    WorkflowStep.Sbt(
      List("ci-release"),
      env = Map(
        "PGP_PASSPHRASE"    -> "${{ secrets.PGP_PASSPHRASE }}",
        "PGP_SECRET"        -> "${{ secrets.PGP_SECRET }}",
        "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
        "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}",
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
  .settings(
    sonatypeCredentialHost := "s01.oss.sonatype.org",
    sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
    sonatypeProfileName := "io.d11",
    organization := "io.d11",
    organizationName := "d11",
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
    libraryDependencies ++=
      Seq(
        "dev.zio" %% "zio"         % zioVersion,
        "dev.zio" %% "zio-streams" % zioVersion,
        "io.netty" % "netty-all"   % "4.1.63.Final",
      ),
  )

// Project Benchmarks
lazy val zhttpBenchmarks = (project in file("./zio-http-benchmarks"))
  .enablePlugins(JmhPlugin)
  .dependsOn(zhttp)
  .settings(stdSettings("zhttpBenchmarks"))
  .settings(
    skip in publish := true,
    publishArtifact := false,
    sonatypeProfileName := "io.d11",
    sonatypeCredentialHost := "s01.oss.sonatype.org",
    sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
    libraryDependencies ++=
      Seq(
        "dev.zio" %% "zio" % zioVersion,
      ),
  )

lazy val example = (project in file("./example"))
  .settings(stdSettings("example"))
  .settings(
    fork := true,
    skip in publish := true,
    publishArtifact := false,
    sonatypeProfileName := "io.d11",
    sonatypeCredentialHost := "s01.oss.sonatype.org",
    sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
    mainClass in (Compile, run) := Option("HelloWorldAdvanced"),
  )
  .dependsOn(zhttp)

addCommandAlias("fmt", "scalafmt; test:scalafmt; sFix;")
addCommandAlias("fmtCheck", "scalafmtCheck; test:scalafmtCheck; sFixCheck")
addCommandAlias("sFix", "scalafix OrganizeImports; test:scalafix OrganizeImports")
addCommandAlias("sFixCheck", "scalafix --check OrganizeImports; test:scalafix --check OrganizeImports")

Global / onChangedBuildSource := ReloadOnSourceChanges
Global / watchAntiEntropy := FiniteDuration(2000, TimeUnit.MILLISECONDS)
