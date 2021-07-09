import sbt._
import Keys._
import scalafix.sbt.ScalafixPlugin.autoImport._
import xerial.sbt.Sonatype.autoImport._

object BuildHelper extends ScalaSettings {
  val Scala212   = "2.12.14"
  val Scala213   = "2.13.6"
  val ScalaDotty = "3.0.1"

  private val stdOptions = Seq(
    "-deprecation",
    "-encoding",
    "UTF-8",
    "-feature",
    "-unchecked",
    "-language:postfixOps",
  ) ++ {
    if (sys.env.contains("CI")) {
      Seq("-Xfatal-warnings")
    } else {
      Nil // to enable Scalafix locally
    }
  }

  private val std2xOptions = Seq(
    "-language:higherKinds",
    "-language:existentials",
    "-explaintypes",
    "-Yrangepos",
    "-Xlint:_,-missing-interpolator,-type-parameter-shadow",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Ywarn-macros:after",
  )

  private def optimizerOptions(optimize: Boolean) =
    if (optimize)
      Seq(
        "-opt:l:inline",
      )
    else Nil

  def extraOptions(scalaVersion: String, optimize: Boolean) =
    CrossVersion.partialVersion(scalaVersion) match {
      case Some((3, 0))  =>
        Seq(
          "-language:implicitConversions",
          "-Xignore-scala2-macros",
          "-noindent",
        )
      case Some((2, 12)) =>
        Seq("-Ywarn-unused:params,-implicits") ++ std2xOptions
      case Some((2, 13)) =>
        Seq("-Ywarn-unused:params,-implicits", "-Ywarn-macros:after") ++ std2xOptions ++ tpoleCatSettings ++
          optimizerOptions(optimize)
      case _             => Seq.empty
    }

  def publishSetting(publishArtifacts: Boolean) = {
    val publishSettings = Seq(
      sonatypeCredentialHost := "s01.oss.sonatype.org",
      sonatypeRepository := "https://s01.oss.sonatype.org/service/local",
      sonatypeProfileName := "io.d11",
    )
    val skipSettings    = Seq(
      publish / skip := true,
      publishArtifact := false,
    )
    if (publishArtifacts) publishSettings else publishSettings ++ skipSettings
  }
  def stdSettings(prjName: String)                          = Seq(
    name := s"$prjName",
    ThisBuild / crossScalaVersions := Seq(Scala212, Scala213, ScalaDotty),
    ThisBuild / scalaVersion := Scala213,
    scalacOptions := stdOptions ++ extraOptions(scalaVersion.value, optimize = !isSnapshot.value),
    semanticdbVersion := scalafixSemanticdb.revision, // use Scalafix compatible version
    ThisBuild / scalafixScalaBinaryVersion := CrossVersion.binaryScalaVersion(scalaVersion.value),
    ThisBuild / scalafixDependencies ++=
      List(
        "com.github.liancheng" %% "organize-imports" % "0.5.0",
        "com.github.vovapolu"  %% "scaluzzi"         % "0.1.16",
      ),
    Test / parallelExecution := true,
    incOptions ~= (_.withLogRecompileOnMacro(false)),
    autoAPIMappings := true,
  )
}
