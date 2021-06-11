import sbt._
import Keys._

import explicitdeps.ExplicitDepsPlugin.autoImport._
import sbtcrossproject.CrossPlugin.autoImport.CrossType
import sbtbuildinfo._
import BuildInfoKeys._

object BuildHelper {

  // Keep this consistent with the version in .circleci/config.yml
  val Scala211 = "2.11.12"
  val Scala212 = "2.12.13"
  val Scala213 = "2.13.5"

  val zioVersion        = "1.0.5"
  val zioLoggingVersion = "0.5.8"
  val zioSchemaVersion  = "0.0.1"
  val zioJsonVersion    = "0.1"
  val zioNioVersion     = "1.0.0-RC11"
  val silencerVersion   = "1.7.3"
  val magnoliaVersion   = "0.17.0"

  private val testDeps = Seq(
    "dev.zio" %% "zio-test"     % zioVersion % "test",
    "dev.zio" %% "zio-test-sbt" % zioVersion % "test"
  )

  private def compileOnlyDeps(scalaVersion: String) = {
    val stdCompileOnlyDeps = Seq(
      ("com.github.ghik" % "silencer-lib" % silencerVersion % Provided).cross(CrossVersion.full),
      compilerPlugin(("com.github.ghik" % "silencer-plugin" % silencerVersion).cross(CrossVersion.full)),
      compilerPlugin(("org.typelevel"   %% "kind-projector" % "0.11.3").cross(CrossVersion.full))
    )
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, x)) if x <= 12 =>
        stdCompileOnlyDeps ++ Seq(
          compilerPlugin(("org.scalamacros" % "paradise" % "2.1.1").cross(CrossVersion.full))
        )
      case _ => stdCompileOnlyDeps
    }
  }

  private def compilerOptions(scalaVersion: String, optimize: Boolean) = {
    val stdOptions = Seq(
      "-deprecation",
      "-encoding",
      "UTF-8",
      "-feature",
      "-unchecked",
      "-Xfatal-warnings",
      "-language:higherKinds",
      "-language:existentials",
      "-explaintypes",
      "-Yrangepos",
      "-Xsource:2.13",
      "-Xlint:_,-type-parameter-shadow",
      "-Ywarn-numeric-widen",
      "-Ywarn-value-discard"
    )

    val optimizerOptions =
      if (optimize)
        Seq(
          "-opt:l:inline"
        )
      else Seq.empty

    val extraOptions = CrossVersion.partialVersion(scalaVersion) match {
      case Some((2, 13)) =>
        Seq(
          "-opt-warnings",
          "-Ywarn-extra-implicit",
          "-Ywarn-unused",
          "-Ymacro-annotations"
        ) ++ optimizerOptions
      case Some((2, 12)) =>
        Seq(
          "-Ypartial-unification",
          "-opt-warnings",
          "-Ywarn-extra-implicit",
          "-Ywarn-unused",
          "-Yno-adapted-args",
          "-Ywarn-inaccessible",
          "-Ywarn-infer-any",
          "-Ywarn-nullary-override",
          "-Ywarn-nullary-unit"
        ) ++ optimizerOptions
      case _ => Seq.empty
    }

    stdOptions ++ extraOptions
  }

  val buildInfoSettings = Seq(
    buildInfoKeys := Seq[BuildInfoKey](name, version, scalaVersion, sbtVersion, isSnapshot),
    buildInfoPackage := "zio",
    buildInfoObject := "BuildInfoZioMacros"
  )

  def stdSettings(prjName: String) =
    Seq(
      name := s"$prjName",
      crossScalaVersions := Seq(Scala213, Scala212, Scala211),
      scalaVersion in ThisBuild := crossScalaVersions.value.head,
      scalacOptions := compilerOptions(scalaVersion.value, optimize = !isSnapshot.value),
      libraryDependencies ++= compileOnlyDeps(scalaVersion.value) ++ testDeps,
      parallelExecution in Test := true,
      incOptions ~= (_.withLogRecompileOnMacro(true)),
      autoAPIMappings := true,
      testFrameworks := Seq(new TestFramework("zio.test.sbt.ZTestFramework"))
    )
}
